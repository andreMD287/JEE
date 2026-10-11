package co.edu.javeriana.pedidos.inventario.dto;

/**
 * Modelo de lectura del inventario de un producto.
 *
 * @param availableStock unidades libres para nuevos pedidos
 * @param reservedStock  unidades apartadas por pedidos en curso (reservas ACTIVA)
 * @param soldStock      unidades vendidas en pedidos confirmados (reservas CONFIRMADA)
 */
public record InventoryView(Long productId, String productName, int availableStock, long reservedStock,
                            long soldStock, boolean active) {

    public boolean available() {
        return active && availableStock > 0;
    }
}
