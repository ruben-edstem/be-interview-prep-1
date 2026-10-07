package com.mock.taskmanager.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mock.taskmanager.entity.Task;
import com.mock.taskmanager.entity.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class TaskRepositoryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void findByStatusReturnsOnlyTasksWithThatStatus() {
        taskRepository.save(task("todo", TaskStatus.TO_DO));
        taskRepository.save(task("doing", TaskStatus.IN_PROGRESS));
        taskRepository.save(task("done", TaskStatus.DONE));

        Page<Task> page = taskRepository.findByStatus(TaskStatus.IN_PROGRESS, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Task::getTitle).containsExactly("doing");
    }

    @Test
    void findByStatusReturnsNothingWhenNoTaskMatches() {
        taskRepository.save(task("todo", TaskStatus.TO_DO));

        Page<Task> page = taskRepository.findByStatus(TaskStatus.DONE, PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void savingATaskAssignsAnIdAndCreatedDate() {
        Task saved = taskRepository.saveAndFlush(task("todo", TaskStatus.TO_DO));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    private Task task(String title, TaskStatus status) {
        return Task.builder().title(title).status(status).build();
    }
}
