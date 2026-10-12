package com.example.orders.api.dto;

import com.example.orders.domain.Order;
import com.example.orders.domain.OrderStatus;
import com.example.orders.domain.Shipment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String id,
        String customerId,
        List<Item> items,
        BigDecimal total,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt,
        ShipmentResponse shipment) {

    public record Item(String sku, int quantity, BigDecimal unitPrice) {}

    public record ShipmentResponse(String carrier, String trackingNumber, Instant shippedAt) {

        static ShipmentResponse from(Shipment shipment) {
            return shipment == null
                    ? null
                    : new ShipmentResponse(shipment.carrier(), shipment.trackingNumber(), shipment.shippedAt());
        }
    }

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.customerId(),
                order.items().stream().map(i -> new Item(i.sku(), i.quantity(), i.unitPrice())).toList(),
                order.total(),
                order.status(),
                order.createdAt(),
                order.updatedAt(),
                ShipmentResponse.from(order.shipment()));
    }
}
