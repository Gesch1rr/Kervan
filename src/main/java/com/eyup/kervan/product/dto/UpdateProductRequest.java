package com.eyup.kervan.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateProductRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 5000) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal price,
        @NotNull Long categoryId,
        // version from the last GET; stock changes get their own endpoint later
        @NotNull Long version
) {
}
