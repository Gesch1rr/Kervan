package com.eyup.kervan.order.repository;

import com.eyup.kervan.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // summaries only, the items collection is never touched here
    Page<Order> findByUserId(Long userId, Pageable pageable);

    // order + items in one query instead of a lazy load per order
    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long id);
}
