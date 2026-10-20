package com.skincart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.skincart.common.AnalysisStatus;
import com.skincart.common.ProductCategory;
import com.skincart.entity.Ingredient;
import com.skincart.entity.Product;
import com.skincart.entity.ProductConcern;
import com.skincart.entity.ProductIngredient;
import com.skincart.entity.SkinAnalysis;
import com.skincart.entity.SkinConcern;
import com.skincart.exception.RecommendationNotReadyException;
import com.skincart.exception.ResourceNotFoundException;
import com.skincart.exception.SkinAnalysisFailedException;
import com.skincart.mapper.RecommendationMapper;
import com.skincart.recommendation.ConcernEvaluator;
import com.skincart.recommendation.ConcernIngredientMapper;
import com.skincart.recommendation.ProductCandidateAssembler;
import com.skincart.recommendation.ProductRecommendationRanker;
import com.skincart.repository.ProductConcernRepository;
import com.skincart.repository.ProductIngredientRepository;
import com.skincart.repository.SkinAnalysisRepository;
import com.skincart.repository.SkinConcernRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private static final UUID ANALYSIS_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID PRODUCT_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

    @Mock
    private SkinAnalysisRepository skinAnalysisRepository;

    @Mock
    private SkinConcernRepository skinConcernRepository;

    @Mock
    private ProductConcernRepository productConcernRepository;

    @Mock
    private ProductIngredientRepository productIngredientRepository;

    private RecommendationService service;

    @BeforeEach
    void setUp() {
        ConcernIngredientMapper ingredientMapper = new ConcernIngredientMapper();
        service = new RecommendationService(
                skinAnalysisRepository,
                skinConcernRepository,
                productConcernRepository,
                productIngredientRepository,
                new ConcernEvaluator(),
                ingredientMapper,
                new ProductCandidateAssembler(),
                new ProductRecommendationRanker(ingredientMapper),
                new RecommendationMapper());
    }

    @Test
    void completedAnalysisReturnsDeduplicatedRecommendationsWithExplainableMatches() {
        SkinAnalysis analysis = analysis(AnalysisStatus.COMPLETED);
        when(skinAnalysisRepository.findById(ANALYSIS_ID)).thenReturn(Optional.of(analysis));
        when(skinConcernRepository.findAllByAnalysisIdOrderByConcernCodeAsc(ANALYSIS_ID))
                .thenReturn(List.of(concern(analysis, "acne", "Acne", 30), concern(analysis, "pore", "Pore", 60)));

        Product product = product("Demo Clear Match Serum", ProductCategory.SERUM);
        ProductConcern acneTarget = new ProductConcern(product, "acne", 5);
        ProductConcern poreTarget = new ProductConcern(product, "pore", 4);
        when(productConcernRepository.findByConcernCodesWithProduct(List.of("acne", "pore")))
                .thenReturn(List.of(acneTarget, poreTarget));
        when(productIngredientRepository.findByIngredientNamesWithProductAndIngredient(
                        List.of("azelaic acid", "niacinamide", "salicylic acid")))
                .thenReturn(List.of(
                        new ProductIngredient(product, new Ingredient("Niacinamide", "demo"), true),
                        new ProductIngredient(product, new Ingredient("Salicylic Acid", "demo"), true)));

        var response = service.getRecommendations(ANALYSIS_ID);

        assertThat(response.analysisId()).isEqualTo(ANALYSIS_ID);
        assertThat(response.recommendations()).hasSize(1);
        assertThat(response.recommendations().getFirst().productName()).isEqualTo("Demo Clear Match Serum");
        assertThat(response.recommendations().getFirst().matchedConcerns()).containsExactly("ACNE", "PORE");
        assertThat(response.recommendations().getFirst().matchedIngredients())
                .containsExactly("Niacinamide", "Salicylic Acid");
        assertThat(response.recommendations().getFirst().reason()).contains("SkinCart maps");
        assertThat(service.getRankedRecommendations(ANALYSIS_ID)).hasSize(1);
    }

    @Test
    void rejectsCreatedAndProcessingAnalyses() {
        for (AnalysisStatus status : List.of(AnalysisStatus.CREATED, AnalysisStatus.PROCESSING)) {
            when(skinAnalysisRepository.findById(ANALYSIS_ID)).thenReturn(Optional.of(analysis(status)));

            assertThatThrownBy(() -> service.getRecommendations(ANALYSIS_ID))
                    .isInstanceOf(RecommendationNotReadyException.class);
            assertThatThrownBy(() -> service.getRankedRecommendations(ANALYSIS_ID))
                    .isInstanceOf(RecommendationNotReadyException.class);
        }
    }

    @Test
    void rejectsFailedAnalysis() {
        when(skinAnalysisRepository.findById(ANALYSIS_ID))
                .thenReturn(Optional.of(analysis(AnalysisStatus.FAILED)));

        assertThatThrownBy(() -> service.getRecommendations(ANALYSIS_ID))
                .isInstanceOf(SkinAnalysisFailedException.class);
        assertThatThrownBy(() -> service.getRankedRecommendations(ANALYSIS_ID))
                .isInstanceOf(SkinAnalysisFailedException.class);
    }

    @Test
    void returnsNotFoundForUnknownAnalysis() {
        when(skinAnalysisRepository.findById(ANALYSIS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRecommendations(ANALYSIS_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getRankedRecommendations(ANALYSIS_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void completedAnalysisWithNoMatchingProductsReturnsAnEmptyList() {
        SkinAnalysis analysis = analysis(AnalysisStatus.COMPLETED);
        when(skinAnalysisRepository.findById(ANALYSIS_ID)).thenReturn(Optional.of(analysis));
        when(skinConcernRepository.findAllByAnalysisIdOrderByConcernCodeAsc(ANALYSIS_ID))
                .thenReturn(List.of(concern(analysis, "acne", "Acne", 65)));
        when(productConcernRepository.findByConcernCodesWithProduct(List.of("acne"))).thenReturn(List.of());
        when(productIngredientRepository.findByIngredientNamesWithProductAndIngredient(
                        List.of("azelaic acid", "niacinamide", "salicylic acid")))
                .thenReturn(List.of());

        var response = service.getRecommendations(ANALYSIS_ID);

        assertThat(response.recommendations()).isEmpty();
    }

    private SkinAnalysis analysis(AnalysisStatus status) {
        SkinAnalysis analysis = new SkinAnalysis(Instant.parse("2026-01-01T00:00:00Z"), status);
        ReflectionTestUtils.setField(analysis, "id", ANALYSIS_ID);
        return analysis;
    }

    private SkinConcern concern(SkinAnalysis analysis, String code, String name, int uiScore) {
        return new SkinConcern(analysis, code, name, uiScore, new BigDecimal("55.0"), List.of());
    }

    private Product product(String name, ProductCategory category) {
        Product product = org.mockito.Mockito.mock(Product.class);
        when(product.getId()).thenReturn(PRODUCT_ID);
        when(product.getName()).thenReturn(name);
        when(product.getBrand()).thenReturn("Synthetic Brand");
        when(product.getCategory()).thenReturn(category);
        when(product.getDescription()).thenReturn("Synthetic demo catalog item.");
        return product;
    }
}
