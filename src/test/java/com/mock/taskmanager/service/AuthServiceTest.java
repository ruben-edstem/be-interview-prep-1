package com.mock.taskmanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.mock.taskmanager.dto.request.LoginRequest;
import com.mock.taskmanager.dto.request.RegisterRequest;
import com.mock.taskmanager.dto.response.TokenResponse;
import com.mock.taskmanager.dto.response.UserResponse;
import com.mock.taskmanager.entity.Role;
import com.mock.taskmanager.entity.User;
import com.mock.taskmanager.exception.InvalidCredentialsException;
import com.mock.taskmanager.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userService, userRepository, passwordEncoder, tokenService);
    }

    @Test
    void registerAlwaysCreatesAUserWithTheUserRole() {
        UserResponse created = new UserResponse(USER_ID, "ada@example.com", Role.USER, Instant.now());
        when(userService.create("ada@example.com", "s3cret-pass", Role.USER)).thenReturn(created);

        UserResponse registered = authService.register(new RegisterRequest("ada@example.com", "s3cret-pass"));

        assertThat(registered).isSameAs(created);
    }

    @Test
    void loginIssuesATokenWhenThePasswordMatches() {
        User user = user();
        TokenResponse token = TokenResponse.bearer("signed-token", 900);
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("s3cret-pass", "hashed-pass")).thenReturn(true);
        when(tokenService.issue(user)).thenReturn(token);

        TokenResponse issued = authService.login(new LoginRequest(" Ada@Example.com ", "s3cret-pass"));

        assertThat(issued).isSameAs(token);
    }

    @Test
    void loginWithTheWrongPasswordThrowsInvalidCredentials() {
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("wrong-pass", "hashed-pass")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("ada@example.com", "wrong-pass")));

        verify(tokenService, never()).issue(any(User.class));
    }

    @Test
    void loginWithAnUnknownEmailThrowsTheSameInvalidCredentials() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("nobody@example.com", "s3cret-pass")));

        verify(tokenService, never()).issue(any(User.class));
    }

    private User user() {
        return User.builder().id(USER_ID).email("ada@example.com").passwordHash("hashed-pass").role(Role.USER).build();
    }
}
