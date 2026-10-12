package com.example.orders.persistence.mongo;

import com.example.orders.domain.Order;
import com.example.orders.domain.OrderItem;
import com.example.orders.domain.OrderStatus;
import com.example.orders.domain.Shipment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("orders")
public record OrderDocument(
        @Id String id,
        @Indexed String customerId,
        List<Item> items,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt,
        ShipmentInfo shipment) {

    public record ShipmentInfo(String carrier, String trackingNumber, Instant shippedAt) {}

    public record Item(String sku, int quantity, BigDecimal unitPrice) {}

    public static OrderDocument fromDomain(Order order) {
        return new OrderDocument(
                order.id(),
                order.customerId(),
                order.items().stream().map(i -> new Item(i.sku(), i.quantity(), i.unitPrice())).toList(),
                order.status(),
                order.createdAt(),
                order.updatedAt(),
                order.shipment() == null
                        ? null
                        : new ShipmentInfo(order.shipment().carrier(), order.shipment().trackingNumber(), order.shipment().shippedAt()));
    }

    public Order toDomain() {
        return new Order(
                id,
                customerId,
                items.stream().map(i -> new OrderItem(i.sku(), i.quantity(), i.unitPrice())).toList(),
                status,
                createdAt,
                updatedAt,
                shipment == null ? null : new Shipment(shipment.carrier(), shipment.trackingNumber(), shipment.shippedAt()));
    }
}
