package com.skincart.dto.response;

import com.skincart.common.AnalysisStatus;
import java.util.UUID;

public record SkinAnalysisCreatedResponse(UUID analysisId, AnalysisStatus status) {
}
