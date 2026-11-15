package com.eyup.kervan.order.repository;

import com.eyup.kervan.order.entity.Order;
import com.eyup.kervan.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // summaries only, the items collection is never touched here
    Page<Order> findByUserId(Long userId, Pageable pageable);

    // order + items in one query instead of a lazy load per order
    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long id);

    // Conditional UPDATE: PostgreSQL locks the row and re-checks the WHERE clause, so of two concurrent
    // cancels only one can succeed (the other sees status CANCELLED and updates 0 rows).
    @Modifying(flushAutomatically = true)
    @Query("""
            update Order o set o.status = com.eyup.kervan.order.entity.OrderStatus.CANCELLED, o.updatedAt = :now
            where o.id = :id and o.userId = :userId and o.status = com.eyup.kervan.order.entity.OrderStatus.PENDING
            """)
    int cancelIfOwner(Long id, Long userId, Instant now);

    boolean existsByIdAndUserId(Long id, Long userId);

    // Same idea as cancelIfOwner: the WHERE clause re-checks the current status under the row lock.
    @Modifying(flushAutomatically = true)
    @Query("""
            update Order o set o.status = :to, o.updatedAt = :now
            where o.id = :id and o.status in :from
            """)
    int updateStatus(Long id, Collection<OrderStatus> from, OrderStatus to, Instant now);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
}
