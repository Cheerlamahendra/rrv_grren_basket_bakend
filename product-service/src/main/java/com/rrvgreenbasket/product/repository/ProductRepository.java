package com.rrvgreenbasket.product.repository;

import com.rrvgreenbasket.product.entity.Product;
import com.rrvgreenbasket.product.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByPublicId(String publicId);

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    @Override
    @EntityGraph(attributePaths = {"category"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    @Query("SELECT p FROM Product p JOIN FETCH p.category LEFT JOIN FETCH p.images WHERE p.publicId = :publicId")
    Optional<Product> findByPublicIdWithDetails(@Param("publicId") String publicId);

    // Customer catalog queries: strictly ACTIVE status and adminEnabled = true
    @Query("SELECT p FROM Product p JOIN FETCH p.category LEFT JOIN FETCH p.images " +
           "WHERE p.publicId = :publicId AND p.status = 'ACTIVE' AND p.adminEnabled = true")
    Optional<Product> findAvailableByPublicId(@Param("publicId") String publicId);

    @Query("SELECT DISTINCT p FROM Product p JOIN FETCH p.category LEFT JOIN FETCH p.images " +
           "WHERE p.status = 'ACTIVE' AND p.adminEnabled = true " +
           "ORDER BY p.category.sortOrder ASC, p.name ASC")
    List<Product> findAllAvailableForCatalog();

    @Query("SELECT DISTINCT p FROM Product p JOIN FETCH p.category LEFT JOIN FETCH p.images " +
           "WHERE p.category.slug = :categorySlug AND p.status = 'ACTIVE' AND p.adminEnabled = true " +
           "ORDER BY p.name ASC")
    List<Product> findAvailableByCategorySlug(@Param("categorySlug") String categorySlug);

    @Query("SELECT DISTINCT p FROM Product p JOIN FETCH p.category LEFT JOIN FETCH p.images " +
           "WHERE p.isFeatured = true AND p.status = 'ACTIVE' AND p.adminEnabled = true " +
           "ORDER BY p.category.sortOrder ASC, p.name ASC")
    List<Product> findAvailableFeatured();
}
