package com.mock.taskmanager.exception;

import com.mock.taskmanager.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ShortUrlExpiredException extends ApiException {

    public ShortUrlExpiredException(String code) {
        super(HttpStatus.GONE, "SHORT_URL_EXPIRED", "Short URL " + code + " has expired");
    }
}
