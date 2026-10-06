package co.edu.javeriana.pedidos.inventario.dto;

/**
 * Modelo de lectura del inventario de un producto.
 *
 * @param availableStock unidades libres para nuevos pedidos
 * @param reservedStock  unidades comprometidas en reservas activas
 */
public record InventoryView(Long productId, String productName, int availableStock, long reservedStock,
                            boolean active) {

    public boolean available() {
        return active && availableStock > 0;
    }
}
