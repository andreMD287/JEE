package co.edu.javeriana.pedidos.shared.contract;

import java.util.List;

public interface InventoryService {

    /**
     * Atomica: valida todos los productos, descuenta el stock y crea la reserva en una sola
     * transaccion. Si falta stock no se modifica nada.
     *
     * @throws co.edu.javeriana.pedidos.shared.exception.InsufficientStockException sin stock suficiente
     * @throws co.edu.javeriana.pedidos.shared.exception.ProductNotFoundException producto inexistente
     */
    ReservationResult reserve(Long orderId, List<ItemRequest> items);

    /** Compensacion. Idempotente: liberar dos veces no duplica el stock. */
    void release(Long orderId);
}
