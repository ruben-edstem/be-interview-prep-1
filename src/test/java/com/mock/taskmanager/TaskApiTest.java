package com.mock.taskmanager;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TaskApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unsupportedMethodReturns405InTheCommonErrorFormat() throws Exception {
        mockMvc.perform(delete("/api/v1/tasks"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        mockMvc.perform(post("/api/v1/tasks").contentType(MediaType.TEXT_PLAIN).content("a task"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unknownPathReturns404InTheCommonErrorFormat() throws Exception {
        mockMvc.perform(get("/api/v1/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unknownSortFieldReturns400NamingTheSortParameter() throws Exception {
        mockMvc.perform(get("/api/v1/tasks").param("sort", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sort"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("cannot sort by nope"));
    }

    @Test
    void unknownSortFieldWithAStatusFilterReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/tasks").param("status", "DONE").param("sort", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sort"));
    }

    @Test
    void hugePageNumberReturns400NamingThePageParameter() throws Exception {
        mockMvc.perform(get("/api/v1/tasks").param("page", "2000000").param("size", "2000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("page is too large for the requested size"));
    }

    @Test
    void unknownStatusInTheBodyReturns400NamingTheField() throws Exception {
        String body = """
                {"title": "Write tests", "status": "ARCHIVED"}
                """;

        mockMvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must be one of [TO_DO, IN_PROGRESS, DONE]"));
    }

    @Test
    void badlyFormattedDueDateReturns400NamingTheField() throws Exception {
        String body = """
                {"title": "Write tests", "dueDate": "tomorrow"}
                """;

        mockMvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("dueDate"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("has an invalid value"));
    }
}
