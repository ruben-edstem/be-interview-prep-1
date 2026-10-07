package com.mock.taskmanager.mapper;

import com.mock.taskmanager.dto.response.UserResponse;
import com.mock.taskmanager.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
