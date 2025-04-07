package com.example.aiteamschedulerapp.repositories;

import com.example.aiteamschedulerapp.entities.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    @Query("SELECT DISTINCT t FROM Team t LEFT JOIN FETCH t.members WHERE t.manager.id = :managerId")
    List<Team> findByManager_Id(@Param("managerId") Long managerId);

}
