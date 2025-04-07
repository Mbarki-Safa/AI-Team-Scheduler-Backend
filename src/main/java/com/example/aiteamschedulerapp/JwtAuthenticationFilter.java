package com.example.aiteamschedulerapp;

import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtDecoder jwtDecoder;

    public JwtAuthenticationFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                Jwt jwt = jwtDecoder.decode(token);

                // Extract user identifier (email or preferred_username) to use as principal
                String userIdentifier;
                if (jwt.hasClaim("email")) {
                    userIdentifier = jwt.getClaimAsString("email");
                } else if (jwt.hasClaim("preferred_username")) {
                    userIdentifier = jwt.getClaimAsString("preferred_username");
                } else {
                    userIdentifier = jwt.getSubject();
                }

                // Extract roles from realm_access.roles
                List<String> roles = Collections.emptyList();
                if (jwt.hasClaim("realm_access")) {
                    @SuppressWarnings("unchecked")
                    List<String> realmRoles = ((List<String>) ((java.util.Map<String, Object>) jwt.getClaim("realm_access")).get("roles"));
                    roles = realmRoles;
                }

                // Create authentication token with roles and user identifier as principal name
                JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                        jwt,
                        roles.stream()
                                .map(role -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role))
                                .collect(Collectors.toList()),
                        userIdentifier  // Use the extracted identifier as principal name
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // Invalid token, clear security context
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

}
