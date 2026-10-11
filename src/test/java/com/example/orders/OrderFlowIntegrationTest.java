package com.example.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.orders.api.dto.AuditEntryResponse;
import com.example.orders.api.dto.OrderResponse;
import com.example.orders.domain.OrderStatus;
import com.example.orders.messaging.OrderEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;

/** End-to-end: REST -> MongoDB + DynamoDB + Kafka, then a payment event back over Kafka. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, OrderFlowIntegrationTest.OrderEventsCollector.class})
class OrderFlowIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    OrderEventsCollector collector;

    @Test
    void orderIsCreatedThenPaidViaPaymentEvent() throws Exception {
        var created = rest.postForEntity("/api/orders", Map.of(
                "customerId", "cust-42",
                "items", List.of(Map.of("sku", "BOOK-1", "quantity", 2, "unitPrice", 12.5))), OrderResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String orderId = created.getBody().id();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(collector.eventsFor(orderId)).extracting(OrderEvent::type).contains(OrderEvent.ORDER_CREATED));

        kafka.send("payments.events", orderId,
                mapper.writeValueAsString(Map.of("orderId", orderId, "result", "SUCCEEDED")));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(rest.getForObject("/api/orders/" + orderId, OrderResponse.class).status())
                        .isEqualTo(OrderStatus.PAID));

        List<AuditEntryResponse> history = rest.exchange("/api/orders/" + orderId + "/history", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<AuditEntryResponse>>() {}).getBody();
        assertThat(history).extracting(AuditEntryResponse::action).containsExactly("CREATED", "PAID");
    }

    @TestConfiguration
    static class OrderEventsCollector {

        private final List<OrderEvent> received = new CopyOnWriteArrayList<>();

        @Autowired
        ObjectMapper mapper;

        @KafkaListener(topics = "orders.events", groupId = "test-collector")
        void collect(ConsumerRecord<String, String> record) throws Exception {
            received.add(mapper.readValue(record.value(), OrderEvent.class));
        }

        List<OrderEvent> eventsFor(String orderId) {
            return new ArrayList<>(received.stream().filter(e -> e.orderId().equals(orderId)).toList());
        }
    }
}
