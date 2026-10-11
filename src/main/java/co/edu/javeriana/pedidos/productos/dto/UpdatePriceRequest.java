package co.edu.javeriana.pedidos.productos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdatePriceRequest(
        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0.0", inclusive = false, message = "debe ser mayor que cero")
        @Digits(integer = 10, fraction = 2, message = "admite máximo 10 enteros y 2 decimales")
        BigDecimal price) {
}
