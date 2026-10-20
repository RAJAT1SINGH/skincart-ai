package com.skincart.provider.youcam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skincart.common.AnalysisStatus;
import com.skincart.provider.ProviderException;
import com.skincart.provider.SkinSimulationSnapshot;
import com.skincart.provider.SkinSimulationSubmission;
import com.skincart.provider.youcam.dto.YouCamErrorResponse;
import com.skincart.provider.youcam.dto.YouCamSkinSimulationRequest;
import com.skincart.provider.youcam.dto.YouCamSkinSimulationStatusResponse;
import com.skincart.provider.youcam.dto.YouCamTaskResponse;
import com.skincart.simulation.SkinSimulationFocus;
import java.io.IOException;
import java.net.URI;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class YouCamSkinSimulationClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(YouCamSkinSimulationClient.class);
    private static final String CREATE_TASK_ENDPOINT = "/s2s/v2.0/task/skin-simulation";
    private static final String TASK_STATUS_ENDPOINT = "/s2s/v2.0/task/skin-simulation/{taskId}";
    private static final int MAX_LOG_DETAIL_LENGTH = 256;
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\bhttps?://[^\\s<>\\\"']+");
    private static final Pattern CREDENTIAL_PATTERN = Pattern.compile(
            "(?i)\\b(api[_ -]?key|access[_ -]?token|authorization|bearer|secret|token)\\b\\s*[:=]?\\s*[^\\s,;\\\"']+");
    private static final Pattern LONG_TOKEN_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9_-])[A-Za-z0-9_+/=-]{24,}(?![A-Za-z0-9_-])");

    private final RestClient apiClient;
    private final YouCamResponseExceptionMapper exceptionMapper;
    private final ObjectMapper objectMapper;

    public YouCamSkinSimulationClient(
            RestClient apiClient, YouCamResponseExceptionMapper exceptionMapper, ObjectMapper objectMapper) {
        this.apiClient = apiClient;
        this.exceptionMapper = exceptionMapper;
        this.objectMapper = objectMapper;
    }

    public SkinSimulationSubmission createTask(String sourceFileId, SkinSimulationFocus focus) {
        YouCamSkinSimulationRequest request = YouCamSkinSimulationRequest.forFocus(sourceFileId, focus);
        LOGGER.info(
                "YouCam Skin Simulation task creation started: sourceFileIdPresent={}, focus={}",
                sourceFileId != null && !sourceFileId.isBlank(),
                focus);
        try {
            ResponseEntity<JsonNode> entity = apiClient.post()
                    .uri(CREATE_TASK_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(JsonNode.class);
            JsonNode body = entity.getBody();
            LOGGER.info(
                    "YouCam Skin Simulation task creation response: httpStatus={}",
                    entity.getStatusCode().value());
            YouCamTaskResponse response = toResponse(body, YouCamTaskResponse.class, "task_creation");
            if (response == null
                    || !Integer.valueOf(200).equals(response.status())
                    || response.data() == null
                    || response.data().taskId() == null
                    || response.data().taskId().isBlank()) {
                throw malformedResponse("task_id_missing");
            }
            LOGGER.info(
                    "YouCam Skin Simulation task created: httpStatus={}, providerResponseStatus={}",
                    entity.getStatusCode().value(),
                    response.status());
            return new SkinSimulationSubmission(response.data().taskId());
        } catch (RestClientResponseException exception) {
            logHttpFailure("task creation", exception);
            throw exceptionMapper.fromResponse(exception);
        } catch (RestClientException exception) {
            LOGGER.warn(
                    "YouCam Skin Simulation task creation failed before receiving an HTTP response: transportError={}",
                    exception.getClass().getSimpleName());
            throw exceptionMapper.fromTransport(exception);
        }
    }

    public SkinSimulationSnapshot getTask(String taskId) {
        try {
            ResponseEntity<JsonNode> entity = apiClient.get()
                    .uri(TASK_STATUS_ENDPOINT, taskId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toEntity(JsonNode.class);
            JsonNode body = entity.getBody();
            LOGGER.info(
                    "YouCam Skin Simulation poll response: httpStatus={}",
                    entity.getStatusCode().value());
            YouCamSkinSimulationStatusResponse response = toResponse(
                    body, YouCamSkinSimulationStatusResponse.class, "task_status");
            return toSnapshot(response, entity.getStatusCode().value());
        } catch (RestClientResponseException exception) {
            logHttpFailure("task polling", exception);
            throw exceptionMapper.fromResponse(exception);
        } catch (RestClientException exception) {
            LOGGER.warn(
                    "YouCam Skin Simulation polling failed before receiving an HTTP response: transportError={}",
                    exception.getClass().getSimpleName());
            throw exceptionMapper.fromTransport(exception);
        }
    }

    private <T> T toResponse(JsonNode body, Class<T> responseType, String operation) {
        if (body == null) {
            return null;
        }
        try {
            return objectMapper.treeToValue(body, responseType);
        } catch (JsonProcessingException exception) {
            throw malformedResponse(operation + "_mapping_failed");
        }
    }

    private void logHttpFailure(String operation, RestClientResponseException exception) {
        String providerErrorCode = "not_provided";
        String providerErrorMessage = "not_provided";
        try {
            YouCamErrorResponse response = objectMapper.readValue(
                    exception.getResponseBodyAsByteArray(), YouCamErrorResponse.class);
            providerErrorCode = safeLogDetail(response.errorCode());
            providerErrorMessage = safeLogDetail(response.error());
        } catch (IOException ignored) {
            // Keep HTTP diagnostics useful without logging an unrecognized provider response body.
        }
        LOGGER.warn(
                "YouCam Skin Simulation {} failed: httpStatus={}, providerErrorCode={}, providerErrorMessage={}",
                operation,
                exception.getStatusCode().value(),
                providerErrorCode,
                providerErrorMessage);
    }

    private SkinSimulationSnapshot toSnapshot(YouCamSkinSimulationStatusResponse response, int httpStatus) {
        if (response == null
                || !Integer.valueOf(200).equals(response.status())
                || response.data() == null
                || response.data().taskStatus() == null) {
            throw malformedResponse("task_status_missing");
        }

        return switch (response.data().taskStatus()) {
            case "running" -> {
                LOGGER.info(
                        "YouCam Skin Simulation provider task status: httpStatus={}, providerResponseStatus={}, "
                                + "taskStatus=running",
                        httpStatus,
                        response.status());
                yield new SkinSimulationSnapshot(AnalysisStatus.PROCESSING, null);
            }
            case "error" -> {
                LOGGER.warn(
                        "YouCam Skin Simulation provider task failed: httpStatus={}, providerResponseStatus={}, "
                                + "taskStatus=error, providerErrorCode={}, providerErrorMessage={}",
                        httpStatus,
                        response.status(),
                        safeLogDetail(response.data().error()),
                        safeLogDetail(response.data().errorMessage()));
                yield new SkinSimulationSnapshot(AnalysisStatus.FAILED, null);
            }
            case "success" -> {
                LOGGER.info(
                        "YouCam Skin Simulation provider task status: httpStatus={}, providerResponseStatus={}, "
                                + "taskStatus=success",
                        httpStatus,
                        response.status());
                yield new SkinSimulationSnapshot(
                        AnalysisStatus.COMPLETED, requireHttpsResultUrl(response.data().results()));
            }
            default -> throw malformedResponse("unsupported_task_status");
        };
    }

    private String requireHttpsResultUrl(YouCamSkinSimulationStatusResponse.Results results) {
        if (results == null || results.url() == null || results.url().isBlank()) {
            throw malformedResponse("result_url_missing");
        }
        try {
            URI uri = URI.create(results.url());
            if (!uri.isAbsolute() || uri.getHost() == null || !"https".equalsIgnoreCase(uri.getScheme())) {
                throw malformedResponse("result_url_invalid");
            }
        } catch (IllegalArgumentException exception) {
            throw malformedResponse("result_url_invalid");
        }
        return results.url();
    }

    private String safeLogDetail(String value) {
        if (value == null || value.isBlank()) {
            return "not_provided";
        }
        String sanitized = URL_PATTERN.matcher(value).replaceAll("[redacted-url]");
        sanitized = CREDENTIAL_PATTERN.matcher(sanitized).replaceAll("[redacted-credential]");
        sanitized = LONG_TOKEN_PATTERN.matcher(sanitized).replaceAll("[redacted-token]");
        sanitized = sanitized.replaceAll("[\\r\\n\\t]", " ");
        return sanitized.length() <= MAX_LOG_DETAIL_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_LOG_DETAIL_LENGTH) + "...";
    }

    private ProviderException malformedResponse(String reason) {
        LOGGER.warn("YouCam Skin Simulation response mapping failed: reason={}", reason);
        return new ProviderException(
                org.springframework.http.HttpStatus.BAD_GATEWAY,
                "PROVIDER_MALFORMED_RESPONSE",
                "The skin simulation provider returned an unexpected response.");
    }
}
