package co.edu.javeriana.pedidos.pagos.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** DTO usado solamente para probar el módulo de pagos de forma aislada. */
public record ProcessPaymentRequest(
        @NotNull(message = "es obligatorio") Long orderId,

        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0.01", message = "debe ser mayor que cero")
        BigDecimal amount,

        boolean simulateFailure
) {
}