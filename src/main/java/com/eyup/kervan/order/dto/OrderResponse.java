package com.eyup.kervan.order.dto;

import com.eyup.kervan.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(Long id, OrderStatus status, BigDecimal totalAmount, ShippingAddress shippingAddress,
                            List<OrderItemResponse> items, Instant createdAt) {
}
