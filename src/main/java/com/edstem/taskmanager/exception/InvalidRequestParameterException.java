package com.edstem.taskmanager.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class InvalidRequestParameterException extends ApiException {

    private final String parameter;
    private final String reason;

    public InvalidRequestParameterException(String parameter, String reason) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid request");
        this.parameter = parameter;
        this.reason = reason;
    }
}
