package co.edu.javeriana.pedidos.inventario.dto;

import co.edu.javeriana.pedidos.shared.contract.ItemRequest;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Solo para probar el modulo de forma aislada; en el flujo real la reserva la pide la SAGA. */
public record ReserveRequest(@NotNull(message = "es obligatorio") Long orderId,
                             @NotEmpty(message = "debe contener al menos un producto") List<ItemRequest> items) {
}
