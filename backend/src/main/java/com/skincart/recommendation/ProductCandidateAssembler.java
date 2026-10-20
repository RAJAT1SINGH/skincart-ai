package com.skincart.recommendation;

import com.skincart.entity.Product;
import com.skincart.entity.ProductConcern;
import com.skincart.entity.ProductIngredient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProductCandidateAssembler {

    public List<ProductCandidate> assemble(
            List<ProductConcern> concernRows, List<ProductIngredient> ingredientRows) {
        Map<UUID, CandidateBuilder> candidates = new LinkedHashMap<>();
        concernRows.forEach(row -> candidates.computeIfAbsent(
                        row.getProduct().getId(), ignored -> new CandidateBuilder(row.getProduct()))
                .addConcern(row));
        ingredientRows.forEach(row -> candidates.computeIfAbsent(
                        row.getProduct().getId(), ignored -> new CandidateBuilder(row.getProduct()))
                .addIngredient(row));

        return candidates.values().stream()
                .map(CandidateBuilder::build)
                .sorted(Comparator.comparing(ProductCandidate::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ProductCandidate::productId))
                .toList();
    }

    private static class CandidateBuilder {

        private final Product product;
        private final Map<String, CandidateIngredient> ingredients = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        private final Map<String, Integer> targetConcernWeights = new LinkedHashMap<>();

        private CandidateBuilder(Product product) {
            this.product = product;
        }

        private void addConcern(ProductConcern productConcern) {
            targetConcernWeights.merge(
                    productConcern.getConcernCode().toLowerCase(Locale.ROOT),
                    productConcern.getRelevanceWeight(),
                    Math::max);
        }

        private void addIngredient(ProductIngredient productIngredient) {
            String name = productIngredient.getIngredient().getName();
            ingredients.merge(
                    name.toLowerCase(Locale.ROOT),
                    new CandidateIngredient(name, productIngredient.isActive()),
                    (existing, incoming) -> new CandidateIngredient(
                            existing.name(), existing.active() || incoming.active()));
        }

        private ProductCandidate build() {
            List<CandidateIngredient> candidateIngredients = new ArrayList<>(ingredients.values());
            return new ProductCandidate(
                    product.getId(),
                    product.getName(),
                    product.getBrand(),
                    product.getCategory(),
                    product.getDescription(),
                    candidateIngredients,
                    targetConcernWeights);
        }
    }
}
