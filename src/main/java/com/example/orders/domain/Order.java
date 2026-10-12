package com.example.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Domain model for an order. Immutable; state changes return a new instance.
 * Persistence and API layers map to and from this type, never expose it directly.
 *
 * @param shipment set once the order is {@link OrderStatus#SHIPPED}, otherwise null
 */
public record Order(
        String id,
        String customerId,
        List<OrderItem> items,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt,
        Shipment shipment) {

    /** An order that has not shipped yet. */
    public Order(
            String id, String customerId, List<OrderItem> items, OrderStatus status, Instant createdAt, Instant updatedAt) {
        this(id, customerId, items, status, createdAt, updatedAt, null);
    }

    public BigDecimal total() {
        return items.stream().map(OrderItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Order withStatus(OrderStatus newStatus, Instant at) {
        return new Order(id, customerId, items, newStatus, createdAt, at, shipment);
    }

    public Order withShipment(Shipment newShipment) {
        return new Order(id, customerId, items, status, createdAt, updatedAt, newShipment);
    }
}
