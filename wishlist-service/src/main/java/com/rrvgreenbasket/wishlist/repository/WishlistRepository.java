package com.rrvgreenbasket.wishlist.repository;

import com.rrvgreenbasket.wishlist.entity.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    Optional<Wishlist> findByCustomerPublicId(String customerPublicId);

    @Query("SELECT w FROM Wishlist w LEFT JOIN FETCH w.items WHERE w.customerPublicId = :customerPublicId")
    Optional<Wishlist> findByCustomerPublicIdWithItems(@Param("customerPublicId") String customerPublicId);

    Optional<Wishlist> findByWishlistPublicId(String wishlistPublicId);

    boolean existsByCustomerPublicId(String customerPublicId);
}
