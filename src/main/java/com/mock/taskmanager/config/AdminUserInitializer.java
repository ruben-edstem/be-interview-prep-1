package com.mock.taskmanager.config;

import com.mock.taskmanager.entity.Role;
import com.mock.taskmanager.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AdminProperties.class)
public class AdminUserInitializer implements ApplicationRunner {

    private final AdminProperties adminProperties;
    private final UserService userService;

    @Override
    public void run(ApplicationArguments args) {
        if (!adminProperties.isConfigured()) {
            log.info("No admin account configured; skipping admin seeding");
            return;
        }
        boolean created = userService.createIfAbsent(adminProperties.email(), adminProperties.password(), Role.ADMIN);
        log.info(created ? "Seeded the configured admin account" : "Configured admin account already exists");
    }
}
