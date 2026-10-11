package com.example.orders.domain;

import java.time.Instant;

public record AuditEntry(String orderId, Instant occurredAt, String action, String detail) {}
