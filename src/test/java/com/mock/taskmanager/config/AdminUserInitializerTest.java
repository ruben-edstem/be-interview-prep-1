package com.mock.taskmanager.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mock.taskmanager.entity.Role;
import com.mock.taskmanager.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserInitializerTest {

    @Mock
    private UserService userService;

    @Test
    void seedsTheConfiguredAdmin() {
        AdminUserInitializer initializer =
                new AdminUserInitializer(new AdminProperties("admin@example.com", "s3cret-pass"), userService);

        initializer.run(null);

        verify(userService).createIfAbsent("admin@example.com", "s3cret-pass", Role.ADMIN);
    }

    @Test
    void doesNothingWhenNoAdminIsConfigured() {
        AdminUserInitializer initializer = new AdminUserInitializer(new AdminProperties("", ""), userService);

        initializer.run(null);

        verify(userService, never()).createIfAbsent(anyString(), anyString(), any(Role.class));
    }

    @Test
    void doesNothingWhenOnlyTheEmailIsConfigured() {
        AdminUserInitializer initializer =
                new AdminUserInitializer(new AdminProperties("admin@example.com", null), userService);

        initializer.run(null);

        verify(userService, never()).createIfAbsent(anyString(), anyString(), any(Role.class));
    }
}
