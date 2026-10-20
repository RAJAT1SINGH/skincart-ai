package com.skincart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skincart.common.ProductCategory;
import com.skincart.exception.RecommendationNotReadyException;
import com.skincart.exception.ResourceNotFoundException;
import com.skincart.exception.SkinAnalysisFailedException;
import com.skincart.recommendation.RankedRecommendation;
import com.skincart.routine.RoutinePlanner;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoutineServiceTest {

    private static final UUID ANALYSIS_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    @Mock
    private RecommendationService recommendationService;

    private RoutineService routineService;

    @BeforeEach
    void setUp() {
        routineService = new RoutineService(recommendationService, new RoutinePlanner());
    }

    @Test
    void completedAnalysisUsesRankedRecommendationCandidatesToGenerateRoutine() {
        when(recommendationService.getRankedRecommendations(ANALYSIS_ID)).thenReturn(List.of(
                product(ProductCategory.CLEANSER, "Demo Cleanser"),
                product(ProductCategory.SERUM, "Demo Serum"),
                product(ProductCategory.MOISTURIZER, "Demo Moisturizer"),
                product(ProductCategory.SUNSCREEN, "Demo Sunscreen")));

        var response = routineService.getRoutine(ANALYSIS_ID);

        assertThat(response.morning()).hasSize(4);
        assertThat(response.evening()).hasSize(3);
        verify(recommendationService).getRankedRecommendations(ANALYSIS_ID);
    }

    @Test
    void propagatesNotFoundForMissingAnalysis() {
        when(recommendationService.getRankedRecommendations(ANALYSIS_ID))
                .thenThrow(new ResourceNotFoundException("Skin analysis"));

        assertThatThrownBy(() -> routineService.getRoutine(ANALYSIS_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void propagatesConflictForCreatedAndProcessingAnalyses() {
        when(recommendationService.getRankedRecommendations(ANALYSIS_ID))
                .thenThrow(new RecommendationNotReadyException());

        assertThatThrownBy(() -> routineService.getRoutine(ANALYSIS_ID))
                .isInstanceOf(RecommendationNotReadyException.class);
        assertThatThrownBy(() -> routineService.getRoutine(ANALYSIS_ID))
                .isInstanceOf(RecommendationNotReadyException.class);
    }

    @Test
    void propagatesUnprocessableEntityForFailedAnalysis() {
        when(recommendationService.getRankedRecommendations(ANALYSIS_ID))
                .thenThrow(new SkinAnalysisFailedException());

        assertThatThrownBy(() -> routineService.getRoutine(ANALYSIS_ID))
                .isInstanceOf(SkinAnalysisFailedException.class);
    }

    private RankedRecommendation product(ProductCategory category, String name) {
        return new RankedRecommendation(
                UUID.nameUUIDFromBytes(name.getBytes()),
                name,
                "Demo Brand",
                category,
                "Synthetic demo product.",
                new BigDecimal("90.0"),
                List.of("ACNE"),
                List.of("Niacinamide"),
                "Recommended based on analyzed concerns.");
    }
}
