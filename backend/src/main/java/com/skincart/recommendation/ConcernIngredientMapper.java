package com.skincart.recommendation;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ConcernIngredientMapper {

    private static final Map<String, List<String>> INGREDIENTS_BY_CONCERN = Map.of(
            "acne", List.of("Salicylic Acid", "Niacinamide", "Azelaic Acid"),
            "pore", List.of("Niacinamide", "Salicylic Acid"),
            "texture", List.of("Retinol", "Salicylic Acid", "Vitamin C"),
            "wrinkle", List.of("Retinol", "Vitamin C", "Niacinamide"));

    public List<String> ingredientsFor(String concernCode) {
        if (concernCode == null) {
            return List.of();
        }
        return INGREDIENTS_BY_CONCERN.getOrDefault(concernCode.toLowerCase(Locale.ROOT), List.of());
    }
}
