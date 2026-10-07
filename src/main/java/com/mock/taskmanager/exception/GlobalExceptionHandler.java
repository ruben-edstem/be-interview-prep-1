package com.mock.taskmanager.exception;

import com.mock.taskmanager.dto.response.ErrorResponse;
import com.mock.taskmanager.dto.response.ErrorResponse.FieldViolation;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApi(ApiException ex) {
        return respond(ex.getStatus(), ex.getCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(InvalidRequestParameterException.class)
    public ResponseEntity<Object> handleInvalidParameter(InvalidRequestParameterException ex) {
        FieldViolation violation = new FieldViolation(ex.getParameter(), ex.getReason());
        return respond(ex.getStatus(), ex.getCode(), ex.getMessage(), List.of(violation));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        FieldViolation violation = new FieldViolation(ex.getName(), "has an invalid value: " + ex.getValue());
        return respond(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, "Invalid request", List.of(violation));
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<Object> handleInvalidSort(PropertyReferenceException ex) {
        FieldViolation violation = new FieldViolation("sort", "cannot sort by " + ex.getPropertyName());
        return respond(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, "Invalid request", List.of(violation));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, "Invalid request", violations);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex.getCause() instanceof MismatchedInputException mismatch) {
            String field = fieldPath(mismatch);
            if (!field.isEmpty()) {
                FieldViolation violation = new FieldViolation(field, describe(mismatch));
                return respond(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, "Invalid request", List.of(violation));
            }
        }
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request body is missing or malformed", List.of());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        HttpStatus resolved = HttpStatus.resolve(statusCode.value());
        String code = resolved != null ? resolved.name() : "ERROR";
        String message = body instanceof ProblemDetail problem && problem.getDetail() != null
                ? problem.getDetail()
                : "Request could not be processed";
        ErrorResponse error = new ErrorResponse(Instant.now(), statusCode.value(), code, message, List.of());
        return ResponseEntity.status(statusCode).headers(headers).body(error);
    }

    private String fieldPath(JacksonException ex) {
        return ex.getPath().stream()
                .map(JacksonException.Reference::getPropertyName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("."));
    }

    private String describe(MismatchedInputException ex) {
        Class<?> target = ex.getTargetType();
        if (target != null && target.isEnum()) {
            return "must be one of " + Arrays.toString(target.getEnumConstants());
        }
        return "has an invalid value";
    }

    private ResponseEntity<Object> respond(
            HttpStatus status, String code, String message, List<FieldViolation> violations) {
        ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), code, message, violations);
        return ResponseEntity.status(status).body(body);
    }
}
