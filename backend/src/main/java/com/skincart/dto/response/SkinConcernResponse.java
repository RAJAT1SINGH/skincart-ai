package com.skincart.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record SkinConcernResponse(
        String code,
        String displayName,
        String severity,
        Integer uiScore,
        BigDecimal rawScore,
        List<String> maskUrls) {

    public SkinConcernResponse {
        maskUrls = maskUrls == null ? List.of() : List.copyOf(maskUrls);
    }

    public SkinConcernResponse(String code, String displayName, String severity) {
        this(code, displayName, severity, null, null, List.of());
    }
}
