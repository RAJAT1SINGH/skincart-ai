package com.skincart.recommendation;

import com.skincart.common.ProductCategory;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductCandidate(
        UUID productId,
        String name,
        String brand,
        ProductCategory category,
        String description,
        List<CandidateIngredient> ingredients,
        Map<String, Integer> targetConcernWeights) {

    public ProductCandidate {
        ingredients = List.copyOf(ingredients);
        targetConcernWeights = Map.copyOf(targetConcernWeights);
    }
}
