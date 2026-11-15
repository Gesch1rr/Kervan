package com.eyup.kervan.product.repository;

import com.eyup.kervan.product.entity.Product;
import org.springframework.data.jpa.domain.Specification;

// Each optional filter is its own Specification, so a missing filter is simply left out of the query
// instead of "(:param is null or ...)", which PostgreSQL cannot type when the parameter is null.
public final class ProductSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private ProductSpecifications() {
    }

    public static Specification<Product> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    public static Specification<Product> inCategory(Long categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    // Case-insensitive "contains". Both sides are lowered by the database so Java and PostgreSQL
    // cannot disagree on letters like the Turkish İ/ı.
    public static Specification<Product> nameContains(String text) {
        String pattern = "%" + escapeLike(text) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), cb.lower(cb.literal(pattern)), LIKE_ESCAPE);
    }

    // A user typing "%" or "_" should search for that character, not use it as a wildcard.
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
