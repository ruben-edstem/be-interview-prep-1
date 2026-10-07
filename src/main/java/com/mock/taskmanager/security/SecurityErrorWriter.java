package com.mock.taskmanager.security;

import com.mock.taskmanager.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {

    private final JsonMapper jsonMapper;

    public void write(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), code, message, List.of());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
