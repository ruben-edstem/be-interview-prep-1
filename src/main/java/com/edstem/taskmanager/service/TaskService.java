package com.edstem.taskmanager.service;

import com.edstem.taskmanager.dto.request.TaskRequest;
import com.edstem.taskmanager.dto.response.TaskResponse;
import com.edstem.taskmanager.entity.Task;
import com.edstem.taskmanager.entity.TaskStatus;
import com.edstem.taskmanager.exception.InvalidRequestParameterException;
import com.edstem.taskmanager.exception.TaskNotFoundException;
import com.edstem.taskmanager.mapper.TaskMapper;
import com.edstem.taskmanager.repository.TaskRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskMapper taskMapper;

    @Transactional
    public TaskResponse create(TaskRequest request) {
        Task task = Task.builder()
                .title(request.title())
                .description(request.description())
                .status(request.status() != null ? request.status() : TaskStatus.TO_DO)
                .dueDate(request.dueDate())
                .build();
        return taskMapper.toResponse(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> list(TaskStatus status, Pageable pageable) {
        if (pageable.isPaged() && pageable.getOffset() > Integer.MAX_VALUE) {
            throw new InvalidRequestParameterException("page", "page is too large for the requested size");
        }
        Page<Task> tasks = status == null
                ? taskRepository.findAll(pageable)
                : taskRepository.findByStatus(status, pageable);
        return tasks.map(taskMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public TaskResponse get(UUID id) {
        return taskMapper.toResponse(find(id));
    }

    @Transactional
    public TaskResponse update(UUID id, TaskRequest request) {
        Task task = find(id);
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setDueDate(request.dueDate());
        if (request.status() != null) {
            task.setStatus(request.status());
        }
        return taskMapper.toResponse(taskRepository.save(task));
    }

    @Transactional
    public void delete(UUID id) {
        taskRepository.delete(find(id));
    }

    private Task find(UUID id) {
        return taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }
}
