package com.navio.trip.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class UserOperationNotAllowedException extends RuntimeException {

    public UserOperationNotAllowedException(String message) {
        super(message);
    }
}
