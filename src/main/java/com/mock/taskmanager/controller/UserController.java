package com.mock.taskmanager.controller;

import com.mock.taskmanager.dto.response.ApiResponse;
import com.mock.taskmanager.dto.response.UserResponse;
import com.mock.taskmanager.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(JwtAuthenticationToken authentication) {
        return ApiResponse.ok(userService.getProfile(UUID.fromString(authentication.getName())));
    }

    @GetMapping
    public ApiResponse<PagedModel<UserResponse>> list(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ApiResponse.ok(new PagedModel<>(userService.list(pageable)));
    }
}
