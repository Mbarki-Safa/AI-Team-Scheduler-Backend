package com.example.aiteamschedulerapp.repositories;

import com.example.aiteamschedulerapp.entities.TeamInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamInvitationRepository extends JpaRepository<TeamInvitation, Long> {
    Optional<TeamInvitation> findByInvitationToken(String token);
    Optional<TeamInvitation> findByEmail(String email);
    List<TeamInvitation> findByTeam_Id(Long teamId);

    List<TeamInvitation> findByAcceptedTrue();
}
