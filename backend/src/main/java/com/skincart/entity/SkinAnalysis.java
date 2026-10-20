package com.skincart.entity;

import com.skincart.common.AnalysisStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "skin_analysis")
public class SkinAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnalysisStatus status;

    @Column(name = "original_image_reference", length = 2048)
    private String originalImageReference;

    @Column(name = "overall_score", precision = 5, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "provider_task_id", length = 255)
    private String providerTaskId;

    @Column(name = "provider_name", length = 100)
    private String providerName;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 2048)
    private String errorMessage;

    protected SkinAnalysis() {
        // Required by JPA.
    }

    public SkinAnalysis(Instant createdAt, AnalysisStatus status) {
        this.createdAt = createdAt;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public String getOriginalImageReference() {
        return originalImageReference;
    }

    public void setOriginalImageReference(String originalImageReference) {
        this.originalImageReference = originalImageReference;
    }

    public BigDecimal getOverallScore() {
        return overallScore;
    }

    public String getProviderTaskId() {
        return providerTaskId;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void markProcessing(String providerName, String providerTaskId) {
        this.providerName = providerName;
        this.providerTaskId = providerTaskId;
        this.status = AnalysisStatus.PROCESSING;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public void markCompleted(BigDecimal overallScore) {
        this.overallScore = overallScore;
        this.status = AnalysisStatus.COMPLETED;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markFailed(String errorCode, String errorMessage) {
        this.status = AnalysisStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
