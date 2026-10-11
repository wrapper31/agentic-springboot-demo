package com.example.codebasemcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.codebasemcp.index.CodebaseAnalyzer;
import com.example.codebasemcp.index.CodebaseIndex;
import com.example.codebasemcp.index.Model.DataModel;
import com.example.codebasemcp.index.Model.Endpoint;
import com.example.codebasemcp.index.Model.KafkaTopology;
import com.example.codebasemcp.index.Model.TypeSummary;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the analyzer against the order-service in this repository (two levels up). */
class CodebaseAnalyzerTest {

    private final CodebaseAnalyzer analyzer = new CodebaseAnalyzer(new CodebaseIndex("../.."));

    @Test
    void findsAllOrderEndpointsWithResolvedPaths() {
        List<Endpoint> endpoints = analyzer.endpoints("/api/orders");

        assertThat(endpoints).extracting(e -> e.httpMethod() + " " + e.path()).containsExactlyInAnyOrder(
                "GET /api/orders",
                "POST /api/orders",
                "GET /api/orders/{orderId}",
                "POST /api/orders/{orderId}/cancel",
                "GET /api/orders/{orderId}/history");

        Endpoint create = endpoints.stream().filter(e -> e.httpMethod().equals("POST") && e.path().equals("/api/orders")).findFirst().orElseThrow();
        assertThat(create.handler()).isEqualTo("OrderController#create");
        assertThat(create.requestBody()).isEqualTo("CreateOrderRequest request");
        assertThat(create.location()).startsWith("src/main/java/com/example/orders/api/OrderController.java:");
    }

    @Test
    void resolvesKafkaTopicsFromConfiguration() {
        KafkaTopology kafka = analyzer.kafka();

        assertThat(kafka.consumers()).singleElement().satisfies(c -> {
            assertThat(c.topics()).containsExactly("payments.events");
            assertThat(c.groupId()).isEqualTo("order-service");
            assertThat(c.handler()).isEqualTo("PaymentEventListener#onPaymentEvent");
        });
        assertThat(kafka.producers()).singleElement().satisfies(p -> {
            assertThat(p.resolvedTopic()).isEqualTo("orders.events");
            assertThat(p.producer()).isEqualTo("OrderEventPublisher#publish");
        });
        assertThat(kafka.declaredTopics()).extracting(t -> t.resolvedTopic())
                .containsExactlyInAnyOrder("orders.events", "payments.events");
        assertThat(kafka.topicConfig()).containsEntry("app.topics.order-events", "orders.events");
    }

    @Test
    void findsMongoAndDynamoModels() {
        List<DataModel> models = analyzer.dataModels(false);

        assertThat(models).extracting(DataModel::store, DataModel::collectionOrTable, DataModel::keys)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("mongodb", "orders", List.of("id")),
                        org.assertj.core.groups.Tuple.tuple("dynamodb", "order-audit",
                                List.of("orderId (partition key)", "occurredAt (sort key)")));
    }

    @Test
    void recordsAreOptIn() {
        assertThat(analyzer.dataModels(true)).extracting(DataModel::type)
                .contains("com.example.orders.domain.Order", "com.example.orders.messaging.OrderEvent");
    }

    @Test
    void describesTypeWithUsages() {
        List<TypeSummary> result = analyzer.describeType("OrderService");

        assertThat(result).singleElement().satisfies(t -> {
            assertThat(t.qualifiedName()).isEqualTo("com.example.orders.service.OrderService");
            assertThat(t.annotations()).contains("@Service");
            assertThat(t.methods()).anyMatch(m -> m.contains("Order create(String customerId, List<OrderItem> items)"));
            assertThat(t.usedBy()).contains("com.example.orders.api.OrderController",
                    "com.example.orders.messaging.PaymentEventListener");
        });
    }

    @Test
    void overviewShowsLayers() {
        var overview = analyzer.overview();

        assertThat(overview.applicationClasses()).contains("com.example.orders.OrderServiceApplication");
        assertThat(overview.packages()).anySatisfy(p -> {
            assertThat(p.packageName()).isEqualTo("com.example.orders.api");
            assertThat(p.stereotypes()).containsEntry("RestController", 1);
        });
    }
}
