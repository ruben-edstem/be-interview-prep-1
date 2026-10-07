package com.edstem.interviewprep.urlshortener.exception;

import com.edstem.interviewprep.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ShortUrlNotFoundException extends ApiException {

    public ShortUrlNotFoundException(String code) {
        super(HttpStatus.NOT_FOUND, "SHORT_URL_NOT_FOUND", "No short URL exists for code " + code);
    }
}
