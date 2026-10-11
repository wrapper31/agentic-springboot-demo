package com.example.orders.messaging;

import com.example.orders.service.OrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final OrderService orderService;
    private final ObjectMapper mapper;

    public PaymentEventListener(OrderService orderService, ObjectMapper mapper) {
        this.orderService = orderService;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "${app.topics.payment-events}")
    public void onPaymentEvent(String payload) throws JsonProcessingException {
        PaymentEvent event = mapper.readValue(payload, PaymentEvent.class);
        log.info("Payment {} for order {}", event.result(), event.orderId());
        orderService.applyPaymentResult(event);
    }
}
