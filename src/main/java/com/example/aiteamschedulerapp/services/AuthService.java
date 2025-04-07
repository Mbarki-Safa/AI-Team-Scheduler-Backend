package com.example.aiteamschedulerapp.services;

import com.example.aiteamschedulerapp.entities.User;
import com.example.aiteamschedulerapp.repositories.UserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
public class AuthService {
    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Check if the authentication is a JwtAuthenticationToken
        if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtToken = (JwtAuthenticationToken) authentication;
            Jwt jwt = jwtToken.getToken();

            // Extract either email or subject from the token
            String email = null;

            // Try to get email from token claims
            if (jwt.hasClaim("email")) {
                email = jwt.getClaimAsString("email");
            } else if (jwt.hasClaim("preferred_username")) {
                email = jwt.getClaimAsString("preferred_username");
            } else {
                // Use subject as fallback
                email = jwt.getSubject();
            }

            // Ensure email is final for the lambda expression
            final String finalEmail = email;

            return userRepository.findByEmail(finalEmail)
                    .orElseThrow(() -> new RuntimeException("User not found with email: " + finalEmail));
        }

        // Fallback to traditional authentication
        String email = authentication.getName();

        // Ensure email is final for the lambda expression
        final String finalEmail = email;

        return userRepository.findByEmail(finalEmail)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + finalEmail));
}
}
