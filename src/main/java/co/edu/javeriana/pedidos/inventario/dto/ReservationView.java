package co.edu.javeriana.pedidos.inventario.dto;

import co.edu.javeriana.pedidos.shared.contract.ReservationStatus;

import java.time.Instant;
import java.util.List;

public record ReservationView(Long id, Long orderId, ReservationStatus status, List<Item> items,
                              Instant createdAt, Instant releasedAt) {

    public record Item(Long productId, int quantity) {
    }
}
