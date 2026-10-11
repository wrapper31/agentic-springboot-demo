package com.example.orders.messaging;

/** Consumed from {@code app.topics.payment-events}; produced by the (external) payment service. */
public record PaymentEvent(String orderId, Result result, String reason) {

    public enum Result {
        SUCCEEDED,
        FAILED
    }
}
