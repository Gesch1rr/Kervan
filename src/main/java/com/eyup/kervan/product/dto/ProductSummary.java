package com.eyup.kervan.product.dto;

import java.math.BigDecimal;

// What other modules (cart, order) may know about a product; never cached, always read from the database.
public record ProductSummary(Long id, String name, BigDecimal price, int stock, boolean active) {
}
