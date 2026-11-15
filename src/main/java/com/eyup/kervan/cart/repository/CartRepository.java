package com.eyup.kervan.cart.repository;

import com.eyup.kervan.cart.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    // Cart and items in one query; loading items lazily would cost a second select.
    @Query("select c from Cart c left join fetch c.items where c.userId = :userId")
    Optional<Cart> findWithItemsByUserId(Long userId);
}
