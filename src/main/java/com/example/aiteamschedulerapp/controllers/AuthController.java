package com.example.aiteamschedulerapp.controllers;

import com.example.aiteamschedulerapp.entities.AuthResponse;
import com.example.aiteamschedulerapp.entities.LoginRequest;
import com.example.aiteamschedulerapp.entities.RefreshTokenRequest;
import com.example.aiteamschedulerapp.entities.RegisterRequest;
import com.example.aiteamschedulerapp.services.KeycloakService;


import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    private final KeycloakService keycloakService;

    public AuthController(KeycloakService keycloakService) {
        this.keycloakService = keycloakService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(keycloakService.registerUser(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(keycloakService.login(request));
    }



}
