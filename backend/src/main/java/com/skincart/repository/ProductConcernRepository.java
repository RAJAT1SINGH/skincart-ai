package com.skincart.repository;

import com.skincart.entity.ProductConcern;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductConcernRepository extends JpaRepository<ProductConcern, UUID> {

    @Query("select productConcern from ProductConcern productConcern "
            + "join fetch productConcern.product "
            + "where productConcern.concernCode in :concernCodes")
    List<ProductConcern> findByConcernCodesWithProduct(@Param("concernCodes") List<String> concernCodes);

    @Query("select count(distinct productConcern.product.id) from ProductConcern productConcern")
    long countCatalogProductsWithTargetConcerns();
}
