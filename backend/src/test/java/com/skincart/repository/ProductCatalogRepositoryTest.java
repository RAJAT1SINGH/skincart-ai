package com.skincart.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class ProductCatalogRepositoryTest {

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private ProductConcernRepository productConcernRepository;

    @Autowired
    private ProductIngredientRepository productIngredientRepository;

    @Test
    void flywaySeedsSeventeenProductsAndSevenIngredients() {
        assertThat(ingredientRepository.count()).isEqualTo(7);
        assertThat(productConcernRepository.countCatalogProductsWithTargetConcerns()).isEqualTo(17);
        assertThat(productIngredientRepository.count()).isEqualTo(36);
    }

    @Test
    void loadsProductCandidatesWithRelationshipsInBatchQueries() {
        var acneProducts = productConcernRepository.findByConcernCodesWithProduct(List.of("acne"));
        var niacinamideProducts = productIngredientRepository.findByIngredientNamesWithProductAndIngredient(
                List.of("niacinamide"));

        assertThat(acneProducts)
                .extracting(row -> row.getProduct().getName())
                .contains("Demo Clear Day Spot Gel", "Demo Clear Path Gel Cleanser");
        assertThat(niacinamideProducts)
                .extracting(row -> row.getProduct().getName())
                .contains("Demo Balance Grid Serum", "Demo Dew Reset Serum");
        assertThat(niacinamideProducts).anySatisfy(row -> {
            assertThat(row.getIngredient().getName()).isEqualTo("Niacinamide");
            assertThat(row.getProduct().getCategory().name()).isEqualTo("SERUM");
        });
    }
}
