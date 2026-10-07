package com.edstem.interviewprep.common.exception;

import com.edstem.interviewprep.common.dto.ApiResponse;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidBody(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidParameter(
            HandlerMethodValidationException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error("Invalid request parameter"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error("Malformed request body"));
    }
}
