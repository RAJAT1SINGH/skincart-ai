package com.skincart.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.skincart.common.ProductCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductRecommendationRankerTest {

    private final ProductRecommendationRanker ranker = new ProductRecommendationRanker(new ConcernIngredientMapper());

    @Test
    void ranksDeterministicallyDeduplicatesAndExplainsConcernAndIngredientMatches() {
        List<EvaluatedConcern> concerns = List.of(
                new EvaluatedConcern("acne", "Acne", 30, 70.0),
                new EvaluatedConcern("pore", "Pore", 60, 40.0));
        ProductCandidate broadMatch = new ProductCandidate(
                UUID.fromString("10000000-0000-4000-8000-000000000001"),
                "Demo Balance Serum",
                "Valegrove",
                ProductCategory.SERUM,
                "Synthetic demo serum.",
                List.of(new CandidateIngredient("Niacinamide", true), new CandidateIngredient("Salicylic Acid", true)),
                Map.of("acne", 5, "pore", 4));
        ProductCandidate singleMatch = new ProductCandidate(
                UUID.fromString("10000000-0000-4000-8000-000000000002"),
                "Demo Daily Cleanser",
                "Asterrow",
                ProductCategory.CLEANSER,
                "Synthetic demo cleanser.",
                List.of(new CandidateIngredient("Niacinamide", true)),
                Map.of("acne", 3));

        List<RankedRecommendation> recommendations = ranker.rank(
                concerns, List.of(singleMatch, broadMatch, broadMatch));

        assertThat(recommendations).hasSize(2);
        assertThat(recommendations.getFirst().productName()).isEqualTo("Demo Balance Serum");
        assertThat(recommendations.getFirst().score()).hasToString("91.5");
        assertThat(recommendations.getFirst().matchedConcerns()).containsExactly("ACNE", "PORE");
        assertThat(recommendations.getFirst().matchedIngredients())
                .containsExactly("Niacinamide", "Salicylic Acid");
        assertThat(recommendations.getFirst().reason())
                .isEqualTo("Recommended because your analysis includes acne and pore concerns, and the product "
                        + "contains Niacinamide and Salicylic Acid, which SkinCart maps to those concerns.");
    }

    @Test
    void retainsAllRankedCandidatesForRoutinePlanningWhileTheRecommendationViewRemainsLimited() {
        List<ProductCandidate> candidates = new ArrayList<>();
        for (int index = 1; index <= 7; index++) {
            candidates.add(new ProductCandidate(
                    UUID.fromString("10000000-0000-4000-8000-" + String.format("%012d", index)),
                    "Demo Serum " + index,
                    "Valegrove",
                    ProductCategory.SERUM,
                    "Synthetic demo serum.",
                    List.of(),
                    Map.of("acne", 5)));
        }
        List<EvaluatedConcern> concerns = List.of(new EvaluatedConcern("acne", "Acne", 30, 70.0));

        assertThat(ranker.rankAll(concerns, candidates)).hasSize(7);
        assertThat(ranker.rank(concerns, candidates)).hasSize(5);
    }
}
