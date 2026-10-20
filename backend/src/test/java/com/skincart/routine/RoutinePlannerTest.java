package com.skincart.routine;

import static org.assertj.core.api.Assertions.assertThat;

import com.skincart.common.ProductCategory;
import com.skincart.recommendation.RankedRecommendation;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoutinePlannerTest {

    private static final UUID ANALYSIS_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private final RoutinePlanner planner = new RoutinePlanner();

    @Test
    void createsDeterministicMorningAndEveningRoutinesFromRankedProducts() {
        List<RankedRecommendation> products = List.of(
                product(1, "Best Cleanser", ProductCategory.CLEANSER, 95, "ACNE", "PORE"),
                product(2, "Second Cleanser", ProductCategory.CLEANSER, 90, "ACNE"),
                product(3, "Best Serum", ProductCategory.SERUM, 92, "ACNE", "PORE"),
                product(4, "Other Treatment", ProductCategory.TREATMENT, 88, "WRINKLE"),
                product(5, "Best Moisturizer", ProductCategory.MOISTURIZER, 84, "TEXTURE"),
                product(6, "Best Sunscreen", ProductCategory.SUNSCREEN, 81, "WRINKLE"));

        var result = planner.plan(ANALYSIS_ID, products);

        assertThat(result.analysisId()).isEqualTo(ANALYSIS_ID);
        assertThat(result.morning()).extracting(step -> step.step()).containsExactly(
                RoutineStep.CLEANSER, RoutineStep.TREATMENT, RoutineStep.MOISTURIZER, RoutineStep.SUNSCREEN);
        assertThat(result.evening()).extracting(step -> step.step()).containsExactly(
                RoutineStep.CLEANSER, RoutineStep.TREATMENT, RoutineStep.MOISTURIZER);
        assertThat(result.morning()).extracting(step -> step.productName()).containsExactly(
                "Best Cleanser", "Best Serum", "Best Moisturizer", "Best Sunscreen");
        assertThat(result.evening()).extracting(step -> step.productName()).containsExactly(
                "Best Cleanser", "Best Serum", "Best Moisturizer");
        assertThat(planner.plan(ANALYSIS_ID, products)).isEqualTo(result);
    }

    @Test
    void selectsTheHighestRankedSuitableTreatmentAndGeneratesExplanations() {
        var result = planner.plan(ANALYSIS_ID, List.of(
                product(10, "Highly Ranked Acne Treatment", ProductCategory.ACNE_TREATMENT, 96, "ACNE", "PORE"),
                product(11, "Lower Ranked Serum", ProductCategory.SERUM, 83, "WRINKLE")));

        assertThat(result.morning()).singleElement().satisfies(step -> {
            assertThat(step.step()).isEqualTo(RoutineStep.TREATMENT);
            assertThat(step.productName()).isEqualTo("Highly Ranked Acne Treatment");
            assertThat(step.category()).isEqualTo(ProductCategory.ACNE_TREATMENT);
            assertThat(step.reason()).isEqualTo(
                    "Selected because it matches the acne and pore concerns identified in your skin analysis.");
        });
        assertThat(result.evening().getFirst().reason())
                .isEqualTo("Selected because it matches the acne and pore concerns identified in your skin analysis.");
    }

    @Test
    void omitsStepsWhenNoSuitableCatalogProductsAreRanked() {
        var result = planner.plan(ANALYSIS_ID, List.of(
                product(20, "Only Cleanser", ProductCategory.CLEANSER, 85, "ACNE")));

        assertThat(result.morning()).extracting(step -> step.step()).containsExactly(RoutineStep.CLEANSER);
        assertThat(result.evening()).extracting(step -> step.step()).containsExactly(RoutineStep.CLEANSER);
    }

    @Test
    void avoidsRepeatingAProductWithinOneRoutineAndAllowsItAcrossMorningAndEvening() {
        UUID sharedId = id(30);
        var duplicateAsTreatment = new RankedRecommendation(
                sharedId, "Shared Product", "Demo", ProductCategory.SERUM, "Demo.", new BigDecimal("95.0"),
                List.of("ACNE"), List.of(), "Recommended.");
        var sameIdAsCleanser = new RankedRecommendation(
                sharedId, "Shared Product", "Demo", ProductCategory.CLEANSER, "Demo.", new BigDecimal("96.0"),
                List.of("ACNE"), List.of(), "Recommended.");
        var alternateTreatment = product(31, "Alternate Treatment", ProductCategory.TREATMENT, 90, "PORE");

        var result = planner.plan(ANALYSIS_ID, List.of(sameIdAsCleanser, duplicateAsTreatment, alternateTreatment));

        assertThat(result.morning()).extracting(step -> step.productName())
                .containsExactly("Shared Product", "Alternate Treatment");
        assertThat(result.evening()).extracting(step -> step.productName())
                .containsExactly("Shared Product", "Alternate Treatment");
        assertThat(result.morning()).extracting(step -> step.productId()).doesNotHaveDuplicates();
        assertThat(result.evening()).extracting(step -> step.productId()).doesNotHaveDuplicates();
    }

    @Test
    void usesTheFixedRoutineStepOrderWhenSomeStepsAreAbsent() {
        var result = planner.plan(ANALYSIS_ID, List.of(
                product(40, "Sunscreen", ProductCategory.SUNSCREEN, 70, "WRINKLE"),
                product(41, "Moisturizer", ProductCategory.MOISTURIZER, 75, "TEXTURE"),
                product(42, "Cleanser", ProductCategory.CLEANSER, 80, "ACNE")));

        assertThat(result.morning()).extracting(step -> step.step()).containsExactly(
                RoutineStep.CLEANSER, RoutineStep.MOISTURIZER, RoutineStep.SUNSCREEN);
        assertThat(result.evening()).extracting(step -> step.step()).containsExactly(
                RoutineStep.CLEANSER, RoutineStep.MOISTURIZER);
    }

    private RankedRecommendation product(
            int id, String name, ProductCategory category, double score, String... concerns) {
        return new RankedRecommendation(
                id(id),
                name,
                "Demo Brand",
                category,
                "Synthetic demo product.",
                BigDecimal.valueOf(score).setScale(1),
                List.of(concerns),
                List.of("Niacinamide"),
                "Recommended based on analyzed concerns.");
    }

    private UUID id(int value) {
        return UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-" + String.format("%012d", value));
    }
}
