package com.example.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.orders.domain.Order;
import com.example.orders.domain.OrderItem;
import com.example.orders.domain.OrderStatus;
import com.example.orders.domain.Shipment;
import com.example.orders.messaging.OrderEvent;
import com.example.orders.messaging.OrderEventPublisher;
import com.example.orders.messaging.PaymentEvent;
import com.example.orders.persistence.dynamo.OrderAuditRepository;
import com.example.orders.persistence.mongo.OrderDocument;
import com.example.orders.persistence.mongo.OrderMongoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final OrderMongoRepository orders = mock(OrderMongoRepository.class);
    private final OrderAuditRepository audit = mock(OrderAuditRepository.class);
    private final OrderEventPublisher events = mock(OrderEventPublisher.class);
    private final OrderService service =
            new OrderService(orders, audit, events, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createSavesAuditsAndPublishes() {
        Order order = service.create("c-1", List.of(new OrderItem("SKU-1", 2, new BigDecimal("10.50"))));

        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.total()).isEqualByComparingTo("21.00");
        verify(orders).save(any(OrderDocument.class));
        verify(audit).save(any());
        verify(events).publish(OrderEvent.ORDER_CREATED, order);
    }

    @Test
    void successfulPaymentMarksOrderPaid() {
        Order existing = existingOrder(OrderStatus.CREATED);

        service.applyPaymentResult(new PaymentEvent(existing.id(), PaymentEvent.Result.SUCCEEDED, null));

        verify(events).publish(eq(OrderEvent.ORDER_STATUS_CHANGED), eq(existing.withStatus(OrderStatus.PAID, NOW)));
    }

    @Test
    void duplicatePaymentIsIgnored() {
        Order existing = existingOrder(OrderStatus.PAID);

        service.applyPaymentResult(new PaymentEvent(existing.id(), PaymentEvent.Result.SUCCEEDED, null));

        verify(orders, never()).save(any());
        verify(events, never()).publish(any(), any());
    }

    @Test
    void shipsPaidOrder() {
        Order existing = existingOrder(OrderStatus.PAID);

        Order shipped = service.ship(existing.id(), "UPS", "1Z999");

        assertThat(shipped.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(shipped.shipment()).isEqualTo(new Shipment("UPS", "1Z999", NOW));
        verify(orders).save(OrderDocument.fromDomain(shipped));
        verify(audit).save(any());
        verify(events).publish(OrderEvent.ORDER_STATUS_CHANGED, shipped);
    }

    @Test
    void cannotShipUnpaidOrder() {
        Order existing = existingOrder(OrderStatus.CREATED);

        assertThatThrownBy(() -> service.ship(existing.id(), "UPS", "1Z999"))
                .isInstanceOf(InvalidOrderStateException.class);
        verify(events, never()).publish(any(), any());
    }

    @Test
    void cannotCancelPaidOrder() {
        Order existing = existingOrder(OrderStatus.PAID);

        assertThatThrownBy(() -> service.cancel(existing.id())).isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void unknownOrderThrowsNotFound() {
        when(orders.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("missing")).isInstanceOf(OrderNotFoundException.class);
    }

    private Order existingOrder(OrderStatus status) {
        Order order = new Order(
                "o-1", "c-1", List.of(new OrderItem("SKU-1", 1, BigDecimal.TEN)), status, NOW, NOW);
        when(orders.findById(order.id())).thenReturn(Optional.of(OrderDocument.fromDomain(order)));
        return order;
    }
}
