package com.skincart.controller;

import com.skincart.dto.response.RecommendationResponse;
import com.skincart.service.RecommendationService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/api/v1/skin-analysis/{analysisId}/recommendations")
    public RecommendationResponse recommendations(@PathVariable UUID analysisId) {
        return recommendationService.getRecommendations(analysisId);
    }
}
