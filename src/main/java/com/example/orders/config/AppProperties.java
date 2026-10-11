package com.example.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Topics topics, Dynamo dynamodb) {

    public record Topics(String orderEvents, String paymentEvents) {}

    /**
     * @param endpoint    override for local/test (LocalStack); leave blank in real AWS
     * @param createTable create the table on startup; only for local/test
     */
    public record Dynamo(String region, String endpoint, String auditTable, boolean createTable) {}
}
