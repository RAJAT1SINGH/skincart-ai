package com.skincart.repository;

import com.skincart.entity.ProductIngredient;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductIngredientRepository extends JpaRepository<ProductIngredient, UUID> {

    @Query("select productIngredient from ProductIngredient productIngredient "
            + "join fetch productIngredient.product "
            + "join fetch productIngredient.ingredient ingredient "
            + "where lower(ingredient.name) in :ingredientNames")
    List<ProductIngredient> findByIngredientNamesWithProductAndIngredient(
            @Param("ingredientNames") List<String> ingredientNames);
}
