package com.eyup.kervan.product.dto;

import jakarta.validation.constraints.Size;

// bound from query parameters ?categoryId=&q=
public record ProductFilter(Long categoryId, @Size(max = 100) String q) {
}
