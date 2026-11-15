package com.eyup.kervan.order.event;

import com.eyup.kervan.order.entity.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderCreatedEvent(UUID eventId, Long orderId, Long userId, BigDecimal totalAmount, Instant occurredAt) {

    // eventId lets the consumer recognise a redelivered message.
    public static OrderCreatedEvent of(Order order) {
        return new OrderCreatedEvent(UUID.randomUUID(), order.getId(), order.getUserId(),
                order.getTotalAmount(), Instant.now());
    }
}
