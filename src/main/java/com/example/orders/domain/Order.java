package com.example.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Domain model for an order. Immutable; state changes return a new instance.
 * Persistence and API layers map to and from this type, never expose it directly.
 */
public record Order(
        String id,
        String customerId,
        List<OrderItem> items,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public BigDecimal total() {
        return items.stream().map(OrderItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Order withStatus(OrderStatus newStatus, Instant at) {
        return new Order(id, customerId, items, newStatus, createdAt, at);
    }
}
