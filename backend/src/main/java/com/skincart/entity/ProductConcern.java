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
        name = "product_concern",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_concern_pair",
                columnNames = {"product_id", "concern_code"}))
public class ProductConcern {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "concern_code", nullable = false, length = 80)
    private String concernCode;

    @Column(name = "relevance_weight", nullable = false)
    private int relevanceWeight;

    protected ProductConcern() {
        // Required by JPA.
    }

    public ProductConcern(Product product, String concernCode, int relevanceWeight) {
        this.product = product;
        this.concernCode = concernCode;
        this.relevanceWeight = relevanceWeight;
    }

    public UUID getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public String getConcernCode() {
        return concernCode;
    }

    public int getRelevanceWeight() {
        return relevanceWeight;
    }
}
