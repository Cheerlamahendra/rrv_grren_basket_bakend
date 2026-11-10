package com.rrvgreenbasket.wishlist.repository;

import com.rrvgreenbasket.wishlist.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    Optional<WishlistItem> findByWishlistIdAndProductPublicId(Long wishlistId, String productPublicId);

    boolean existsByWishlistIdAndProductPublicId(Long wishlistId, String productPublicId);

    @Query("SELECT COUNT(i) FROM WishlistItem i WHERE i.wishlist.id = :wishlistId")
    int countByWishlistId(@Param("wishlistId") Long wishlistId);

    @Query("SELECT COUNT(i) FROM WishlistItem i WHERE i.wishlist.customerPublicId = :customerPublicId")
    int countByCustomerPublicId(@Param("customerPublicId") String customerPublicId);

    void deleteByWishlistIdAndProductPublicId(Long wishlistId, String productPublicId);

    void deleteByWishlistId(Long wishlistId);

    List<WishlistItem> findByWishlistIdAndProductPublicIdIn(Long wishlistId, List<String> productPublicIds);

    @Query("SELECT i.productPublicId FROM WishlistItem i WHERE i.wishlist.customerPublicId = :customerPublicId AND i.productPublicId IN :productPublicIds")
    List<String> findExistingProductPublicIdsByCustomerAndProducts(
            @Param("customerPublicId") String customerPublicId,
            @Param("productPublicIds") List<String> productPublicIds);
}
