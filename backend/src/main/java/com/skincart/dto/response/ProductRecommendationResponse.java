package com.skincart.dto.response;

import com.skincart.common.ProductCategory;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductRecommendationResponse(
        UUID productId,
        String productName,
        String brand,
        ProductCategory category,
        String description,
        BigDecimal score,
        List<String> matchedConcerns,
        List<String> matchedIngredients,
        String reason) {

    public ProductRecommendationResponse {
        matchedConcerns = List.copyOf(matchedConcerns);
        matchedIngredients = List.copyOf(matchedIngredients);
    }
}
