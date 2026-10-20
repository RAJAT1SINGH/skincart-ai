package com.skincart.mapper;

import com.skincart.dto.response.ProductRecommendationResponse;
import com.skincart.dto.response.RecommendationResponse;
import com.skincart.recommendation.RankedRecommendation;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RecommendationMapper {

    public RecommendationResponse toResponse(UUID analysisId, List<RankedRecommendation> recommendations) {
        List<ProductRecommendationResponse> productRecommendations = recommendations.stream()
                .map(recommendation -> new ProductRecommendationResponse(
                        recommendation.productId(),
                        recommendation.productName(),
                        recommendation.brand(),
                        recommendation.category(),
                        recommendation.description(),
                        recommendation.score(),
                        recommendation.matchedConcerns(),
                        recommendation.matchedIngredients(),
                        recommendation.reason()))
                .toList();
        return new RecommendationResponse(analysisId, productRecommendations);
    }
}
