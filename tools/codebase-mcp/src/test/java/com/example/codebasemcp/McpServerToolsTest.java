package com.example.codebasemcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"codebase.root=../..", "spring.ai.mcp.server.enabled=false"})
class McpServerToolsTest {

    @Autowired
    ToolCallbackProvider tools;

    @Test
    void registersAllToolsAndAnswersOverJson() {
        ToolCallback[] callbacks = tools.getToolCallbacks();

        assertThat(Arrays.stream(callbacks).map(c -> c.getToolDefinition().name())).containsExactlyInAnyOrder(
                "project_overview", "list_rest_endpoints", "list_kafka_topics", "list_data_models", "describe_type");

        ToolCallback endpoints = Arrays.stream(callbacks)
                .filter(c -> c.getToolDefinition().name().equals("list_rest_endpoints"))
                .findFirst()
                .orElseThrow();
        assertThat(endpoints.call("{\"pathContains\":\"/cancel\"}")).contains("OrderController#cancel");
    }
}
