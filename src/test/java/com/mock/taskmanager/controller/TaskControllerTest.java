package com.mock.taskmanager.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mock.taskmanager.dto.request.TaskRequest;
import com.mock.taskmanager.dto.response.TaskResponse;
import com.mock.taskmanager.entity.TaskStatus;
import com.mock.taskmanager.exception.TaskNotFoundException;
import com.mock.taskmanager.service.TaskService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    private static final UUID TASK_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    @Test
    void createReturns201WithTheCreatedTask() throws Exception {
        LocalDate dueDate = LocalDate.now().plusDays(3);
        TaskResponse created = response(TaskStatus.TO_DO, dueDate);
        when(taskService.create(any(TaskRequest.class))).thenReturn(created);

        String body = """
                {"title": "Write tests", "description": "cover the API", "dueDate": "%s"}
                """.formatted(dueDate);

        mockMvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(TASK_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("TO_DO"))
                .andExpect(jsonPath("$.data.dueDate").value(dueDate.toString()));
    }

    @Test
    void createWithBlankTitleReturns400WithAFieldMessage() throws Exception {
        String body = """
                {"title": "  "}
                """;

        mockMvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("title is required"));
    }

    @Test
    void createWithTitleOver100CharactersReturns400() throws Exception {
        String body = """
                {"title": "%s"}
                """.formatted("a".repeat(101));

        mockMvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("title must be at most 100 characters"));
    }

    @Test
    void createWithTitleOfExactly100CharactersIsAccepted() throws Exception {
        when(taskService.create(any(TaskRequest.class))).thenReturn(response(TaskStatus.TO_DO, null));
        String body = """
                {"title": "%s"}
                """.formatted("a".repeat(100));

        mockMvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void createWithDescriptionOver2000CharactersReturns400() throws Exception {
        String body = """
                {"title": "Write tests", "description": "%s"}
                """.formatted("a".repeat(2001));

        mockMvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("description"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("description must be at most 2000 characters"));
    }

    @Test
    void createWithDescriptionOfExactly2000CharactersIsAccepted() throws Exception {
        when(taskService.create(any(TaskRequest.class))).thenReturn(response(TaskStatus.TO_DO, null));
        String body = """
                {"title": "Write tests", "description": "%s"}
                """.formatted("a".repeat(2000));

        mockMvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void createWithPastDueDateReturns400() throws Exception {
        String body = """
                {"title": "Write tests", "dueDate": "%s"}
                """.formatted(LocalDate.now().minusDays(1));

        mockMvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("dueDate"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("dueDate cannot be in the past"));
    }

    @Test
    void createWithSeveralInvalidFieldsReportsEachOne() throws Exception {
        String body = """
                {"title": "", "dueDate": "%s"}
                """.formatted(LocalDate.now().minusDays(1));

        mockMvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void createWithUnknownStatusReturns400() throws Exception {
        String body = """
                {"title": "Write tests", "status": "ARCHIVED"}
                """;

        mockMvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"));
    }

    @Test
    void createWithMalformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void listPassesTheStatusFilterToTheService() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(taskService.list(eq(TaskStatus.DONE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response(TaskStatus.DONE, null)), pageable, 1));

        mockMvc.perform(get("/api/v1/tasks").param("status", "DONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].status").value("DONE"))
                .andExpect(jsonPath("$.data.page.totalElements").value(1));

        verify(taskService).list(eq(TaskStatus.DONE), any(Pageable.class));
    }

    @Test
    void listWithoutStatusDoesNotFilter() throws Exception {
        when(taskService.list(eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    void listWithInvalidStatusReturns400NamingTheParameter() throws Exception {
        mockMvc.perform(get("/api/v1/tasks").param("status", "ARCHIVED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"));
    }

    @Test
    void getReturnsTheTask() throws Exception {
        when(taskService.get(TASK_ID)).thenReturn(response(TaskStatus.IN_PROGRESS, null));

        mockMvc.perform(get("/api/v1/tasks/{id}", TASK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    void getUnknownTaskReturns404InTheCommonErrorFormat() throws Exception {
        when(taskService.get(TASK_ID)).thenThrow(new TaskNotFoundException(TASK_ID));

        mockMvc.perform(get("/api/v1/tasks/{id}", TASK_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Task not found: " + TASK_ID));
    }

    @Test
    void getWithMalformedIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("id"));
    }

    @Test
    void updateReturnsTheUpdatedTask() throws Exception {
        when(taskService.update(eq(TASK_ID), any(TaskRequest.class)))
                .thenReturn(response(TaskStatus.DONE, null));
        String body = """
                {"title": "Write tests", "status": "DONE"}
                """;

        mockMvc.perform(put("/api/v1/tasks/{id}", TASK_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"));
    }

    @Test
    void updateWithInvalidInputReturns400() throws Exception {
        String body = """
                {"title": ""}
                """;

        mockMvc.perform(put("/api/v1/tasks/{id}", TASK_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
    }

    @Test
    void updateUnknownTaskReturns404() throws Exception {
        when(taskService.update(eq(TASK_ID), any(TaskRequest.class)))
                .thenThrow(new TaskNotFoundException(TASK_ID));
        String body = """
                {"title": "Write tests"}
                """;

        mockMvc.perform(put("/api/v1/tasks/{id}", TASK_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/tasks/{id}", TASK_ID))
                .andExpect(status().isNoContent());

        verify(taskService).delete(TASK_ID);
    }

    @Test
    void deleteUnknownTaskReturns404() throws Exception {
        doThrow(new TaskNotFoundException(TASK_ID)).when(taskService).delete(TASK_ID);

        mockMvc.perform(delete("/api/v1/tasks/{id}", TASK_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void unexpectedFailureReturns500WithoutLeakingDetails() throws Exception {
        when(taskService.get(TASK_ID)).thenThrow(new IllegalStateException("db password is hunter2"));

        mockMvc.perform(get("/api/v1/tasks/{id}", TASK_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    private TaskResponse response(TaskStatus status, LocalDate dueDate) {
        return new TaskResponse(TASK_ID, "Write tests", "cover the API", status, dueDate, Instant.parse("2026-10-07T00:00:00Z"));
    }
}
