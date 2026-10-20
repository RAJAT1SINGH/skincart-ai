package com.skincart.provider;

import org.springframework.http.HttpStatus;

public class ProviderTimeoutException extends ProviderException {

    public ProviderTimeoutException(String message) {
        super(HttpStatus.GATEWAY_TIMEOUT, "PROVIDER_TIMEOUT", message);
    }
}
