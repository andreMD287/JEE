package co.edu.javeriana.pedidos.pedidos.dto;

import java.math.BigDecimal;

public record OrderItemView(
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
}