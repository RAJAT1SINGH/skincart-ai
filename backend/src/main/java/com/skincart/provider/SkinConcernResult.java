package com.skincart.provider;

import java.math.BigDecimal;
import java.util.List;

public record SkinConcernResult(
        String name, Integer uiScore, BigDecimal rawScore, List<String> maskUrls) {

    public SkinConcernResult {
        maskUrls = maskUrls == null ? List.of() : List.copyOf(maskUrls);
    }
}
