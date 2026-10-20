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
import java.util.UUID;

@Entity
@Table(name = "skin_concern_mask")
public class SkinConcernMask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skin_concern_id", nullable = false)
    private SkinConcern skinConcern;

    @Column(name = "mask_url", nullable = false, length = 2048)
    private String maskUrl;

    protected SkinConcernMask() {
        // Required by JPA.
    }

    public SkinConcernMask(SkinConcern skinConcern, String maskUrl) {
        this.skinConcern = skinConcern;
        this.maskUrl = maskUrl;
    }

    public String getMaskUrl() {
        return maskUrl;
    }
}
