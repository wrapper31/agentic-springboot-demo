package com.example.orders.api;

import com.example.orders.api.dto.AuditEntryResponse;
import com.example.orders.api.dto.CreateOrderRequest;
import com.example.orders.api.dto.OrderResponse;
import com.example.orders.domain.OrderItem;
import com.example.orders.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        List<OrderItem> items = request.items().stream()
                .map(i -> new OrderItem(i.sku(), i.quantity(), i.unitPrice()))
                .toList();
        return OrderResponse.from(orderService.create(request.customerId(), items));
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable String orderId) {
        return OrderResponse.from(orderService.get(orderId));
    }

    @GetMapping
    public List<OrderResponse> findByCustomer(@RequestParam String customerId) {
        return orderService.findByCustomer(customerId).stream().map(OrderResponse::from).toList();
    }

    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancel(@PathVariable String orderId) {
        return OrderResponse.from(orderService.cancel(orderId));
    }

    @GetMapping("/{orderId}/history")
    public List<AuditEntryResponse> history(@PathVariable String orderId) {
        return orderService.history(orderId).stream().map(AuditEntryResponse::from).toList();
    }
}
