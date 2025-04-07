package com.example.aiteamschedulerapp.services;

import org.springframework.stereotype.Service;
import com.example.aiteamschedulerapp.entities.Team;
import com.example.aiteamschedulerapp.entities.TeamInvitation;
import com.example.aiteamschedulerapp.entities.User;
import com.example.aiteamschedulerapp.entities.UserRole;
import com.example.aiteamschedulerapp.repositories.TeamInvitationRepository;
import com.example.aiteamschedulerapp.repositories.TeamRepository;
import com.example.aiteamschedulerapp.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;

import org.springframework.web.server.ResponseStatusException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TeamService {
    private static final Logger logger = LoggerFactory.getLogger(TeamService.class);

    private final TeamRepository teamRepository;
    private final TeamInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public TeamService(
            TeamRepository teamRepository,
            TeamInvitationRepository invitationRepository,
            UserRepository userRepository,
            JavaMailSender mailSender
    ) {
        this.teamRepository = teamRepository;
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
    }

    @Transactional
    public Team createTeam(User manager, String teamName, List<String> invitedEmails) {
        // Validate manager role
        if (manager.getRole() != UserRole.Manager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Managers can create teams");
        }
        // Create team
        Team team = new Team();
        team.setName(teamName);
        team.setManager(manager);
        Team savedTeam = teamRepository.save(team);

        invitedEmails.forEach(email -> createTeamInvitation(savedTeam, email));
        return savedTeam;
    }

    private void createTeamInvitation(Team team, String email) {
        // Check if user already exists or is already invited
        if (userRepository.existsByEmail(email) ||
                invitationRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "User with email " + email + " is already registered or invited");
        }

        // Create invitation
        TeamInvitation invitation = new TeamInvitation();
        invitation.setTeam(team);
        invitation.setEmail(email);
        invitation.setInvitationToken(TeamInvitation.generateToken());
        invitation.setCreatedAt(LocalDateTime.now());
        invitation.setExpiresAt(LocalDateTime.now().plusDays(7));
        invitation.setAccepted(false);

        invitation = invitationRepository.save(invitation);

        // Send invitation email
        sendInvitationEmail(invitation);
    }

    private void sendInvitationEmail(TeamInvitation invitation) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(invitation.getEmail());
        message.setSubject("Team Invitation for " + invitation.getTeam().getName());

        String registrationLink = frontendUrl + "/register?token=" + invitation.getInvitationToken();

        message.setText("You have been invited to join the team: " + invitation.getTeam().getName() +
                "\n\nPlease register using this link: " + registrationLink +
                "\n\nThis link will expire in 7 days.");

        mailSender.send(message);

        logger.info("Invitation email sent to {}", invitation.getEmail());
    }

    public List<Team> getManagerTeams(User manager) {
        List<Team> teams = teamRepository.findByManager_Id(manager.getId());
        // Log the teams to verify data is returned correctly
        logger.info("Found {} teams for manager ID: {}", teams.size(), manager.getId());
        for (Team team : teams) {
            logger.info("Team: {}, Members count: {}", team.getName(),
                    team.getMembers() != null ? team.getMembers().size() : 0);
        }
        return teams;
    }


    @Transactional
    public Team addMembersToTeam(Long teamId, List<Long> memberIds) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));

        List<User> members = userRepository.findAllById(memberIds);
        team.getMembers().addAll(members);

        return teamRepository.save(team);
    }

    public TeamInvitation validateInvitationToken(String token) {
        return invitationRepository.findByInvitationToken(token)
                .filter(invitation -> !invitation.isAccepted() &&
                        invitation.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired invitation"));
    }

    public TeamInvitation getInvitationByToken(String token) {
        return invitationRepository.findByInvitationToken(token)
                .filter(invitation -> invitation.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired invitation"));
    }

    @Transactional
    public void removeTeamMember(Long teamId, Long userId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Check if the user is actually a member of the team
        if (!team.getMembers().contains(user)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not a member of this team");
        }

        // Remove the user from the team
        team.getMembers().remove(user);
        teamRepository.save(team);

        logger.info("User {} removed from team {}", userId, teamId);
    }


    @Transactional
    public void acceptInvitation(String token, User user) {
        TeamInvitation invitation = invitationRepository.findByInvitationToken(token)
                .filter(inv -> inv.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired invitation"));

        // Mark the invitation as accepted
        invitation.setAccepted(true);
        invitationRepository.save(invitation);

        // Add the user to the team's members
        Team team = invitation.getTeam();
        if (team.getMembers() == null) {
            team.setMembers(new ArrayList<>());
        }

        // Check if the user is already a team member to avoid duplicates
        if (!team.getMembers().contains(user)) {
            team.getMembers().add(user);
            teamRepository.save(team);
        }

        logger.info("User {} accepted invitation to team {}", user.getId(), team.getId());
    }

    @Transactional
    public void processAcceptedInvitations() {
        List<TeamInvitation> acceptedInvitations = invitationRepository.findByAcceptedTrue();

        for (TeamInvitation invitation : acceptedInvitations) {
            User user = userRepository.findByEmail(invitation.getEmail()).orElse(null);
            if (user != null) {
                Team team = invitation.getTeam();
                if (team.getMembers() == null) {
                    team.setMembers(new ArrayList<>());
                }

                if (!team.getMembers().contains(user)) {
                    team.getMembers().add(user);
                    teamRepository.save(team);
                    logger.info("User {} from accepted invitation added to team {}", user.getId(), team.getId());
                }
            }
        }
    }


}
