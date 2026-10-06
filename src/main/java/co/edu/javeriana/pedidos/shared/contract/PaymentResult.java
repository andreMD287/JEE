package co.edu.javeriana.pedidos.shared.contract;

import java.math.BigDecimal;

public record PaymentResult(Long paymentId, Long orderId, BigDecimal amount, PaymentStatus status) {
}
