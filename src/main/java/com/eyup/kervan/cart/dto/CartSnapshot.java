package com.eyup.kervan.cart.dto;

import java.util.List;

// What checkout needs from the cart: product ids and quantities only. Prices come from the product module.
public record CartSnapshot(List<Line> lines) {

    public record Line(Long productId, int quantity) {
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
