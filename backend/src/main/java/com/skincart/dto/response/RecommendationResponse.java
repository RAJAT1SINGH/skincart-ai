package com.skincart.dto.response;

import java.util.List;
import java.util.UUID;

public record RecommendationResponse(UUID analysisId, List<ProductRecommendationResponse> recommendations) {

    public RecommendationResponse {
        recommendations = List.copyOf(recommendations);
    }
}
