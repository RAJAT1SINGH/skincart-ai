package com.skincart.service;

import com.skincart.common.AnalysisStatus;
import com.skincart.dto.response.SkinAnalysisCreatedResponse;
import com.skincart.dto.response.SkinAnalysisResponse;
import com.skincart.entity.SkinAnalysis;
import com.skincart.exception.InvalidImageException;
import com.skincart.exception.ResourceNotFoundException;
import com.skincart.mapper.SkinAnalysisMapper;
import com.skincart.provider.ProviderException;
import com.skincart.provider.ProviderTimeoutException;
import com.skincart.provider.SkinAnalysisImage;
import com.skincart.provider.SkinAnalysisProvider;
import com.skincart.provider.SkinAnalysisSnapshot;
import com.skincart.provider.SkinConcernResult;
import com.skincart.repository.SkinAnalysisRepository;
import com.skincart.repository.SkinConcernRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SkinAnalysisService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkinAnalysisService.class);
    private static final String TASK_FAILED_CODE = "PROVIDER_TASK_FAILED";
    private static final String TASK_FAILED_MESSAGE = "The skin analysis task could not be completed.";
    private static final String POLLING_TIMEOUT_MESSAGE = "Skin analysis did not complete within the polling window.";
    private static final int MAX_POLL_ATTEMPTS = 6;

    private final SkinAnalysisRepository skinAnalysisRepository;
    private final SkinConcernRepository skinConcernRepository;
    private final SkinAnalysisMapper skinAnalysisMapper;
    private final ImageUploadValidator imageUploadValidator;
    private final Clock clock;
    private final SkinAnalysisProvider skinAnalysisProvider;
    private final Duration pollInterval;
    private final int maxPollAttempts;

    public SkinAnalysisService(
            SkinAnalysisRepository skinAnalysisRepository,
            SkinConcernRepository skinConcernRepository,
            SkinAnalysisMapper skinAnalysisMapper,
            ImageUploadValidator imageUploadValidator,
            Clock clock,
            SkinAnalysisProvider skinAnalysisProvider,
            @Value("${youcam.poll-interval:10s}") Duration pollInterval,
            @Value("${youcam.max-poll-attempts:6}") int maxPollAttempts) {
        if (pollInterval.isNegative() || maxPollAttempts < 1 || maxPollAttempts > MAX_POLL_ATTEMPTS) {
            throw new IllegalArgumentException(
                    "Polling interval must be non-negative and attempts must be between 1 and 6.");
        }
        this.skinAnalysisRepository = skinAnalysisRepository;
        this.skinConcernRepository = skinConcernRepository;
        this.skinAnalysisMapper = skinAnalysisMapper;
        this.imageUploadValidator = imageUploadValidator;
        this.clock = clock;
        this.skinAnalysisProvider = skinAnalysisProvider;
        this.pollInterval = pollInterval;
        this.maxPollAttempts = maxPollAttempts;
    }

    public SkinAnalysisCreatedResponse create(MultipartFile image) {
        LOGGER.info("Skin analysis request started");
        try {
            imageUploadValidator.validate(image);
            LOGGER.info("Skin analysis image validation passed");
        } catch (InvalidImageException exception) {
            LOGGER.warn("Skin analysis image validation failed with code {}", exception.getCode());
            throw exception;
        }

        SkinAnalysis analysis = new SkinAnalysis(Instant.now(clock), AnalysisStatus.CREATED);
        analysis.setProviderName(skinAnalysisProvider.providerName());
        SkinAnalysis savedAnalysis = skinAnalysisRepository.save(analysis);

        try {
            SkinAnalysisImage providerImage = new SkinAnalysisImage(
                    image.getBytes(), safeFileName(image), normalizedContentType(image.getContentType()));
            var submission = skinAnalysisProvider.submit(providerImage);
            if (submission == null || submission.taskId() == null || submission.taskId().isBlank()) {
                throw new ProviderException(
                        HttpStatus.BAD_GATEWAY,
                        "PROVIDER_MALFORMED_RESPONSE",
                        "The skin analysis provider returned an unexpected response.");
            }
            savedAnalysis.setOriginalImageReference(submission.sourceImageReference());
            savedAnalysis.markProcessing(skinAnalysisProvider.providerName(), submission.taskId());
            savedAnalysis = skinAnalysisRepository.save(savedAnalysis);

            for (int attempt = 0; attempt < maxPollAttempts; attempt++) {
                if (attempt > 0) {
                    waitBeforeNextPoll();
                }
                LOGGER.info(
                        "Skin analysis polling attempt {}/{}: provider={}",
                        attempt + 1,
                        maxPollAttempts,
                        skinAnalysisProvider.providerName());
                SkinAnalysisSnapshot snapshot = skinAnalysisProvider.poll(submission.taskId());
                if (snapshot == null || snapshot.status() == null) {
                    throw new ProviderException(
                            HttpStatus.BAD_GATEWAY,
                            "PROVIDER_MALFORMED_RESPONSE",
                            "The skin analysis provider returned an unexpected response.");
                }
                if (snapshot.status() == AnalysisStatus.PROCESSING) {
                    savedAnalysis.markProcessing(skinAnalysisProvider.providerName(), submission.taskId());
                    savedAnalysis = skinAnalysisRepository.save(savedAnalysis);
                    continue;
                }
                if (snapshot.status() == AnalysisStatus.FAILED) {
                    savedAnalysis.markFailed(TASK_FAILED_CODE, TASK_FAILED_MESSAGE);
                    savedAnalysis = skinAnalysisRepository.save(savedAnalysis);
                    LOGGER.warn(
                            "Skin analysis finished with status {} and code {}",
                            AnalysisStatus.FAILED,
                            TASK_FAILED_CODE);
                    return skinAnalysisMapper.toCreatedResponse(savedAnalysis);
                }
                if (snapshot.status() == AnalysisStatus.COMPLETED) {
                    persistConcerns(savedAnalysis, snapshot);
                    savedAnalysis.markCompleted(snapshot.result().overallScore());
                    savedAnalysis = skinAnalysisRepository.save(savedAnalysis);
                    LOGGER.info("Skin analysis finished with status {}", AnalysisStatus.COMPLETED);
                    return skinAnalysisMapper.toCreatedResponse(savedAnalysis);
                }
                throw new ProviderException(
                        HttpStatus.BAD_GATEWAY,
                        "PROVIDER_MALFORMED_RESPONSE",
                        "The skin analysis provider returned an unexpected response.");
            }

            savedAnalysis.markFailed("PROVIDER_TIMEOUT", POLLING_TIMEOUT_MESSAGE);
            skinAnalysisRepository.save(savedAnalysis);
            throw new ProviderTimeoutException(POLLING_TIMEOUT_MESSAGE);
        } catch (ProviderException exception) {
            if (savedAnalysis.getStatus() != AnalysisStatus.FAILED) {
                savedAnalysis.markFailed(exception.getCode(), exception.getMessage());
                skinAnalysisRepository.save(savedAnalysis);
            }
            LOGGER.warn("Skin analysis provider operation failed with code {}", exception.getCode());
            throw exception;
        } catch (IOException exception) {
            savedAnalysis.markFailed("INVALID_IMAGE", "The uploaded image could not be read.");
            skinAnalysisRepository.save(savedAnalysis);
            LOGGER.warn("Skin analysis image could not be read after validation");
            throw new com.skincart.exception.InvalidImageException(
                    HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "The uploaded image could not be read.");
        }
    }

    public SkinAnalysisResponse get(UUID analysisId) {
        SkinAnalysis analysis = skinAnalysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException("Skin analysis"));
        return skinAnalysisMapper.toResponse(
                analysis, skinConcernRepository.findAllByAnalysisIdWithMasks(analysisId));
    }

    private void persistConcerns(SkinAnalysis analysis, SkinAnalysisSnapshot snapshot) {
        if (snapshot.result() == null || snapshot.result().concerns() == null) {
            throw new ProviderException(
                    HttpStatus.BAD_GATEWAY,
                    "PROVIDER_MALFORMED_RESPONSE",
                    "The skin analysis provider returned an unexpected response.");
        }
        List<com.skincart.entity.SkinConcern> concerns = snapshot.result().concerns().stream()
                .map(result -> toEntity(analysis, result))
                .toList();
        skinConcernRepository.saveAll(concerns);
    }

    private com.skincart.entity.SkinConcern toEntity(SkinAnalysis analysis, SkinConcernResult result) {
        return new com.skincart.entity.SkinConcern(
                analysis,
                result.name(),
                displayName(result.name()),
                result.uiScore(),
                result.rawScore(),
                result.maskUrls());
    }

    private String displayName(String concernName) {
        String[] words = concernName.replace('_', ' ').split("\\s+");
        StringBuilder displayName = new StringBuilder();
        for (String word : words) {
            if (!word.isBlank()) {
                if (!displayName.isEmpty()) {
                    displayName.append(' ');
                }
                displayName.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
                displayName.append(word.substring(1));
            }
        }
        return displayName.toString();
    }

    private String safeFileName(MultipartFile image) {
        String name = image.getOriginalFilename();
        if (name == null || name.isBlank()) {
            String extension = normalizedContentType(image.getContentType()).equals("image/png") ? ".png" : ".jpg";
            return "skin-analysis-upload" + extension;
        }
        String baseName = name.replace('\\', '/');
        baseName = baseName.substring(baseName.lastIndexOf('/') + 1);
        return baseName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String normalizedContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return normalized.equals("image/jpg") ? "image/jpeg" : normalized;
    }

    private void waitBeforeNextPoll() {
        try {
            Thread.sleep(pollInterval.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderTimeoutException("Skin analysis polling was interrupted.");
        }
    }
}
