package com.skincart.exception;

import org.springframework.http.HttpStatus;

public class SkinSimulationUnavailableException extends ApplicationException {

    public SkinSimulationUnavailableException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
