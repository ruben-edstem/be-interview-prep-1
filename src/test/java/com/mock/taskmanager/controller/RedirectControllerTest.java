package com.mock.taskmanager.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mock.taskmanager.config.SecurityConfig;
import com.mock.taskmanager.exception.ShortUrlExpiredException;
import com.mock.taskmanager.exception.ShortUrlNotFoundException;
import com.mock.taskmanager.service.ShortUrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RedirectController.class)
@Import(SecurityConfig.class)
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShortUrlService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void redirectsToOriginalUrlWithoutCaching() throws Exception {
        when(service.resolve("abc12345")).thenReturn("https://example.com/a?x=1&y=2");

        mockMvc.perform(get("/abc12345"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/a?x=1&y=2"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void returnsNotFoundForUnknownCode() throws Exception {
        when(service.resolve("missing1")).thenThrow(new ShortUrlNotFoundException("missing1"));

        mockMvc.perform(get("/missing1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsGoneForExpiredCode() throws Exception {
        when(service.resolve("old12345")).thenThrow(new ShortUrlExpiredException("old12345"));

        mockMvc.perform(get("/old12345"))
                .andExpect(status().isGone());
    }

    @Test
    void rejectsMalformedCodeWithoutLookup() throws Exception {
        mockMvc.perform(get("/not-a-code!"))
                .andExpect(status().isBadRequest());

        verify(service, never()).resolve(any());
    }
}
