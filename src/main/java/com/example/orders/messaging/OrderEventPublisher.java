package com.example.orders.messaging;

import com.example.orders.config.AppProperties;
import com.example.orders.domain.Order;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public OrderEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper, AppProperties props) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = props.topics().orderEvents();
    }

    public void publish(String type, Order order) {
        var event = new OrderEvent(
                type, order.id(), order.customerId(), order.status(), order.total(), Instant.now());
        try {
            kafka.send(topic, order.id(), mapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize " + event, e);
        }
    }
}
