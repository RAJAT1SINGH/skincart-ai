package com.skincart.repository;

import com.skincart.entity.SkinConcern;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkinConcernRepository extends JpaRepository<SkinConcern, UUID> {

    List<SkinConcern> findAllByAnalysisIdOrderByConcernCodeAsc(UUID analysisId);

    @Query("select distinct concern from SkinConcern concern "
            + "left join fetch concern.masks "
            + "where concern.analysis.id = :analysisId "
            + "order by concern.displayName asc")
    List<SkinConcern> findAllByAnalysisIdWithMasks(@Param("analysisId") UUID analysisId);
}
