package com.skincart.provider.youcam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skincart.common.AnalysisStatus;
import com.skincart.provider.ProviderException;
import com.skincart.provider.SkinAnalysisResult;
import com.skincart.provider.SkinAnalysisSnapshot;
import com.skincart.provider.SkinAnalysisSubmission;
import com.skincart.provider.SkinConcernResult;
import com.skincart.provider.youcam.dto.YouCamSkinAnalysisRequest;
import com.skincart.provider.youcam.dto.YouCamTaskResponse;
import com.skincart.provider.youcam.dto.YouCamTaskStatusResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class YouCamSkinAnalysisClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(YouCamSkinAnalysisClient.class);
    private static final String CREATE_TASK_ENDPOINT = "/s2s/v2.1/task/skin-analysis";
    private static final String TASK_STATUS_ENDPOINT = "/s2s/v2.1/task/skin-analysis/{taskId}";
    private static final Set<String> SUPPORTED_CONCERNS = Set.of("wrinkle", "pore", "texture", "acne");
    private static final int MAX_LOG_DETAIL_LENGTH = 256;
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\bhttps?://[^\\s<>\\\"']+");
    private static final Pattern CREDENTIAL_PATTERN = Pattern.compile(
            "(?i)\\b(api[_ -]?key|access[_ -]?token|authorization|bearer|secret|token)\\b\\s*[:=]?\\s*[^\\s,;\\\"']+");
    private static final Pattern LONG_TOKEN_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9_-])[A-Za-z0-9_+/=-]{24,}(?![A-Za-z0-9_-])");

    private final RestClient apiClient;
    private final YouCamResponseExceptionMapper exceptionMapper;
    private final ObjectMapper objectMapper;

    public YouCamSkinAnalysisClient(
            RestClient apiClient, YouCamResponseExceptionMapper exceptionMapper, ObjectMapper objectMapper) {
        this.apiClient = apiClient;
        this.exceptionMapper = exceptionMapper;
        this.objectMapper = objectMapper;
    }

    public SkinAnalysisSubmission createTask(String fileId, List<String> concerns) {
        YouCamSkinAnalysisRequest request = new YouCamSkinAnalysisRequest(fileId, List.copyOf(concerns), "json");
        try {
            LOGGER.info("YouCam Skin Analysis task creation started");
            ResponseEntity<YouCamTaskResponse> entity = apiClient.post()
                    .uri(CREATE_TASK_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(YouCamTaskResponse.class);
            YouCamTaskResponse response = entity.getBody();
            if (response == null
                    || !Integer.valueOf(200).equals(response.status())
                    || response.data() == null
                    || response.data().taskId() == null
                    || response.data().taskId().isBlank()) {
                throw malformedResponse();
            }
            LOGGER.info(
                    "YouCam Skin Analysis task created: httpStatus={}, providerResponseStatus={}",
                    entity.getStatusCode().value(),
                    response.status());
            return new SkinAnalysisSubmission(response.data().taskId(), fileId);
        } catch (RestClientResponseException exception) {
            LOGGER.warn(
                    "YouCam Skin Analysis task creation failed: httpStatus={}",
                    exception.getStatusCode().value());
            throw exceptionMapper.fromResponse(exception);
        } catch (RestClientException exception) {
            LOGGER.warn(
                    "YouCam Skin Analysis task creation failed before receiving an HTTP response: transportError={}",
                    exception.getClass().getSimpleName());
            throw exceptionMapper.fromTransport(exception);
        }
    }

    public SkinAnalysisSnapshot getTask(String taskId) {
        try {
            ResponseEntity<JsonNode> entity = apiClient.get()
                    .uri(TASK_STATUS_ENDPOINT, taskId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toEntity(JsonNode.class);
            JsonNode body = entity.getBody();
            return toSnapshot(toTaskStatusResponse(body), entity.getStatusCode().value());
        } catch (RestClientResponseException exception) {
            LOGGER.warn(
                    "YouCam Skin Analysis polling request failed: httpStatus={}",
                    exception.getStatusCode().value());
            throw exceptionMapper.fromResponse(exception);
        } catch (RestClientException exception) {
            LOGGER.warn(
                    "YouCam Skin Analysis polling request failed before receiving an HTTP response: transportError={}",
                    exception.getClass().getSimpleName());
            throw exceptionMapper.fromTransport(exception);
        }
    }

    private YouCamTaskStatusResponse toTaskStatusResponse(JsonNode body) {
        if (body == null) {
            return null;
        }
        try {
            return objectMapper.treeToValue(body, YouCamTaskStatusResponse.class);
        } catch (JsonProcessingException exception) {
            LOGGER.warn(
                    "YouCam Skin Analysis task-status JSON could not be mapped: exceptionClass={}",
                    exception.getClass().getSimpleName());
            throw malformedResponse();
        }
    }

    private SkinAnalysisSnapshot toSnapshot(YouCamTaskStatusResponse response, int httpStatus) {
        if (response == null
                || !Integer.valueOf(200).equals(response.status())
                || response.data() == null
                || response.data().taskStatus() == null) {
            throw malformedResponse();
        }

        return switch (response.data().taskStatus()) {
            case "running" -> {
                LOGGER.info(
                        "YouCam Skin Analysis poll response: httpStatus={}, providerResponseStatus={}, "
                                + "taskStatus=running",
                        httpStatus,
                        response.status());
                yield new SkinAnalysisSnapshot(AnalysisStatus.PROCESSING, null);
            }
            case "error" -> {
                LOGGER.warn(
                        "YouCam skin-analysis task failed: httpStatus={}, providerResponseStatus={}, taskStatus={}, "
                                + "providerErrorCode={}, providerErrorMessage={}",
                        httpStatus,
                        response.status(),
                        response.data().taskStatus(),
                        response.data().error(),
                        safeLogDetail(response.data().errorMessage()));
                yield new SkinAnalysisSnapshot(AnalysisStatus.FAILED, null);
            }
            case "success" -> {
                LOGGER.info(
                        "YouCam Skin Analysis poll response: httpStatus={}, providerResponseStatus={}, "
                                + "taskStatus=success",
                        httpStatus,
                        response.status());
                yield new SkinAnalysisSnapshot(AnalysisStatus.COMPLETED, toResult(response.data().results()));
            }
            default -> throw malformedResponse();
        };
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

    private SkinAnalysisResult toResult(YouCamTaskStatusResponse.Results results) {
        if (results == null) {
            throw malformedSuccessfulResult("results_missing");
        }
        if (results.output() == null) {
            throw malformedSuccessfulResult("results_output_missing");
        }
        List<SkinConcernResult> concerns = new ArrayList<>(results.output().size());
        for (int index = 0; index < results.output().size(); index++) {
            YouCamTaskStatusResponse.Output output = results.output().get(index);
            if (output == null) {
                throw malformedSuccessfulResult("output_entry_null,index=" + index);
            }
            String concernType = output.type();
            if (concernType == null || !SUPPORTED_CONCERNS.contains(concernType)) {
                continue;
            }
            if (output.uiScore() == null) {
                throw malformedSuccessfulResult("output_ui_score_missing,index=" + index);
            }
            if (output.rawScore() == null) {
                throw malformedSuccessfulResult("output_raw_score_missing,index=" + index);
            }
            if (output.maskUrls() != null
                    && output.maskUrls().stream().anyMatch(java.util.Objects::isNull)) {
                throw malformedSuccessfulResult("output_mask_urls_contains_null,index=" + index);
            }
            concerns.add(new SkinConcernResult(
                    concernType, output.uiScore(), output.rawScore(), output.maskUrls()));
        }
        return new SkinAnalysisResult(null, concerns);
    }

    private ProviderException malformedSuccessfulResult(String reason) {
        LOGGER.warn("YouCam successful response mapping failed: reason={}", reason);
        return malformedResponse();
    }

    private ProviderException malformedResponse() {
        return new ProviderException(
                org.springframework.http.HttpStatus.BAD_GATEWAY,
                "PROVIDER_MALFORMED_RESPONSE",
                "The skin analysis provider returned an unexpected response.");
    }
}
