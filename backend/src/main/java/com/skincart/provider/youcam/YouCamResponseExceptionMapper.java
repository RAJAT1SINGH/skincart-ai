package com.skincart.provider.youcam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skincart.provider.ProviderException;
import com.skincart.provider.ProviderTimeoutException;
import com.skincart.provider.youcam.dto.YouCamErrorResponse;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class YouCamResponseExceptionMapper {

    private static final java.util.Set<String> IMAGE_ERROR_CODES = java.util.Set.of(
            "error_below_min_image_size",
            "error_exceed_max_image_size",
            "error_src_face_too_small",
            "error_src_face_out_of_bound",
            "error_lighting_dark");

    private final ObjectMapper objectMapper;

    public YouCamResponseExceptionMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProviderException fromResponse(RestClientResponseException exception) {
        HttpStatus providerStatus = HttpStatus.resolve(exception.getStatusCode().value());
        String retryAfter = responseHeader(exception.getResponseHeaders(), HttpHeaders.RETRY_AFTER);

        if (providerStatus == HttpStatus.BAD_REQUEST) {
            String providerCode = readProviderErrorCode(exception.getResponseBodyAsByteArray());
            if (IMAGE_ERROR_CODES.contains(providerCode)) {
                return new ProviderException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "PROVIDER_IMAGE_REJECTED",
                        "The uploaded image could not be analyzed. Check the documented selfie requirements.");
            }
            String code = "InvalidParameters".equals(providerCode)
                    ? "PROVIDER_INVALID_PARAMETERS"
                    : "PROVIDER_REJECTED_REQUEST";
            return new ProviderException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    code,
                    "The skin analysis provider rejected the request.");
        }
        if (providerStatus == HttpStatus.UNAUTHORIZED || providerStatus == HttpStatus.FORBIDDEN) {
            return new ProviderException(
                    HttpStatus.BAD_GATEWAY,
                    "PROVIDER_AUTHENTICATION_FAILED",
                    "The skin analysis provider could not authenticate the request.");
        }
        if (providerStatus == HttpStatus.TOO_MANY_REQUESTS) {
            return new ProviderException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "PROVIDER_RATE_LIMITED",
                    "The skin analysis provider is rate limiting requests.",
                    retryAfter);
        }
        if (providerStatus != null && providerStatus.is5xxServerError()) {
            return new ProviderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PROVIDER_UNAVAILABLE",
                    "The skin analysis provider is temporarily unavailable.");
        }
        return new ProviderException(
                HttpStatus.BAD_GATEWAY,
                "PROVIDER_REQUEST_FAILED",
                "The skin analysis provider request could not be completed.");
    }

    public ProviderException fromTransport(RestClientException exception) {
        if (exception instanceof ResourceAccessException && causedByTimeout(exception)) {
            return new ProviderTimeoutException("The skin analysis provider request timed out.");
        }
        if (exception instanceof ResourceAccessException) {
            return new ProviderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PROVIDER_UNAVAILABLE",
                    "The skin analysis provider is temporarily unavailable.");
        }
        return new ProviderException(
                HttpStatus.BAD_GATEWAY,
                "PROVIDER_MALFORMED_RESPONSE",
                "The skin analysis provider returned an unexpected response.");
    }

    private boolean causedByTimeout(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private String readProviderErrorCode(byte[] responseBody) {
        if (responseBody == null || responseBody.length == 0) {
            return null;
        }
        try {
            YouCamErrorResponse response = objectMapper.readValue(responseBody, YouCamErrorResponse.class);
            return response.errorCode();
        } catch (IOException exception) {
            return null;
        }
    }

    private String responseHeader(HttpHeaders headers, String name) {
        if (headers == null) {
            return null;
        }
        return headers.getFirst(name);
    }
}
