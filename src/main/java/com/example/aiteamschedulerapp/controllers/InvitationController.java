package com.example.aiteamschedulerapp.controllers;

import com.example.aiteamschedulerapp.entities.TeamInvitation;
import com.example.aiteamschedulerapp.services.TeamService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invitations")
@CrossOrigin(origins = "*")
public class InvitationController {
    private final TeamService teamService;

    public InvitationController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping("/validate")
    public ResponseEntity<TeamInvitation> validateInvitation(@RequestParam String token) {
        TeamInvitation invitation = teamService.validateInvitationToken(token);
        return ResponseEntity.ok(invitation);
    }

    @GetMapping("/details")
    public ResponseEntity<TeamInvitation> getInvitationDetails(@RequestParam String token) {
        TeamInvitation invitation = teamService.getInvitationByToken(token);
        return ResponseEntity.ok(invitation);
    }
}
