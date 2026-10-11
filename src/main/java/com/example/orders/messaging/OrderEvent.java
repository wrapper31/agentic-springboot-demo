package com.example.orders.messaging;

import com.example.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** Published to {@code app.topics.order-events}, keyed by orderId. */
public record OrderEvent(
        String type, String orderId, String customerId, OrderStatus status, BigDecimal total, Instant occurredAt) {

    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String ORDER_STATUS_CHANGED = "ORDER_STATUS_CHANGED";
}
