package com.example.aiteamschedulerapp.services;

import org.springframework.stereotype.Service;
import com.example.aiteamschedulerapp.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;

    @Value("${app.user-list-path:classpath:user-list.txt}")
    private String userListPath;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<Map<String, String>> getAvailableUsers() {
        List<Map<String, String>> availableUsers = new ArrayList<>();
        try {
            BufferedReader reader;
            if (userListPath.startsWith("classpath:")) {
                String filePath = userListPath.substring("classpath:".length());
                ClassPathResource resource = new ClassPathResource(filePath);
                reader = new BufferedReader(new InputStreamReader(resource.getInputStream()));
            } else {
                // For file system path
                reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(userListPath)));
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(",")) {
                    String[] parts = line.split(",", 2);
                    String name = parts[0].trim();
                    String email = parts[1].trim();

                    // Skip if user already exists in the system
                    if (!userRepository.existsByEmail(email)) {
                        availableUsers.add(Map.of(
                                "name", name,
                                "email", email
                        ));
                    }
                }
            }
            reader.close();
        } catch (IOException e) {
            logger.error("Error reading user list file", e);
        }
        return availableUsers;
    }

    public List<String> getAvailableUserEmails() {
        return getAvailableUsers().stream()
                .map(user -> user.get("email"))
                .collect(Collectors.toList());
    }

}
