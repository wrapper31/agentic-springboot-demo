package com.example.orders.service;

import com.example.orders.domain.Order;
import com.example.orders.domain.OrderItem;
import com.example.orders.domain.OrderStatus;
import com.example.orders.messaging.OrderEvent;
import com.example.orders.messaging.OrderEventPublisher;
import com.example.orders.messaging.PaymentEvent;
import com.example.orders.persistence.dynamo.OrderAuditRecord;
import com.example.orders.persistence.dynamo.OrderAuditRepository;
import com.example.orders.persistence.mongo.OrderDocument;
import com.example.orders.persistence.mongo.OrderMongoRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Business logic for orders. The only layer allowed to coordinate persistence and messaging:
 * every state change is saved to MongoDB, audited in DynamoDB, then published to Kafka.
 */
@Service
public class OrderService {

    private final OrderMongoRepository orders;
    private final OrderAuditRepository audit;
    private final OrderEventPublisher events;
    private final Clock clock;

    public OrderService(
            OrderMongoRepository orders, OrderAuditRepository audit, OrderEventPublisher events, Clock clock) {
        this.orders = orders;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    public Order create(String customerId, List<OrderItem> items) {
        Instant now = clock.instant();
        Order order = new Order(UUID.randomUUID().toString(), customerId, items, OrderStatus.CREATED, now, now);
        orders.save(OrderDocument.fromDomain(order));
        audit.save(new OrderAuditRecord(order.id(), now.toString(), "CREATED", "total=" + order.total()));
        events.publish(OrderEvent.ORDER_CREATED, order);
        return order;
    }

    public Order get(String orderId) {
        return orders.findById(orderId).map(OrderDocument::toDomain).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    public List<Order> findByCustomer(String customerId) {
        return orders.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(OrderDocument::toDomain)
                .toList();
    }

    public Order cancel(String orderId) {
        Order order = get(orderId);
        if (order.status() != OrderStatus.CREATED) {
            throw new InvalidOrderStateException("Only CREATED orders can be cancelled, was " + order.status());
        }
        return transition(order, OrderStatus.CANCELLED, "cancelled by customer");
    }

    public void applyPaymentResult(PaymentEvent payment) {
        Order order = get(payment.orderId());
        if (order.status() != OrderStatus.CREATED) {
            // Duplicate or late delivery: payments are idempotent per order.
            return;
        }
        OrderStatus next = payment.result() == PaymentEvent.Result.SUCCEEDED ? OrderStatus.PAID : OrderStatus.PAYMENT_FAILED;
        transition(order, next, payment.reason());
    }

    public List<OrderAuditRecord> history(String orderId) {
        get(orderId);
        return audit.findByOrderId(orderId);
    }

    private Order transition(Order order, OrderStatus next, String detail) {
        Instant now = clock.instant();
        Order updated = order.withStatus(next, now);
        orders.save(OrderDocument.fromDomain(updated));
        audit.save(new OrderAuditRecord(order.id(), now.toString(), next.name(), detail));
        events.publish(OrderEvent.ORDER_STATUS_CHANGED, updated);
        return updated;
    }
}
