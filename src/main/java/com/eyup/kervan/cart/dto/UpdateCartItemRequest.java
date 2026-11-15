package com.eyup.kervan.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// to remove a line use DELETE, not quantity 0
public record UpdateCartItemRequest(
        @NotNull @Min(1) @Max(99) Integer quantity
) {
}
