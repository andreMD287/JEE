package co.edu.javeriana.pedidos.pedidos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import co.edu.javeriana.pedidos.shared.contract.OrderStatus;

public record OrderView(
        Long orderId,
        String customerId,
        OrderStatus status,
        List<OrderItemView> items,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt
) {
    public OrderView {
        items = List.copyOf(items);
    }
}