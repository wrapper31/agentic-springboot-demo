package com.example.orders.domain;

import java.math.BigDecimal;

public record OrderItem(String sku, int quantity, BigDecimal unitPrice) {

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
