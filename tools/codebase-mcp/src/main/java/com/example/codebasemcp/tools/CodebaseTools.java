package com.example.codebasemcp.tools;

import com.example.codebasemcp.index.CodebaseAnalyzer;
import com.example.codebasemcp.index.Model.DataModel;
import com.example.codebasemcp.index.Model.Endpoint;
import com.example.codebasemcp.index.Model.KafkaTopology;
import com.example.codebasemcp.index.Model.Overview;
import com.example.codebasemcp.index.Model.TypeSummary;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** The MCP tool surface. Descriptions are written for the calling model: say when to use each tool. */
@Component
public class CodebaseTools {

    private final CodebaseAnalyzer analyzer;

    public CodebaseTools(CodebaseAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    @Tool(name = "project_overview", description = """
            Start here. Summarizes the Spring Boot codebase: modules, @SpringBootApplication classes, and every \
            package with its type count and Spring stereotypes (RestController, Service, Repository, Document, ...). \
            Use it to learn the layering before reading files.""")
    public Overview projectOverview() {
        return analyzer.overview();
    }

    @Tool(name = "list_rest_endpoints", description = """
            Lists every REST endpoint: HTTP method, full path (class + method mapping, placeholders resolved), \
            handler Class#method, request body type, path variables, query params, return type and file:line. \
            Use it to find where an API is implemented or to check a new path does not clash.""")
    public List<Endpoint> listRestEndpoints(
            @ToolParam(required = false, description = "Only endpoints whose path contains this text, e.g. /orders")
                    String pathContains) {
        return analyzer.endpoints(pathContains);
    }

    @Tool(name = "list_kafka_topics", description = """
            Maps the Kafka topology: @KafkaListener consumers (topics resolved from application.yml, group id, \
            payload type), KafkaTemplate.send producers (topic expression and resolved name), topics declared with \
            TopicBuilder, and all topic-related config keys. Use it to trace how events flow between services.""")
    public KafkaTopology listKafkaTopics() {
        return analyzer.kafka();
    }

    @Tool(name = "list_data_models", description = """
            Lists persistence models: MongoDB @Document (collection, id fields), DynamoDB @DynamoDbBean (table, \
            partition/sort keys) and JPA @Entity, with their fields. Set includeRecords=true to also list Java \
            records (DTOs, events, domain types).""")
    public List<DataModel> listDataModels(
            @ToolParam(required = false, description = "Also include plain Java records; default false")
                    Boolean includeRecords) {
        return analyzer.dataModels(Boolean.TRUE.equals(includeRecords));
    }

    @Tool(name = "describe_type", description = """
            Describes one class, record, interface or enum by simple or fully qualified name: annotations, \
            supertypes, fields, non-private method signatures, file:line, and which other types use it. \
            Use it to understand a type and its blast radius before changing it.""")
    public List<TypeSummary> describeType(
            @ToolParam(description = "Simple name (OrderService) or fully qualified name") String name) {
        return analyzer.describeType(name);
    }
}
