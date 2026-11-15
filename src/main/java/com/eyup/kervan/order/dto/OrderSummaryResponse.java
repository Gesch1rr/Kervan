package com.eyup.kervan.order.dto;

import com.eyup.kervan.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

// no item count on purpose: it would force loading the items collection -> N+1
public record OrderSummaryResponse(Long id, OrderStatus status, BigDecimal totalAmount, Instant createdAt) {
}
