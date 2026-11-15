package com.eyup.kervan.product.repository;

import com.eyup.kervan.product.entity.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // Loads the category in the same query, so mapping to ProductResponse needs no extra select.
    @EntityGraph(attributePaths = "category")
    Optional<Product> findWithCategoryById(Long id);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsByCategoryId(Long categoryId);
}
