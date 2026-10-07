package com.mock.taskmanager;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = "app.security.rate-limit.ip-requests=3")
@AutoConfigureMockMvc
class AuthRateLimitApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginIsRefusedWithA429OnceTheClientExceedsTheLimit() throws Exception {
        RequestPostProcessor client = fromAddress("10.0.0.1");
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login").with(client)
                            .contentType(MediaType.APPLICATION_JSON).content(unknownUserBody()))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login").with(client)
                        .contentType(MediaType.APPLICATION_JSON).content(unknownUserBody()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void theLimitAlsoCoversRegistrationFromTheSameClient() throws Exception {
        RequestPostProcessor client = fromAddress("10.0.0.2");
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(post("/api/v1/auth/register").with(client)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/v1/auth/register").with(client)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void oneClientBeingLimitedDoesNotAffectAnother() throws Exception {
        RequestPostProcessor noisy = fromAddress("10.0.0.3");
        for (int attempt = 0; attempt < 4; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login").with(noisy)
                    .contentType(MediaType.APPLICATION_JSON).content(unknownUserBody()));
        }

        mockMvc.perform(post("/api/v1/auth/login").with(fromAddress("10.0.0.4"))
                        .contentType(MediaType.APPLICATION_JSON).content(unknownUserBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void otherEndpointsAreNotCountedAgainstTheLimit() throws Exception {
        RequestPostProcessor client = fromAddress("10.0.0.5");
        for (int attempt = 0; attempt < 10; attempt++) {
            mockMvc.perform(get("/api/v1/users/me").with(client))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
    }

    private String unknownUserBody() {
        return """
                {"email": "nobody-%s@example.com", "password": "s3cret-pass"}
                """.formatted(UUID.randomUUID());
    }

    private RequestPostProcessor fromAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
