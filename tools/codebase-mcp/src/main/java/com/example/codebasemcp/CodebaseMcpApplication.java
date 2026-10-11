package com.example.codebasemcp;

import com.example.codebasemcp.tools.CodebaseTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * MCP server (stdio) exposing structural knowledge about a Spring Boot codebase:
 * REST endpoints, Kafka topics, data models and layering. Point it at a checkout with
 * {@code --codebase.root=/path/to/repo}.
 */
@SpringBootApplication
public class CodebaseMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodebaseMcpApplication.class, args);
    }

    @Bean
    ToolCallbackProvider codebaseToolCallbacks(CodebaseTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
