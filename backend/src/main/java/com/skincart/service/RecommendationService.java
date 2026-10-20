package com.skincart.service;

import com.skincart.common.AnalysisStatus;
import com.skincart.dto.response.RecommendationResponse;
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
import com.skincart.recommendation.EvaluatedConcern;
import com.skincart.recommendation.ProductCandidate;
import com.skincart.recommendation.ProductCandidateAssembler;
import com.skincart.recommendation.ProductRecommendationRanker;
import com.skincart.recommendation.RankedRecommendation;
import com.skincart.repository.ProductConcernRepository;
import com.skincart.repository.ProductIngredientRepository;
import com.skincart.repository.SkinAnalysisRepository;
import com.skincart.repository.SkinConcernRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationService {

    private final SkinAnalysisRepository skinAnalysisRepository;
    private final SkinConcernRepository skinConcernRepository;
    private final ProductConcernRepository productConcernRepository;
    private final ProductIngredientRepository productIngredientRepository;
    private final ConcernEvaluator concernEvaluator;
    private final ConcernIngredientMapper concernIngredientMapper;
    private final ProductCandidateAssembler productCandidateAssembler;
    private final ProductRecommendationRanker productRecommendationRanker;
    private final RecommendationMapper recommendationMapper;

    public RecommendationService(
            SkinAnalysisRepository skinAnalysisRepository,
            SkinConcernRepository skinConcernRepository,
            ProductConcernRepository productConcernRepository,
            ProductIngredientRepository productIngredientRepository,
            ConcernEvaluator concernEvaluator,
            ConcernIngredientMapper concernIngredientMapper,
            ProductCandidateAssembler productCandidateAssembler,
            ProductRecommendationRanker productRecommendationRanker,
            RecommendationMapper recommendationMapper) {
        this.skinAnalysisRepository = skinAnalysisRepository;
        this.skinConcernRepository = skinConcernRepository;
        this.productConcernRepository = productConcernRepository;
        this.productIngredientRepository = productIngredientRepository;
        this.concernEvaluator = concernEvaluator;
        this.concernIngredientMapper = concernIngredientMapper;
        this.productCandidateAssembler = productCandidateAssembler;
        this.productRecommendationRanker = productRecommendationRanker;
        this.recommendationMapper = recommendationMapper;
    }

    @Transactional(readOnly = true)
    public RecommendationResponse getRecommendations(UUID analysisId) {
        List<RankedRecommendation> ranked = getRankedRecommendations(analysisId);
        return recommendationMapper.toResponse(
                analysisId, ranked.stream().limit(ProductRecommendationRanker.MAX_RECOMMENDATIONS).toList());
    }

    @Transactional(readOnly = true)
    public List<RankedRecommendation> getRankedRecommendations(UUID analysisId) {
        SkinAnalysis analysis = skinAnalysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException("Skin analysis"));
        if (analysis.getStatus() == AnalysisStatus.CREATED || analysis.getStatus() == AnalysisStatus.PROCESSING) {
            throw new RecommendationNotReadyException();
        }
        if (analysis.getStatus() == AnalysisStatus.FAILED) {
            throw new SkinAnalysisFailedException();
        }

        List<SkinConcern> persistedConcerns = skinConcernRepository.findAllByAnalysisIdOrderByConcernCodeAsc(analysisId);
        List<EvaluatedConcern> concerns = concernEvaluator.evaluate(persistedConcerns);
        if (concerns.isEmpty()) {
            return List.of();
        }

        Set<String> concernCodes = new TreeSet<>();
        Set<String> ingredientNames = new TreeSet<>();
        for (EvaluatedConcern concern : concerns) {
            concernCodes.add(concern.code());
            concernIngredientMapper.ingredientsFor(concern.code()).stream()
                    .map(ingredient -> ingredient.toLowerCase(Locale.ROOT))
                    .forEach(ingredientNames::add);
        }

        List<ProductConcern> concernRows = productConcernRepository.findByConcernCodesWithProduct(
                new ArrayList<>(concernCodes));
        List<ProductIngredient> ingredientRows = ingredientNames.isEmpty()
                ? List.of()
                : productIngredientRepository.findByIngredientNamesWithProductAndIngredient(
                        new ArrayList<>(ingredientNames));
        List<ProductCandidate> candidates = productCandidateAssembler.assemble(concernRows, ingredientRows);
        return productRecommendationRanker.rankAll(concerns, candidates);
    }
}
