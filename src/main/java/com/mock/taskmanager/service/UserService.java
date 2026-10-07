package com.mock.taskmanager.service;

import com.mock.taskmanager.dto.response.UserResponse;
import com.mock.taskmanager.entity.Role;
import com.mock.taskmanager.entity.User;
import com.mock.taskmanager.exception.EmailAlreadyRegisteredException;
import com.mock.taskmanager.exception.InvalidRequestParameterException;
import com.mock.taskmanager.exception.UserNotFoundException;
import com.mock.taskmanager.mapper.UserMapper;
import com.mock.taskmanager.repository.UserRepository;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> SORTABLE_PROPERTIES = Set.of("email", "role", "createdAt");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public UserResponse create(String email, String rawPassword, Role role) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role)
                .build();
        try {
            return userMapper.toResponse(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    @Transactional
    public boolean createIfAbsent(String email, String rawPassword, Role role) {
        if (userRepository.existsByEmail(normalizeEmail(email))) {
            return false;
        }
        create(email, rawPassword, role);
        return true;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID id) {
        return userRepository.findById(id)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(Pageable pageable) {
        if (pageable.isPaged() && pageable.getOffset() > Integer.MAX_VALUE) {
            throw new InvalidRequestParameterException("page", "page is too large for the requested size");
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidRequestParameterException("sort", "cannot sort by " + order.getProperty());
            }
        }
        return userRepository.findAll(pageable).map(userMapper::toResponse);
    }
}
