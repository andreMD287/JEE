package co.edu.javeriana.pedidos.shared.contract;

public record ReservationResult(Long reservationId, Long orderId, ReservationStatus status) {
}
