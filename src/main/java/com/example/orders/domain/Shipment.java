package com.example.orders.domain;

import java.time.Instant;

public record Shipment(String carrier, String trackingNumber, Instant shippedAt) {}
