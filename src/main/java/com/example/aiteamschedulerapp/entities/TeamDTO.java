package com.example.aiteamschedulerapp.entities;

import java.util.List;
import java.util.stream.Collectors;

public class TeamDTO {
    private Long id;
    private String name;
    private UserDto manager;
    private List<UserDto> members;

    public TeamDTO(Team team) {
        this.id = team.getId();
        this.name = team.getName();

        // Convert manager User to UserDto
        User managerUser = team.getManager();
        if (managerUser != null) {
            this.manager = new UserDto(
                    managerUser.getId(),
                    managerUser.getFirstName(),
                    managerUser.getLastName(),
                    managerUser.getEmail(),
                    managerUser.getRole()
            );
        }

        // Convert member Users to UserDto objects
        this.members = team.getMembers() != null ?
                team.getMembers().stream()
                        .map(user -> new UserDto(
                                user.getId(),
                                user.getFirstName(),
                                user.getLastName(),
                                user.getEmail(),
                                user.getRole()
                        ))
                        .collect(Collectors.toList()) :
                List.of();
    }

    // Getters
    public Long getId() { return id; }
    public String getName() { return name; }
    public UserDto getManager() { return manager; }
    public List<UserDto> getMembers() { return members; }
}
