package com.skincart.recommendation;

import com.skincart.common.ProductCategory;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RankedRecommendation(
        UUID productId,
        String productName,
        String brand,
        ProductCategory category,
        String description,
        BigDecimal score,
        List<String> matchedConcerns,
        List<String> matchedIngredients,
        String reason) {

    public RankedRecommendation {
        matchedConcerns = List.copyOf(matchedConcerns);
        matchedIngredients = List.copyOf(matchedIngredients);
    }
}
