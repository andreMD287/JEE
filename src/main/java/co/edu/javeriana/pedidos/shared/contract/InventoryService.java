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

    /**
     * Ultimo paso del pedido: la reserva pasa a CONFIRMADA (el stock queda vendido).
     * Idempotente: confirmar dos veces no cambia nada.
     *
     * @throws co.edu.javeriana.pedidos.shared.exception.InvalidStateException si ya fue liberada
     */
    void confirm(Long orderId);

    /**
     * Compensacion: devuelve el stock de una reserva ACTIVA o CONFIRMADA.
     * Idempotente: liberar dos veces, o sin reserva, no duplica el stock.
     */
    void release(Long orderId);
}
