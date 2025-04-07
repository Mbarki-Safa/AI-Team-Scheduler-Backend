package com.example.aiteamschedulerapp.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.aiteamschedulerapp.entities.*;
import com.example.aiteamschedulerapp.repositories.UserRepository;
import jakarta.ws.rs.NotFoundException;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;


@Service
public class KeycloakService {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakService.class);


    private final UserRepository userRepository;

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.resource}")
    private String clientId;

    @Value("${keycloak.credentials.secret}")
    private String clientSecret;



    public KeycloakService(UserRepository userRepository) {
        this.userRepository = userRepository;

    }

    @Transactional
    public AuthResponse registerUser(RegisterRequest request) {
        try {
            // Check if email already exists
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
            }

            // Create Keycloak admin instance
            Keycloak keycloakAdmin = getKeycloakAdminInstance();
            RealmResource realmResource = keycloakAdmin.realm(realm);
            UsersResource usersResource = realmResource.users();

            // Create user representation
            UserRepresentation userRepresentation = new UserRepresentation();
            userRepresentation.setEnabled(true);
            userRepresentation.setUsername(request.getEmail());
            userRepresentation.setEmail(request.getEmail());
            userRepresentation.setFirstName(request.getFirstName());
            userRepresentation.setLastName(request.getLastName());
            userRepresentation.setEmailVerified(true);

            // Create user in Keycloak
            jakarta.ws.rs.core.Response response = usersResource.create(userRepresentation);
            String keycloakUserId = getCreatedUserId(response);

            // Set password
            setUserPassword(usersResource, keycloakUserId, request.getPassword());

            // Assign role
            assignRealmRole(realmResource, keycloakUserId, request.getRole().name());

            // Save user in our database
            User user = new User();
            user.setFirstName(request.getFirstName());
            user.setLastName(request.getLastName());
            user.setEmail(request.getEmail());
            user.setRole(request.getRole());
            user.setKeycloakId(keycloakUserId);
            user = userRepository.save(user);

            // Get tokens
            AccessTokenResponse tokenResponse = getToken(request.getEmail(), request.getPassword());

            // Create response
            return AuthResponse.builder()
                    .accessToken(tokenResponse.getToken())
                    .refreshToken(tokenResponse.getRefreshToken())
                    .user(mapToUserDTO(user))
                    .build();

        } catch (Exception e) {
            // Log the error
            logger.error("Error occurred during user registration: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "An error occurred while registering the user");
        }
    }

    public AuthResponse login(LoginRequest request) {
        logger.info("User login attempt: {}", request.getEmail());

        try {
            // Get tokens from Keycloak
            AccessTokenResponse tokenResponse = getToken(request.getEmail(), request.getPassword());

            // Find user in our database
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

            logger.info("User found in local database: {}", user.getEmail());

            // Create response
            return AuthResponse.builder()
                    .accessToken(tokenResponse.getToken())
                    .refreshToken(tokenResponse.getRefreshToken())
                    .user(mapToUserDTO(user))
                    .build();

        } catch (Exception e) {
            logger.error("Login failed for user: {}. Error: {}", request.getEmail(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
    }

    private Keycloak getKeycloakAdminInstance() {
        logger.info("Creating Keycloak admin instance");

        return KeycloakBuilder.builder()
                .serverUrl(authServerUrl)
                .realm("master")
                .clientId("admin-cli")
                .username("admin") // Replace with your admin username
                .password("admin") // Replace with your admin password
                .build();
    }

    private String getCreatedUserId(jakarta.ws.rs.core.Response response) {
        if (response.getStatus() != 201) {
            logger.error("Failed to create user in Keycloak, status: {}", response.getStatus());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create user in Keycloak");
        }

        String location = response.getLocation().toString();
        String userId = location.substring(location.lastIndexOf('/') + 1);
        logger.info("Created user in Keycloak with ID: {}", userId);
        return userId;
    }

    private void setUserPassword(UsersResource usersResource, String userId, String password) {
        logger.info("Resetting password for Keycloak user: {}", userId);

        UserResource userResource = usersResource.get(userId);

        CredentialRepresentation credentialRepresentation = new CredentialRepresentation();
        credentialRepresentation.setType(CredentialRepresentation.PASSWORD);
        credentialRepresentation.setValue(password);
        credentialRepresentation.setTemporary(false);

        userResource.resetPassword(credentialRepresentation);
    }

    private void assignRealmRole(RealmResource realmResource, String userId, String roleName) {
        try {
            logger.info("Assigning role {} to user: {}", roleName, userId);

            // Get role
            RoleRepresentation role = realmResource.roles().get(roleName).toRepresentation();

            // Assign role to user
            realmResource.users().get(userId).roles().realmLevel()
                    .add(Collections.singletonList(role));
        } catch (NotFoundException e) {
            logger.error("Role not found: {}", roleName);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role not found: " + roleName);
        }
    }

    private AccessTokenResponse getToken(String username, String password) {
        logger.info("Getting token for user: {}", username);

        Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl(authServerUrl)
                .realm(realm)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .username(username)
                .password(password)
                .grantType("password")
                .build();

        return keycloak.tokenManager().getAccessToken();
    }

    private UserDto mapToUserDTO(User user) {
        return new UserDto(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole()
        );
    }


    //User management fonctions
    /**
     * Updates a user's details in Keycloak
     * @param user The user entity with updated information
     */
    public void updateUserDetails(User user) {
        logger.info("Updating user details in Keycloak for user ID: {}", user.getKeycloakId());

        try {
            // Create Keycloak admin instance
            Keycloak keycloakAdmin = getKeycloakAdminInstance();
            RealmResource realmResource = keycloakAdmin.realm(realm);
            UserResource userResource = realmResource.users().get(user.getKeycloakId());

            // Get current representation
            UserRepresentation userRepresentation = userResource.toRepresentation();

            // Update fields
            userRepresentation.setFirstName(user.getFirstName());
            userRepresentation.setLastName(user.getLastName());
            userRepresentation.setEmail(user.getEmail());
            userRepresentation.setUsername(user.getEmail()); // If username is same as email

            // Update user in Keycloak
            userResource.update(userRepresentation);

            // Update user role if it has changed
            updateUserRole(realmResource, user);

            logger.info("Successfully updated user details in Keycloak for user ID: {}", user.getKeycloakId());
        } catch (Exception e) {
            logger.error("Error updating user in Keycloak: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update user in Keycloak");
        }
    }

    /**
     * Updates a user's role in Keycloak if it has changed
     * @param realmResource The realm resource
     * @param user The user entity with updated role
     */
    private void updateUserRole(RealmResource realmResource, User user) {
        UserResource userResource = realmResource.users().get(user.getKeycloakId());

        // Get current roles
        List<RoleRepresentation> currentRoles = userResource.roles().realmLevel().listAll();

        // Remove current roles
        userResource.roles().realmLevel().remove(currentRoles);

        // Add new role
        RoleRepresentation newRole = realmResource.roles().get(user.getRole().name()).toRepresentation();
        userResource.roles().realmLevel().add(Collections.singletonList(newRole));

        logger.info("Updated role to {} for user ID: {}", user.getRole().name(), user.getKeycloakId());
    }

    /**
     * Deletes a user from Keycloak
     * @param keycloakId The Keycloak ID of the user to delete
     */
    public void deleteUser(String keycloakId) {
        logger.info("Deleting user from Keycloak with ID: {}", keycloakId);

        try {
            // Create Keycloak admin instance
            Keycloak keycloakAdmin = getKeycloakAdminInstance();
            RealmResource realmResource = keycloakAdmin.realm(realm);

            // Delete user
            realmResource.users().get(keycloakId).remove();

            logger.info("Successfully deleted user from Keycloak with ID: {}", keycloakId);
        } catch (Exception e) {
            logger.error("Error deleting user from Keycloak: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete user from Keycloak");
        }
    }

    //refresh token
    public AuthResponse refreshToken(String refreshToken) {
        logger.info("Refreshing token using refresh token");

        try {
            // Prepare the form data for the token endpoint
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "refresh_token");
            formData.add("refresh_token", refreshToken);
            formData.add("client_id", clientId);
            formData.add("client_secret", clientSecret);

            // Create headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            // Create the HTTP entity
            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(formData, headers);

            // Make the request to the Keycloak token endpoint
            String tokenEndpoint = authServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";
            ResponseEntity<Map> response = new RestTemplate().postForEntity(
                    tokenEndpoint,
                    entity,
                    Map.class
            );

            // Extract the new tokens
            Map<String, Object> responseBody = response.getBody();
            String newAccessToken = (String) responseBody.get("access_token");
            String newRefreshToken = (String) responseBody.get("refresh_token");

            // Extract user information from the token
            String[] jwtParts = newAccessToken.split("\\.");
            String payload = new String(Base64.getUrlDecoder().decode(jwtParts[1]));
            JsonNode payloadJson = new ObjectMapper().readTree(payload);
            String email = payloadJson.get("email").asText();
            logger.info("Attempting to find user with email: '{}'", email);

            logger.info("JWT payload: {}", payload);

            // Find the user in the database
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

            // Create and return the response
            return AuthResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .user(mapToUserDTO(user))
                    .build();
        } catch (Exception e) {
            logger.error("Token refresh failed: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
    }
}