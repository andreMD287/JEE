package co.edu.javeriana.pedidos.productos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateStockRequest(
        @NotNull(message = "es obligatorio")
        @PositiveOrZero(message = "no puede ser negativo")
        Integer stock) {
}
