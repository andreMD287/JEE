package co.edu.javeriana.pedidos.productos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateStockRequest(@NotNull @PositiveOrZero Integer stock) {
}
