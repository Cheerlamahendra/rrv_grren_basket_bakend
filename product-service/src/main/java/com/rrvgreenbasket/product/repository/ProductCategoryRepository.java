package com.rrvgreenbasket.product.repository;

import com.rrvgreenbasket.product.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    Optional<ProductCategory> findByPublicId(String publicId);

    Optional<ProductCategory> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByName(String name);

    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsByNameAndIdNot(String name, Long id);

    List<ProductCategory> findAllByIsActiveTrueOrderBySortOrderAscNameAsc();

    List<ProductCategory> findAllByOrderBySortOrderAscNameAsc();
}
