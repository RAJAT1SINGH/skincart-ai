package com.skincart.provider;

import java.math.BigDecimal;
import java.util.List;

public record SkinAnalysisResult(BigDecimal overallScore, List<SkinConcernResult> concerns) {

    public SkinAnalysisResult {
        concerns = List.copyOf(concerns);
    }
}
