package com.example.orders.api.dto;

import com.example.orders.domain.AuditEntry;
import java.time.Instant;

public record AuditEntryResponse(Instant occurredAt, String action, String detail) {

    public static AuditEntryResponse from(AuditEntry entry) {
        return new AuditEntryResponse(entry.occurredAt(), entry.action(), entry.detail());
    }
}
