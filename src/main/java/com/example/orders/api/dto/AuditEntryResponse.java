package com.example.orders.api.dto;

import com.example.orders.persistence.dynamo.OrderAuditRecord;

public record AuditEntryResponse(String occurredAt, String action, String detail) {

    public static AuditEntryResponse from(OrderAuditRecord record) {
        return new AuditEntryResponse(record.getOccurredAt(), record.getAction(), record.getDetail());
    }
}
