package com.skincart.routine;

import com.skincart.common.ProductCategory;
import com.skincart.dto.response.RoutineResponse;
import com.skincart.dto.response.RoutineStepResponse;
import com.skincart.recommendation.RankedRecommendation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RoutinePlanner {

    private static final List<RoutineStep> MORNING_STEPS = List.of(
            RoutineStep.CLEANSER,
            RoutineStep.TREATMENT,
            RoutineStep.MOISTURIZER,
            RoutineStep.SUNSCREEN);
    private static final List<RoutineStep> EVENING_STEPS = List.of(
            RoutineStep.CLEANSER,
            RoutineStep.TREATMENT,
            RoutineStep.MOISTURIZER);

    public RoutineResponse plan(UUID analysisId, List<RankedRecommendation> rankedProducts) {
        List<RankedRecommendation> products = List.copyOf(rankedProducts);
        Map<ProductCategory, List<RankedRecommendation>> productsByCategory = groupByCategory(products);
        Map<UUID, Integer> rankOrder = new HashMap<>();
        for (int index = 0; index < products.size(); index++) {
            rankOrder.putIfAbsent(products.get(index).productId(), index);
        }
        return new RoutineResponse(
                analysisId,
                buildRoutine(MORNING_STEPS, productsByCategory, rankOrder),
                buildRoutine(EVENING_STEPS, productsByCategory, rankOrder));
    }

    private List<RoutineStepResponse> buildRoutine(
            List<RoutineStep> steps,
            Map<ProductCategory, List<RankedRecommendation>> productsByCategory,
            Map<UUID, Integer> rankOrder) {
        List<RoutineStepResponse> routine = new ArrayList<>();
        Set<UUID> usedProductIds = new HashSet<>();
        for (RoutineStep step : steps) {
            bestProductForStep(step, productsByCategory, rankOrder, usedProductIds)
                    .ifPresent(product -> {
                        usedProductIds.add(product.productId());
                        routine.add(new RoutineStepResponse(
                                step,
                                product.productId(),
                                product.productName(),
                                product.category(),
                                explanation(product)));
                    });
        }
        return List.copyOf(routine);
    }

    private Map<ProductCategory, List<RankedRecommendation>> groupByCategory(
            List<RankedRecommendation> rankedProducts) {
        Map<ProductCategory, List<RankedRecommendation>> productsByCategory = new EnumMap<>(ProductCategory.class);
        rankedProducts.forEach(product -> productsByCategory
                .computeIfAbsent(product.category(), ignored -> new ArrayList<>())
                .add(product));
        productsByCategory.replaceAll((category, products) -> List.copyOf(products));
        return Map.copyOf(productsByCategory);
    }

    private Optional<RankedRecommendation> bestProductForStep(
            RoutineStep step,
            Map<ProductCategory, List<RankedRecommendation>> productsByCategory,
            Map<UUID, Integer> rankOrder,
            Set<UUID> usedProductIds) {
        List<ProductCategory> categories = switch (step) {
            case CLEANSER -> List.of(ProductCategory.CLEANSER);
            case TREATMENT -> List.of(ProductCategory.SERUM, ProductCategory.TREATMENT, ProductCategory.ACNE_TREATMENT);
            case MOISTURIZER -> List.of(ProductCategory.MOISTURIZER);
            case SUNSCREEN -> List.of(ProductCategory.SUNSCREEN);
        };
        return categories.stream()
                .map(category -> productsByCategory.getOrDefault(category, List.of()))
                .flatMap(List::stream)
                .filter(product -> !usedProductIds.contains(product.productId()))
                .min(Comparator.comparingInt(product -> rankOrder.get(product.productId())));
    }

    private String explanation(RankedRecommendation product) {
        List<String> concerns = product.matchedConcerns().stream()
                .map(concern -> concern.toLowerCase(Locale.ROOT))
                .toList();
        if (concerns.isEmpty()) {
            return "Selected as part of your skincare routine based on your analyzed concerns.";
        }
        String concernNames = joinConcerns(concerns);
        String noun = concerns.size() == 1 ? "concern" : "concerns";
        return "Selected because it matches the " + concernNames + " " + noun
                + " identified in your skin analysis.";
    }

    private String joinConcerns(List<String> concerns) {
        if (concerns.size() == 1) {
            return concerns.getFirst();
        }
        if (concerns.size() == 2) {
            return concerns.getFirst() + " and " + concerns.getLast();
        }
        return String.join(", ", concerns.subList(0, concerns.size() - 1))
                + ", and " + concerns.getLast();
    }
}
