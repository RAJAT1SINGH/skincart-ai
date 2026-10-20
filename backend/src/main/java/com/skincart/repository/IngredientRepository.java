package com.skincart.repository;

import com.skincart.entity.Ingredient;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientRepository extends JpaRepository<Ingredient, UUID> {
}
