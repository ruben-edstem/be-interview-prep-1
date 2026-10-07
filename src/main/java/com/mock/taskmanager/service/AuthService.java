package com.mock.taskmanager.service;

import com.mock.taskmanager.dto.request.LoginRequest;
import com.mock.taskmanager.dto.request.RegisterRequest;
import com.mock.taskmanager.dto.response.TokenResponse;
import com.mock.taskmanager.dto.response.UserResponse;
import com.mock.taskmanager.entity.Role;
import com.mock.taskmanager.entity.User;
import com.mock.taskmanager.exception.InvalidCredentialsException;
import com.mock.taskmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public UserResponse register(RegisterRequest request) {
        return userService.create(request.email(), request.password(), Role.USER);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(UserService.normalizeEmail(request.email()))
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return tokenService.issue(user);
    }
}
