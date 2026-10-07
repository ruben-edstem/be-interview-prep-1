package com.mock.taskmanager.service;

import com.mock.taskmanager.dto.request.LoginRequest;
import com.mock.taskmanager.dto.request.RegisterRequest;
import com.mock.taskmanager.dto.response.TokenResponse;
import com.mock.taskmanager.dto.response.UserResponse;
import com.mock.taskmanager.entity.Role;
import com.mock.taskmanager.entity.User;
import com.mock.taskmanager.exception.InvalidCredentialsException;
import com.mock.taskmanager.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final String missingUserHash;

    public AuthService(UserService userService, UserRepository userRepository,
            PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.missingUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public UserResponse register(RegisterRequest request) {
        return userService.create(request.email(), request.password(), Role.USER);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Optional<User> found = userRepository.findByEmail(UserService.normalizeEmail(request.email()));
        String hash = found.map(User::getPasswordHash).orElse(missingUserHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (found.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        return tokenService.issue(found.orElseThrow());
    }
}
