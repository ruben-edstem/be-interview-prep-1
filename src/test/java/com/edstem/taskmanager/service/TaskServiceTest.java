package com.edstem.taskmanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edstem.taskmanager.dto.request.TaskRequest;
import com.edstem.taskmanager.dto.response.TaskResponse;
import com.edstem.taskmanager.entity.Task;
import com.edstem.taskmanager.entity.TaskStatus;
import com.edstem.taskmanager.exception.TaskNotFoundException;
import com.edstem.taskmanager.mapper.TaskMapper;
import com.edstem.taskmanager.repository.TaskRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final UUID TASK_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private TaskRepository taskRepository;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, new TaskMapper());
    }

    @Test
    void createDefaultsTheStatusToToDo() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TaskRequest request = new TaskRequest("Write tests", "cover the API", null, null);

        TaskResponse created = taskService.create(request);

        assertThat(created.status()).isEqualTo(TaskStatus.TO_DO);
        assertThat(created.title()).isEqualTo("Write tests");
    }

    @Test
    void createKeepsTheRequestedStatusAndDueDate() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDate dueDate = LocalDate.now().plusDays(2);
        TaskRequest request = new TaskRequest("Write tests", null, TaskStatus.IN_PROGRESS, dueDate);

        TaskResponse created = taskService.create(request);

        assertThat(created.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(created.dueDate()).isEqualTo(dueDate);
    }

    @Test
    void listWithoutStatusReturnsEveryTask() {
        Pageable pageable = PageRequest.of(0, 20);
        when(taskRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(task(TaskStatus.TO_DO))));

        Page<TaskResponse> page = taskService.list(null, pageable);

        assertThat(page.getContent()).hasSize(1);
        verify(taskRepository, never()).findByStatus(any(), any());
    }

    @Test
    void listWithStatusFiltersByThatStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        when(taskRepository.findByStatus(TaskStatus.DONE, pageable))
                .thenReturn(new PageImpl<>(List.of(task(TaskStatus.DONE))));

        Page<TaskResponse> page = taskService.list(TaskStatus.DONE, pageable);

        assertThat(page.getContent()).extracting(TaskResponse::status).containsExactly(TaskStatus.DONE);
        verify(taskRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void getReturnsTheTask() {
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task(TaskStatus.TO_DO)));

        TaskResponse found = taskService.get(TASK_ID);

        assertThat(found.title()).isEqualTo("Write tests");
    }

    @Test
    void getUnknownTaskThrowsNotFound() {
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.empty());

        TaskNotFoundException thrown = assertThrows(TaskNotFoundException.class, () -> taskService.get(TASK_ID));

        assertThat(thrown.getMessage()).contains(TASK_ID.toString());
    }

    @Test
    void updateReplacesTheEditableFields() {
        Task existing = task(TaskStatus.TO_DO);
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDate dueDate = LocalDate.now().plusDays(5);
        TaskRequest request = new TaskRequest("New title", "New description", TaskStatus.DONE, dueDate);

        TaskResponse updated = taskService.update(TASK_ID, request);

        assertThat(updated.title()).isEqualTo("New title");
        assertThat(updated.description()).isEqualTo("New description");
        assertThat(updated.status()).isEqualTo(TaskStatus.DONE);
        assertThat(updated.dueDate()).isEqualTo(dueDate);
    }

    @Test
    void updateWithoutStatusKeepsTheCurrentStatus() {
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task(TaskStatus.IN_PROGRESS)));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TaskRequest request = new TaskRequest("New title", null, null, null);

        TaskResponse updated = taskService.update(TASK_ID, request);

        assertThat(updated.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void updateUnknownTaskThrowsNotFoundAndSavesNothing() {
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.empty());
        TaskRequest request = new TaskRequest("New title", null, null, null);

        assertThrows(TaskNotFoundException.class, () -> taskService.update(TASK_ID, request));

        verify(taskRepository, never()).save(any());
    }

    @Test
    void deleteRemovesTheTask() {
        Task existing = task(TaskStatus.TO_DO);
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(existing));

        taskService.delete(TASK_ID);

        ArgumentCaptor<Task> deleted = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).delete(deleted.capture());
        assertThat(deleted.getValue()).isSameAs(existing);
    }

    @Test
    void deleteUnknownTaskThrowsNotFound() {
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.empty());

        assertThrows(TaskNotFoundException.class, () -> taskService.delete(TASK_ID));

        verify(taskRepository, never()).delete(any());
    }

    private Task task(TaskStatus status) {
        return Task.builder()
                .id(TASK_ID)
                .title("Write tests")
                .description("cover the API")
                .status(status)
                .build();
    }
}
