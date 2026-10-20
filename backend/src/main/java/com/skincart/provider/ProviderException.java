package com.skincart.provider;

import com.skincart.exception.ApplicationException;
import org.springframework.http.HttpStatus;

public class ProviderException extends ApplicationException {

    private final String retryAfter;

    public ProviderException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ProviderException(HttpStatus status, String code, String message, String retryAfter) {
        super(status, code, message);
        this.retryAfter = retryAfter;
    }

    public String getRetryAfter() {
        return retryAfter;
    }
}
