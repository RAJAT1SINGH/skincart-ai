package com.skincart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(
        name = "product_ingredient",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_ingredient_pair",
                columnNames = {"product_id", "ingredient_id"}))
public class ProductIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected ProductIngredient() {
        // Required by JPA.
    }

    public ProductIngredient(Product product, Ingredient ingredient) {
        this(product, ingredient, false);
    }

    public ProductIngredient(Product product, Ingredient ingredient, boolean active) {
        this.product = product;
        this.ingredient = ingredient;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public boolean isActive() {
        return active;
    }
}
