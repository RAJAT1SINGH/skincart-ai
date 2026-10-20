package com.skincart.recommendation;

import com.skincart.common.ProductCategory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ProductRecommendationRanker {

    private static final double CONCERN_MATCH_WEIGHT = 40.0;
    private static final double INGREDIENT_MATCH_WEIGHT = 35.0;
    private static final double CATALOG_RELEVANCE_WEIGHT = 15.0;
    private static final double CATEGORY_RELEVANCE_WEIGHT = 10.0;
    private static final double MAX_CATALOG_RELEVANCE = 5.0;
    public static final int MAX_RECOMMENDATIONS = 5;

    private static final Map<String, Set<ProductCategory>> CATEGORIES_BY_CONCERN = Map.of(
            "acne", Set.of(ProductCategory.CLEANSER, ProductCategory.SERUM, ProductCategory.ACNE_TREATMENT),
            "pore", Set.of(ProductCategory.CLEANSER, ProductCategory.SERUM, ProductCategory.ACNE_TREATMENT),
            "texture", Set.of(
                    ProductCategory.SERUM, ProductCategory.TREATMENT, ProductCategory.MOISTURIZER),
            "wrinkle", Set.of(
                    ProductCategory.SERUM,
                    ProductCategory.TREATMENT,
                    ProductCategory.MOISTURIZER,
                    ProductCategory.SUNSCREEN));

    private final ConcernIngredientMapper concernIngredientMapper;

    public ProductRecommendationRanker(ConcernIngredientMapper concernIngredientMapper) {
        this.concernIngredientMapper = concernIngredientMapper;
    }

    public List<RankedRecommendation> rank(
            List<EvaluatedConcern> concerns, List<ProductCandidate> candidates) {
        return rankAll(concerns, candidates).stream().limit(MAX_RECOMMENDATIONS).toList();
    }

    public List<RankedRecommendation> rankAll(
            List<EvaluatedConcern> concerns, List<ProductCandidate> candidates) {
        if (concerns.isEmpty() || candidates.isEmpty()) {
            return List.of();
        }

        List<EvaluatedConcern> rankedConcerns = concerns.stream()
                .sorted(Comparator.comparingDouble(EvaluatedConcern::priorityWeight)
                        .reversed()
                        .thenComparing(EvaluatedConcern::code))
                .toList();

        return deduplicate(candidates).values().stream()
                .map(candidate -> score(candidate, rankedConcerns))
                .filter(recommendation -> !recommendation.matchedConcerns().isEmpty())
                .sorted(Comparator.comparing(RankedRecommendation::score)
                        .reversed()
                        .thenComparing(RankedRecommendation::productName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(RankedRecommendation::productId))
                .toList();
    }

    private Map<UUID, ProductCandidate> deduplicate(List<ProductCandidate> candidates) {
        Map<UUID, ProductCandidate> unique = new LinkedHashMap<>();
        for (ProductCandidate candidate : candidates) {
            unique.merge(candidate.productId(), candidate, this::merge);
        }
        return unique;
    }

    private ProductCandidate merge(ProductCandidate first, ProductCandidate second) {
        Map<String, CandidateIngredient> ingredients = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        first.ingredients().forEach(ingredient -> ingredients.put(ingredient.name(), ingredient));
        second.ingredients().forEach(ingredient -> ingredients.merge(
                ingredient.name(),
                ingredient,
                (left, right) -> new CandidateIngredient(left.name(), left.active() || right.active())));

        Map<String, Integer> targetWeights = new LinkedHashMap<>(first.targetConcernWeights());
        second.targetConcernWeights().forEach((concern, weight) -> targetWeights.merge(concern, weight, Math::max));
        return new ProductCandidate(
                first.productId(),
                first.name(),
                first.brand(),
                first.category(),
                first.description(),
                new ArrayList<>(ingredients.values()),
                targetWeights);
    }

    private RankedRecommendation score(ProductCandidate candidate, List<EvaluatedConcern> concerns) {
        double totalPriority = concerns.stream().mapToDouble(EvaluatedConcern::priorityWeight).sum();
        Map<String, CandidateIngredient> ingredientsByName = candidate.ingredients().stream()
                .collect(Collectors.toMap(
                        ingredient -> normalize(ingredient.name()),
                        ingredient -> ingredient,
                        (first, ignored) -> first));

        double targetedPriority = 0.0;
        double ingredientCoverage = 0.0;
        double catalogRelevanceNumerator = 0.0;
        double catalogRelevanceDenominator = 0.0;
        boolean categoryMatches = false;
        List<EvaluatedConcern> matchedConcerns = new ArrayList<>();
        Set<String> matchedIngredients = new LinkedHashSet<>();

        for (EvaluatedConcern concern : concerns) {
            Integer catalogWeight = candidate.targetConcernWeights().get(concern.code());
            if (catalogWeight != null) {
                targetedPriority += concern.priorityWeight();
                catalogRelevanceNumerator += concern.priorityWeight() * catalogWeight;
                catalogRelevanceDenominator += concern.priorityWeight();
            }

            List<String> mappedIngredients = concernIngredientMapper.ingredientsFor(concern.code());
            long matchingCount = mappedIngredients.stream()
                    .filter(name -> ingredientsByName.containsKey(normalize(name)))
                    .peek(matchedIngredients::add)
                    .count();
            if (!mappedIngredients.isEmpty()) {
                ingredientCoverage += concern.priorityWeight() * (matchingCount / (double) mappedIngredients.size());
            }

            boolean ingredientMatches = matchingCount > 0;
            boolean concernMatches = catalogWeight != null || ingredientMatches;
            if (concernMatches) {
                matchedConcerns.add(concern);
                categoryMatches |= CATEGORIES_BY_CONCERN
                        .getOrDefault(concern.code(), Set.of())
                        .contains(candidate.category());
            }
        }

        double concernMatchPoints = CONCERN_MATCH_WEIGHT * (targetedPriority / totalPriority);
        double ingredientMatchPoints = INGREDIENT_MATCH_WEIGHT * (ingredientCoverage / totalPriority);
        double catalogRelevancePoints = catalogRelevanceDenominator == 0.0
                ? 0.0
                : CATALOG_RELEVANCE_WEIGHT
                        * (catalogRelevanceNumerator / catalogRelevanceDenominator / MAX_CATALOG_RELEVANCE);
        double categoryRelevancePoints = categoryMatches ? CATEGORY_RELEVANCE_WEIGHT : 0.0;
        BigDecimal score = BigDecimal.valueOf(
                        concernMatchPoints + ingredientMatchPoints + catalogRelevancePoints + categoryRelevancePoints)
                .setScale(1, RoundingMode.HALF_UP);

        List<String> concernCodes = matchedConcerns.stream()
                .map(concern -> concern.code().toUpperCase(Locale.ROOT))
                .toList();
        List<String> ingredientNames = matchedIngredients.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return new RankedRecommendation(
                candidate.productId(),
                candidate.name(),
                candidate.brand(),
                candidate.category(),
                candidate.description(),
                score,
                concernCodes,
                ingredientNames,
                reason(matchedConcerns, ingredientNames));
    }

    private String reason(List<EvaluatedConcern> concerns, List<String> ingredients) {
        String concernNames = joinWords(concerns.stream()
                .map(concern -> concern.displayName().toLowerCase(Locale.ROOT))
                .toList());
        if (ingredients.isEmpty()) {
            return "Recommended because your analysis includes " + concernNames
                    + " concerns, and the catalog lists this product for those concerns.";
        }
        return "Recommended because your analysis includes " + concernNames
                + " concerns, and the product contains " + joinWords(ingredients)
                + ", which SkinCart maps to those concerns.";
    }

    private String joinWords(List<String> words) {
        if (words.size() == 1) {
            return words.getFirst();
        }
        if (words.size() == 2) {
            return words.getFirst() + " and " + words.getLast();
        }
        return String.join(", ", words.subList(0, words.size() - 1)) + ", and " + words.getLast();
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
