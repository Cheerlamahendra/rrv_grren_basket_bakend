package com.rrvgreenbasket.product.repository;

import com.rrvgreenbasket.product.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    Optional<ProductImage> findByPublicId(String publicId);

    List<ProductImage> findAllByProduct_IdOrderBySortOrderAscCreatedAtAsc(Long productId);

    List<ProductImage> findAllByProduct_PublicIdOrderBySortOrderAscCreatedAtAsc(String productPublicId);

    Optional<ProductImage> findFirstByProduct_IdAndIsPrimaryTrue(Long productId);

    long countByProduct_Id(Long productId);
    List<ProductImage> findAllByProduct_Id(Long productId);

    void deleteAllByProduct_Id(Long productId);
}
