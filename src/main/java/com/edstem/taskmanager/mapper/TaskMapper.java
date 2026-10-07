package com.edstem.taskmanager.mapper;

import com.edstem.taskmanager.dto.response.TaskResponse;
import com.edstem.taskmanager.entity.Task;
import org.springframework.stereotype.Component;

@Component
public class TaskMapper {

    public TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getDueDate(),
                task.getCreatedAt());
    }
}
