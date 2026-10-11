package co.edu.javeriana.pedidos.shared.contract;

public enum ReservationStatus {
    /** Stock apartado para un pedido en curso. */
    ACTIVA,
    /** El pedido se confirmo: el stock quedo vendido. */
    CONFIRMADA,
    /** Compensacion: el stock se devolvio al inventario. */
    LIBERADA
}
