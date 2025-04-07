package com.example.aiteamschedulerapp.controllers;


import com.example.aiteamschedulerapp.entities.Team;
import com.example.aiteamschedulerapp.entities.TeamDTO;
import com.example.aiteamschedulerapp.entities.User;
import com.example.aiteamschedulerapp.services.AuthService;
import com.example.aiteamschedulerapp.services.TeamService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teams")
public class TeamController {
    private final TeamService teamService;
    private final AuthService authService;

    public TeamController(TeamService teamService, AuthService authService) {
        this.teamService = teamService;
        this.authService = authService;
    }

    @PostMapping
    @PreAuthorize("hasRole('Manager')")
    public ResponseEntity<Team> createTeam(
            @RequestBody TeamCreationRequest request
    ) {
        User currentUser = authService.getCurrentUser();
        Team team = teamService.createTeam(currentUser, request.getTeamName(), request.getInvitedEmails());
        return ResponseEntity.ok(team);
    }

    @GetMapping
    @PreAuthorize("hasRole('Manager')")
    public ResponseEntity<List<TeamDTO>> getManagerTeams() {
        User currentUser = authService.getCurrentUser();
        List<Team> teams = teamService.getManagerTeams(currentUser);
        List<TeamDTO> teamDTOs = teams.stream()
                .map(TeamDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(teamDTOs);
    }

    @PostMapping("/{teamId}/members")
    @PreAuthorize("hasRole('Manager')")
    public ResponseEntity<Team> addMembersToTeam(
            @PathVariable Long teamId,
            @RequestBody List<Long> memberIds
    ) {
        Team team = teamService.addMembersToTeam(teamId, memberIds);
        return ResponseEntity.ok(team);
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    @PreAuthorize("hasRole('Manager')")
    public ResponseEntity<Void> removeTeamMember(
            @PathVariable Long teamId,
            @PathVariable Long userId
    ) {
        teamService.removeTeamMember(teamId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invitations/accept")
    public ResponseEntity<Void> acceptInvitation(@RequestParam String token) {
        User currentUser = authService.getCurrentUser();
        teamService.acceptInvitation(token, currentUser);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/invitations/process-accepted")
    @PreAuthorize("hasRole('Manager')")
    public ResponseEntity<Void> processAcceptedInvitations() {
        teamService.processAcceptedInvitations();
        return ResponseEntity.ok().build();
    }


    // DTOs
    public static class TeamCreationRequest {
        private String teamName;
        private List<String> invitedEmails;

        // Getters and setters
        public String getTeamName() { return teamName; }
        public void setTeamName(String teamName) { this.teamName = teamName; }
        public List<String> getInvitedEmails() { return invitedEmails; }
        public void setInvitedEmails(List<String> invitedEmails) { this.invitedEmails = invitedEmails; }
    }
}
