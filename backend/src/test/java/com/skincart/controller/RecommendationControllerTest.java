package com.skincart.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skincart.dto.response.ProductRecommendationResponse;
import com.skincart.dto.response.RecommendationResponse;
import com.skincart.common.ProductCategory;
import com.skincart.exception.GlobalExceptionHandler;
import com.skincart.exception.RecommendationNotReadyException;
import com.skincart.exception.ResourceNotFoundException;
import com.skincart.exception.SkinAnalysisFailedException;
import com.skincart.service.RecommendationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RecommendationController.class)
@Import(GlobalExceptionHandler.class)
class RecommendationControllerTest {

    private static final UUID ANALYSIS_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final String PATH = "/api/v1/skin-analysis/" + ANALYSIS_ID + "/recommendations";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationService recommendationService;

    @Test
    void returnsRecommendationsForCompletedAnalysis() throws Exception {
        when(recommendationService.getRecommendations(ANALYSIS_ID)).thenReturn(new RecommendationResponse(
                ANALYSIS_ID,
                List.of(new ProductRecommendationResponse(
                        UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
                        "Demo Balance Serum",
                        "Valegrove",
                        ProductCategory.SERUM,
                        "Synthetic demo product.",
                        new BigDecimal("87.5"),
                        List.of("ACNE", "PORE"),
                        List.of("Niacinamide", "Salicylic Acid"),
                        "Recommended because this product matches analyzed concerns."))));

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(ANALYSIS_ID.toString()))
                .andExpect(jsonPath("$.recommendations.length()").value(1))
                .andExpect(jsonPath("$.recommendations[0].category").value("SERUM"))
                .andExpect(jsonPath("$.recommendations[0].matchedConcerns[0]").value("ACNE"))
                .andExpect(jsonPath("$.recommendations[0].matchedIngredients[0]").value("Niacinamide"))
                .andExpect(jsonPath("$.recommendations[0].reason").exists());
    }

    @Test
    void returnsNotFoundForUnknownAnalysis() throws Exception {
        when(recommendationService.getRecommendations(ANALYSIS_ID))
                .thenThrow(new ResourceNotFoundException("Skin analysis"));

        mockMvc.perform(get(PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void reportsRecommendationsUnavailableForIncompleteAnalysis() throws Exception {
        when(recommendationService.getRecommendations(ANALYSIS_ID))
                .thenThrow(new RecommendationNotReadyException());

        mockMvc.perform(get(PATH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECOMMENDATIONS_NOT_AVAILABLE"));
    }

    @Test
    void reportsAnalysisFailureWithoutGeneratingRecommendations() throws Exception {
        when(recommendationService.getRecommendations(ANALYSIS_ID))
                .thenThrow(new SkinAnalysisFailedException());

        mockMvc.perform(get(PATH))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SKIN_ANALYSIS_FAILED"));
    }
}
