package co.edu.javeriana.pedidos.pagos.dto;

import java.math.BigDecimal;
import java.time.Instant;

import co.edu.javeriana.pedidos.shared.contract.PaymentStatus;

/** Modelo de lectura de un pago. */
public record PaymentView(
        Long paymentId,
        Long orderId,
        BigDecimal amount,
        PaymentStatus status,
        Instant createdAt,
        Instant processedAt,
        Instant reversedAt
) {
}