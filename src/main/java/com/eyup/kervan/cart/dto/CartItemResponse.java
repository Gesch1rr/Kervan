package com.eyup.kervan.cart.dto;

import java.math.BigDecimal;

// available: product is still active and has enough stock for this quantity; prices are current, not frozen until checkout
public record CartItemResponse(
        Long productId,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        boolean available) {
}
