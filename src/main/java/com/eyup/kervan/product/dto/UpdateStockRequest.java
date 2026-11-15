package com.eyup.kervan.product.dto;

import jakarta.validation.constraints.NotNull;

// Positive adds stock, negative removes it.
public record UpdateStockRequest(@NotNull Integer delta) {
}
