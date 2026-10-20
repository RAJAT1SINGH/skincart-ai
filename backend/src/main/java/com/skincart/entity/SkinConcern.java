package com.skincart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "skin_concern")
public class SkinConcern {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private SkinAnalysis analysis;

    @Column(name = "concern_code", nullable = false, length = 80)
    private String concernCode;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "severity", length = 40)
    private String severity;

    @Column(name = "ui_score")
    private Integer uiScore;

    @Column(name = "raw_score", precision = 12, scale = 9)
    private BigDecimal rawScore;

    @OneToMany(mappedBy = "skinConcern", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private List<SkinConcernMask> masks = new ArrayList<>();

    protected SkinConcern() {
        // Required by JPA.
    }

    public SkinConcern(SkinAnalysis analysis, String concernCode, String displayName, String severity) {
        this.analysis = analysis;
        this.concernCode = concernCode;
        this.displayName = displayName;
        this.severity = severity;
    }

    public SkinConcern(
            SkinAnalysis analysis,
            String concernCode,
            String displayName,
            Integer uiScore,
            BigDecimal rawScore,
            List<String> maskUrls) {
        this.analysis = analysis;
        this.concernCode = concernCode;
        this.displayName = displayName;
        this.uiScore = uiScore;
        this.rawScore = rawScore;
        if (maskUrls != null) {
            maskUrls.forEach(maskUrl -> masks.add(new SkinConcernMask(this, maskUrl)));
        }
    }

    public UUID getId() {
        return id;
    }

    public SkinAnalysis getAnalysis() {
        return analysis;
    }

    public String getConcernCode() {
        return concernCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSeverity() {
        return severity;
    }

    public Integer getUiScore() {
        return uiScore;
    }

    public BigDecimal getRawScore() {
        return rawScore;
    }

    public List<String> getMaskUrls() {
        return masks.stream().map(SkinConcernMask::getMaskUrl).toList();
    }
}
