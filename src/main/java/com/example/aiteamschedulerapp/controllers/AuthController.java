package com.example.aiteamschedulerapp.controllers;

import com.example.aiteamschedulerapp.entities.*;
import com.example.aiteamschedulerapp.repositories.TeamInvitationRepository;
import com.example.aiteamschedulerapp.services.AuthService;
import com.example.aiteamschedulerapp.services.KeycloakService;


import com.example.aiteamschedulerapp.services.TeamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    private final KeycloakService keycloakService;
    private final TeamInvitationRepository invitationRepository;
    private final TeamService teamService;

    public AuthController(KeycloakService keycloakService , TeamInvitationRepository invitationRepository, TeamService teamService) {
        this.keycloakService = keycloakService;
        this.invitationRepository = invitationRepository;
        this.teamService = teamService;

    }
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {

        TeamInvitation invitation = null;

        if (request.getInvitationToken() != null && !request.getInvitationToken().isEmpty()) {
            invitation = teamService.validateInvitationToken(request.getInvitationToken());

            if (!invitation.getEmail().equals(request.getEmail())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Email does not match the invitation");
            }

            if (invitation.isAccepted()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "This invitation has already been used");
            }
        }

        AuthResponse response = keycloakService.registerUser(request);

        if (invitation != null) {
            invitation.setAccepted(true);
            invitationRepository.save(invitation);
        }

        return ResponseEntity.ok(response);

    }


    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(keycloakService.login(request));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        AuthResponse response = keycloakService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }
}
