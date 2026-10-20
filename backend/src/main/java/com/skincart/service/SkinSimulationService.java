package com.skincart.service;

import com.skincart.common.AnalysisStatus;
import com.skincart.dto.request.SkinSimulationRequest;
import com.skincart.dto.response.SkinSimulationResponse;
import com.skincart.entity.SkinAnalysis;
import com.skincart.entity.SkinConcern;
import com.skincart.exception.ResourceNotFoundException;
import com.skincart.exception.SkinAnalysisFailedException;
import com.skincart.exception.SkinSimulationNotReadyException;
import com.skincart.exception.SkinSimulationUnavailableException;
import com.skincart.provider.ProviderException;
import com.skincart.provider.ProviderTimeoutException;
import com.skincart.provider.SkinSimulationProvider;
import com.skincart.provider.SkinSimulationSnapshot;
import com.skincart.provider.SkinSimulationSubmission;
import com.skincart.repository.SkinAnalysisRepository;
import com.skincart.repository.SkinConcernRepository;
import com.skincart.simulation.SkinSimulationFocus;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SkinSimulationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkinSimulationService.class);
    private static final Duration PROVIDER_FILE_RETENTION = Duration.ofDays(30);
    private static final int MAX_POLL_ATTEMPTS = 6;
    private static final String POLLING_TIMEOUT_MESSAGE = "Skin simulation did not complete within the polling window.";
    private static final int MAX_LOG_DETAIL_LENGTH = 256;
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\bhttps?://[^\\s<>\\\"']+");
    private static final Pattern CREDENTIAL_PATTERN = Pattern.compile(
            "(?i)\\b(api[_ -]?key|access[_ -]?token|authorization|bearer|secret|token)\\b\\s*[:=]?\\s*[^\\s,;\\\"']+");
    private static final Pattern LONG_TOKEN_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9_-])[A-Za-z0-9_+/=-]{24,}(?![A-Za-z0-9_-])");

    private final SkinAnalysisRepository skinAnalysisRepository;
    private final SkinConcernRepository skinConcernRepository;
    private final SkinSimulationProvider skinSimulationProvider;
    private final Clock clock;
    private final Duration pollInterval;
    private final int maxPollAttempts;

    public SkinSimulationService(
            SkinAnalysisRepository skinAnalysisRepository,
            SkinConcernRepository skinConcernRepository,
            SkinSimulationProvider skinSimulationProvider,
            Clock clock,
            @Value("${youcam.poll-interval:10s}") Duration pollInterval,
            @Value("${youcam.max-poll-attempts:6}") int maxPollAttempts) {
        if (pollInterval.isNegative() || maxPollAttempts < 1 || maxPollAttempts > MAX_POLL_ATTEMPTS) {
            throw new IllegalArgumentException("Polling interval must be non-negative and attempts must be between 1 and 6.");
        }
        this.skinAnalysisRepository = skinAnalysisRepository;
        this.skinConcernRepository = skinConcernRepository;
        this.skinSimulationProvider = skinSimulationProvider;
        this.clock = clock;
        this.pollInterval = pollInterval;
        this.maxPollAttempts = maxPollAttempts;
    }

    public SkinSimulationResponse simulate(UUID analysisId, SkinSimulationRequest request) {
        LOGGER.info("Skin simulation request started");
        SkinAnalysis analysis = skinAnalysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException("Skin analysis"));
        validateAnalysisState(analysis);

        String requestedConcernCode = request.concernCode();
        List<SkinConcern> analysisConcerns = skinConcernRepository.findAllByAnalysisIdOrderByConcernCodeAsc(analysisId);
        boolean concernWasAnalyzed = analysisConcerns.stream()
                .anyMatch(concern -> concern.getConcernCode().equals(requestedConcernCode));
        if (!concernWasAnalyzed) {
            LOGGER.warn("Skin simulation stopped before provider call: reason=focus_not_analyzed");
            throw unavailable("SIMULATION_FOCUS_NOT_ANALYZED", "Choose a concern returned by this skin analysis.");
        }

        SkinSimulationFocus focus = SkinSimulationFocus.forConcernCode(requestedConcernCode)
                .orElseThrow(() -> unavailable(
                        "SIMULATION_NOT_SUPPORTED", "Visual simulation isn't available for this focus yet."));

        String sourceImageReference = analysis.getOriginalImageReference();
        if (skinSimulationProvider.requiresSourceImageReference()) {
            if (sourceImageReference == null || sourceImageReference.isBlank()) {
                LOGGER.warn("Skin simulation stopped before provider call: reason=source_file_reference_missing");
                throw unavailable(
                        "SIMULATION_SOURCE_UNAVAILABLE",
                        "The original image reference is unavailable. Analyze the photo again to create a visual simulation.");
            }
            // SkinCart records created_at before upload begins, so this check expires the opaque provider file ID
            // conservatively and never treats the documented 30-day retention as indefinite.
            if (!clock.instant().isBefore(analysis.getCreatedAt().plus(PROVIDER_FILE_RETENTION))) {
                LOGGER.warn("Skin simulation stopped before provider call: reason=source_file_reference_expired");
                throw unavailable(
                        "SIMULATION_SOURCE_EXPIRED",
                        "The original image has expired from the simulation provider. Analyze the photo again to create a new preview.");
            }
        }

        LOGGER.info(
                "Skin simulation task submission started: provider={}, focus={}",
                skinSimulationProvider.providerName(),
                focus.concernCode());
        SkinSimulationSubmission submission;
        try {
            submission = skinSimulationProvider.submit(sourceImageReference, focus);
        } catch (ProviderException exception) {
            logProviderException("task submission", focus, exception);
            throw exception;
        }
        if (submission == null || submission.taskId() == null || submission.taskId().isBlank()) {
            throw malformedResponse("task_id_missing");
        }
        LOGGER.info("Skin simulation task submitted: provider={}", skinSimulationProvider.providerName());

        for (int attempt = 0; attempt < maxPollAttempts; attempt++) {
            if (attempt > 0) {
                waitBeforeNextPoll();
            }
            LOGGER.info(
                    "Skin simulation polling attempt {}/{}: provider={}",
                    attempt + 1,
                    maxPollAttempts,
                    skinSimulationProvider.providerName());
            SkinSimulationSnapshot snapshot;
            try {
                snapshot = skinSimulationProvider.poll(submission.taskId());
            } catch (ProviderException exception) {
                logProviderException("task polling", focus, exception);
                throw exception;
            }
            if (snapshot == null || snapshot.status() == null) {
                throw malformedResponse("task_status_missing");
            }
            LOGGER.info(
                    "Skin simulation provider task status: provider={}, taskStatus={}",
                    skinSimulationProvider.providerName(),
                    snapshot.status());
            if (snapshot.status() == AnalysisStatus.PROCESSING) {
                continue;
            }
            if (snapshot.status() == AnalysisStatus.FAILED) {
                LOGGER.warn(
                        "Skin simulation provider task failed: provider={}, providerTaskStatus=FAILED, focus={}",
                        skinSimulationProvider.providerName(),
                        focus.concernCode());
                throw unavailable(
                        "SIMULATION_FAILED", "The visual simulation could not be completed. Please try again later.");
            }
            if (snapshot.status() == AnalysisStatus.COMPLETED) {
                if (snapshot.resultImageUrl() == null || snapshot.resultImageUrl().isBlank()) {
                    throw malformedResponse("result_url_missing");
                }
                LOGGER.info("Skin simulation completed: provider={}", skinSimulationProvider.providerName());
                return new SkinSimulationResponse(
                        analysisId,
                        focus.concernCode(),
                        focus.displayName(),
                        snapshot.resultImageUrl(),
                        "mock".equals(skinSimulationProvider.providerName()));
            }
            throw malformedResponse("unsupported_task_status");
        }

        LOGGER.warn("Skin simulation polling exhausted after {} attempts", maxPollAttempts);
        throw new ProviderTimeoutException(POLLING_TIMEOUT_MESSAGE);
    }

    private void validateAnalysisState(SkinAnalysis analysis) {
        if (analysis.getStatus() == AnalysisStatus.CREATED || analysis.getStatus() == AnalysisStatus.PROCESSING) {
            throw new SkinSimulationNotReadyException();
        }
        if (analysis.getStatus() == AnalysisStatus.FAILED) {
            throw new SkinAnalysisFailedException();
        }
    }

    private SkinSimulationUnavailableException unavailable(String code, String message) {
        return new SkinSimulationUnavailableException(code, message);
    }

    private void logProviderException(String operation, SkinSimulationFocus focus, ProviderException exception) {
        LOGGER.warn(
                "Skin simulation provider {} failed: exceptionClass={}, providerErrorCode={}, mappedHttpStatus={}, "
                        + "providerErrorMessage={}, focus={}",
                operation,
                exception.getClass().getSimpleName(),
                exception.getCode(),
                exception.getStatus().value(),
                safeLogDetail(exception.getMessage()),
                focus.concernCode());
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
        LOGGER.warn("Skin simulation response mapping failed: reason={}", reason);
        return new ProviderException(
                HttpStatus.BAD_GATEWAY,
                "PROVIDER_MALFORMED_RESPONSE",
                "The skin simulation provider returned an unexpected response.");
    }

    private void waitBeforeNextPoll() {
        try {
            Thread.sleep(pollInterval.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderTimeoutException("Skin simulation polling was interrupted.");
        }
    }
}
