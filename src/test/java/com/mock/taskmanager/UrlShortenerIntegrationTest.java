package com.mock.taskmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UrlShortenerIntegrationTest {

    private static final String LONG_URL = "https://example.com/some/long/path?q=1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shortenedLinkRedirectsAndShowsInStats() throws Exception {
        String code = shorten(LONG_URL);

        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", LONG_URL));
        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound());

        mockMvc.perform(get("/api/v1/urls/" + code + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalUrl").value(LONG_URL))
                .andExpect(jsonPath("$.data.visitCount").value(2))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
    }

    @Test
    void shorteningTheSameUrlTwiceIssuesIndependentLinks() throws Exception {
        String first = shorten(LONG_URL);
        String second = shorten(LONG_URL);

        mockMvc.perform(get("/" + first)).andExpect(status().isFound());

        assertThat(first).isNotEqualTo(second);
        mockMvc.perform(get("/api/v1/urls/" + first + "/stats"))
                .andExpect(jsonPath("$.data.visitCount").value(1));
        mockMvc.perform(get("/api/v1/urls/" + second + "/stats"))
                .andExpect(jsonPath("$.data.visitCount").value(0));
    }

    @Test
    void unknownCodeIsNotFoundForRedirectAndStats() throws Exception {
        mockMvc.perform(get("/zzzzzzzz")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/urls/zzzzzzzz/stats")).andExpect(status().isNotFound());
    }

    @Test
    void visitCountStaysAccurateUnderConcurrentVisits() throws Exception {
        int visits = 200;
        int threads = 16;
        String code = shorten(LONG_URL);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        try {
            for (int i = 0; i < visits; i++) {
                Callable<Integer> visit = () -> {
                    start.await();
                    return mockMvc.perform(get("/" + code)).andReturn().getResponse().getStatus();
                };
                results.add(executor.submit(visit));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get()).isEqualTo(302);
            }
        } finally {
            executor.shutdownNow();
        }

        mockMvc.perform(get("/api/v1/urls/" + code + "/stats"))
                .andExpect(jsonPath("$.data.visitCount").value(visits));
    }

    private String shorten(String url) throws Exception {
        String body = mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + url + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");
        assertThat(data.get("code").asText()).hasSizeLessThanOrEqualTo(8);
        assertThat(data.get("shortUrl").asText()).endsWith("/" + data.get("code").asText());
        return data.get("code").asText();
    }
}
