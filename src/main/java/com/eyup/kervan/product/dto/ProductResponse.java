package com.eyup.kervan.product.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        String slug,
        String description,
        BigDecimal price,
        int stock,
        boolean active,
        CategoryResponse category,
        Long version,
        Instant createdAt
) {
}
