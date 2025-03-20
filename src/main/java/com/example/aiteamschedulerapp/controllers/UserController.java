package com.example.aiteamschedulerapp.controllers;

import com.example.aiteamschedulerapp.entities.User;
import com.example.aiteamschedulerapp.repositories.UserRepository;
import com.example.aiteamschedulerapp.services.KeycloakService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserRepository userRepository;
    private final KeycloakService keycloakService;

    public UserController(UserRepository userRepository, KeycloakService keycloakService) {
        this.userRepository = userRepository;
        this.keycloakService = keycloakService;
    }

    @GetMapping
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<List<User>> getAllUsers() {
        logger.info("Retrieving all users");
        List<User> users = userRepository.findAll();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        logger.info("Retrieving user with ID: {}", id);
        Optional<User> user = userRepository.findById(id);
        return user.map(ResponseEntity::ok)
                .orElseGet(() -> {
                    logger.warn("User with ID: {} not found", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User userDetails) {
        logger.info("Updating user with ID: {}", id);

        Optional<User> optionalUser = userRepository.findById(id);
        if (optionalUser.isEmpty()) {
            logger.warn("User with ID: {} not found", id);
            return ResponseEntity.notFound().build();
        }

        User user = optionalUser.get();

        // Check if email is already in use by another user
        if (!user.getEmail().equals(userDetails.getEmail()) &&
                userRepository.existsByEmail(userDetails.getEmail())) {
            logger.warn("Email {} is already in use by another user", userDetails.getEmail());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        try {
            // Update user properties
            user.setFirstName(userDetails.getFirstName());
            user.setLastName(userDetails.getLastName());
            user.setEmail(userDetails.getEmail());
            user.setRole(userDetails.getRole());

            // Update user in Keycloak
            keycloakService.updateUserDetails(user);

            // Save to database
            User updatedUser = userRepository.save(user);
            logger.info("User with ID: {} updated successfully", id);
            return ResponseEntity.ok(updatedUser);
        } catch (Exception e) {
            logger.error("Error updating user with ID: {}", id, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update user");
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        logger.info("Deleting user with ID: {}", id);

        Optional<User> optionalUser = userRepository.findById(id);
        if (optionalUser.isEmpty()) {
            logger.warn("User with ID: {} not found", id);
            return ResponseEntity.notFound().build();
        }

        User user = optionalUser.get();
        String keycloakId = user.getKeycloakId();

        try {
            // Delete from Keycloak first
            keycloakService.deleteUser(keycloakId);

            // Delete from database
            userRepository.deleteById(id);
            logger.info("User with ID: {} deleted successfully", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting user with ID: {}", id, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete user");
        }
    }
}
