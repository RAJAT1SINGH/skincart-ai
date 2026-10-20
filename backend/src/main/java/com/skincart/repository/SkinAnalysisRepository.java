package com.skincart.repository;

import com.skincart.entity.SkinAnalysis;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkinAnalysisRepository extends JpaRepository<SkinAnalysis, UUID> {
}
