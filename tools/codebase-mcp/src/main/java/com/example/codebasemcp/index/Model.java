package com.example.codebasemcp.index;

import java.util.List;
import java.util.Map;

/** Result types returned by the MCP tools. Kept flat and JSON-friendly. */
public final class Model {

    private Model() {}

    public record Endpoint(
            String httpMethod,
            String path,
            String handler,
            String requestBody,
            List<String> pathVariables,
            List<String> queryParams,
            String returns,
            String location) {}

    public record KafkaConsumer(List<String> topics, String groupId, String handler, String payloadType, String location) {}

    public record KafkaProducer(String topicExpression, String resolvedTopic, String producer, String location) {}

    public record DeclaredTopic(String topicExpression, String resolvedTopic, String location) {}

    public record KafkaTopology(
            List<KafkaConsumer> consumers,
            List<KafkaProducer> producers,
            List<DeclaredTopic> declaredTopics,
            Map<String, String> topicConfig) {}

    public record Field(String name, String type, List<String> annotations) {}

    public record DataModel(
            String store, String type, String collectionOrTable, List<String> keys, List<Field> fields, String location) {}

    public record TypeSummary(
            String qualifiedName,
            String kind,
            List<String> annotations,
            List<String> supertypes,
            List<Field> fields,
            List<String> methods,
            List<String> usedBy,
            String location) {}

    public record PackageSummary(String packageName, int types, Map<String, Integer> stereotypes) {}

    public record Overview(
            String root,
            List<String> modules,
            List<String> applicationClasses,
            int javaFiles,
            int endpoints,
            int kafkaConsumers,
            int kafkaProducers,
            List<PackageSummary> packages) {}
}
