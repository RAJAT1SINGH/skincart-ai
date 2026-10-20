package com.skincart.dto.response;

import java.util.UUID;

public record SkinSimulationResponse(
        UUID analysisId,
        String concernCode,
        String focusLabel,
        String resultImageUrl,
        boolean mock) {
}
