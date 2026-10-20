package com.skincart.exception;

import org.springframework.http.HttpStatus;

public class InvalidImageException extends ApplicationException {

    public InvalidImageException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
