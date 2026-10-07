package com.edstem.taskmanager.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        List<FieldViolation> fieldErrors) {

    public record FieldViolation(String field, String message) {
    }
}
