package co.edu.javeriana.pedidos.productos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 150, message = "admite máximo 150 caracteres")
        String name,

        @Size(max = 500, message = "admite máximo 500 caracteres")
        String description,

        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0.0", inclusive = false, message = "debe ser mayor que cero")
        @Digits(integer = 10, fraction = 2, message = "admite máximo 10 enteros y 2 decimales")
        BigDecimal price,

        @NotNull(message = "es obligatorio")
        @PositiveOrZero(message = "no puede ser negativo")
        Integer stock) {
}
