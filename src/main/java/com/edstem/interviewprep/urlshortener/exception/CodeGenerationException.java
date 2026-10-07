package com.edstem.interviewprep.urlshortener.exception;

import com.edstem.interviewprep.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CodeGenerationException extends ApiException {

    public CodeGenerationException(int attempts) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "CODE_GENERATION_FAILED",
                "Could not generate a unique short code after " + attempts + " attempts");
    }
}
