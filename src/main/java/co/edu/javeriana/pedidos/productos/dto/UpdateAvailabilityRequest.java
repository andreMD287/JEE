package co.edu.javeriana.pedidos.productos.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateAvailabilityRequest(@NotNull Boolean active) {
}
