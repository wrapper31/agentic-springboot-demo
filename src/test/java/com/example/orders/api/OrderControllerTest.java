package com.example.orders.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.orders.domain.Order;
import com.example.orders.domain.OrderItem;
import com.example.orders.domain.OrderStatus;
import com.example.orders.service.OrderNotFoundException;
import com.example.orders.service.OrderService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OrderService orderService;

    @Test
    void createReturns201WithTotal() throws Exception {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        var order = new Order("o-1", "c-1", List.of(new OrderItem("SKU-1", 3, new BigDecimal("2.50"))),
                OrderStatus.CREATED, now, now);
        when(orderService.create(eq("c-1"), any())).thenReturn(order);

        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"c-1","items":[{"sku":"SKU-1","quantity":3,"unitPrice":2.50}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("o-1"))
                .andExpect(jsonPath("$.total").value(7.5))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void createRejectsEmptyItems() throws Exception {
        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"c-1\",\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownOrderReturns404() throws Exception {
        when(orderService.get("missing")).thenThrow(new OrderNotFoundException("missing"));

        mvc.perform(get("/api/orders/missing")).andExpect(status().isNotFound());
    }
}
