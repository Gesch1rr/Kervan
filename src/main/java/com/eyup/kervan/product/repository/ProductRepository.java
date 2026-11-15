package com.eyup.kervan.product.repository;

import com.eyup.kervan.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    // Joins the category into the page query; without it every row would trigger its own category select (N+1).
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    // Loads the category in the same query, so mapping to ProductResponse needs no extra select.
    @EntityGraph(attributePaths = "category")
    Optional<Product> findWithCategoryById(Long id);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsByCategoryId(Long categoryId);

    // Stock changes are single conditional UPDATEs instead of read-modify-write in Java. PostgreSQL locks the
    // row and re-checks the WHERE clause, so concurrent orders queue up and stock can never go below zero.
    // They return the number of updated rows: 0 means the condition did not hold.
    //
    // No clearAutomatically: clearing would detach every entity the calling transaction (e.g. checkout) has
    // loaded. The trade-off: a Product already loaded in the same transaction still shows the old stock.
    // The version is not bumped either, so admin edits (@Version) do not conflict with orders.
    @Modifying(flushAutomatically = true)
    @Query("""
            update Product p set p.stock = p.stock - :quantity, p.updatedAt = :now
            where p.id = :id and p.active = true and p.stock >= :quantity
            """)
    int decreaseStock(Long id, int quantity, Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Product p set p.stock = p.stock + :quantity, p.updatedAt = :now
            where p.id = :id
            """)
    int increaseStock(Long id, int quantity, Instant now);

    // Admin correction in either direction; refuses to go below zero.
    @Modifying(flushAutomatically = true)
    @Query("""
            update Product p set p.stock = p.stock + :delta, p.updatedAt = :now
            where p.id = :id and p.stock + :delta >= 0
            """)
    int adjustStock(Long id, int delta, Instant now);
}
