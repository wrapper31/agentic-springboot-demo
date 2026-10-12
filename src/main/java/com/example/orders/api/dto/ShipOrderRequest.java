package com.example.orders.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ShipOrderRequest(@NotBlank String carrier, @NotBlank String trackingNumber) {}
