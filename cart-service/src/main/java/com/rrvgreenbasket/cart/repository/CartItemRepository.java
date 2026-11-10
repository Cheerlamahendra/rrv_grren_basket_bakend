package com.rrvgreenbasket.cart.repository;

import com.rrvgreenbasket.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCartIdAndProductPublicIdAndUnitOfMeasure(
            Long cartId,
            String productPublicId,
            String unitOfMeasure
    );
    Optional<CartItem> findByPublicIdAndCartId(
            String publicId,
            Long cartId
    );

}