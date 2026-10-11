package com.example.orders.persistence.dynamo;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * One row per order state change. Partition key = orderId, sort key = ISO-8601 timestamp,
 * so a query on orderId returns the full history in order.
 */
@DynamoDbBean
public class OrderAuditRecord {

    private String orderId;
    private String occurredAt;
    private String action;
    private String detail;

    public OrderAuditRecord() {}

    public OrderAuditRecord(String orderId, String occurredAt, String action, String detail) {
        this.orderId = orderId;
        this.occurredAt = occurredAt;
        this.action = action;
        this.detail = detail;
    }

    @DynamoDbPartitionKey
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    @DynamoDbSortKey
    public String getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
