package com.skincart.dto.response;

import com.skincart.common.AnalysisStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SkinAnalysisResponse(
        UUID analysisId,
        Instant createdAt,
        AnalysisStatus status,
        BigDecimal overallScore,
        List<SkinConcernResponse> concerns,
        String errorCode,
        String errorMessage) {
}
