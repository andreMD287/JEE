package co.edu.javeriana.pedidos.productos.model;

import co.edu.javeriana.pedidos.shared.exception.InsufficientStockException;
import co.edu.javeriana.pedidos.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductTest {

    private Product product() {
        return new Product("Teclado", "Mecanico", new BigDecimal("150000.00"), 10);
    }

    @Test
    void nuevoProductoQuedaActivoYDisponible() {
        Product p = product();
        assertTrue(p.isActive());
        assertTrue(p.isAvailable());
    }

    @Test
    void rechazaNombreVacio() {
        assertThrows(ValidationException.class, () -> new Product(" ", null, BigDecimal.TEN, 1));
    }

    @Test
    void rechazaPrecioCeroONegativo() {
        Product p = product();
        assertThrows(ValidationException.class, () -> p.changePrice(BigDecimal.ZERO));
        assertThrows(ValidationException.class, () -> p.changePrice(new BigDecimal("-1")));
        assertThrows(ValidationException.class, () -> p.changePrice(null));
    }

    @Test
    void rechazaStockNegativo() {
        assertThrows(ValidationException.class, () -> product().changeStock(-1));
    }

    @Test
    void descontarStockMayorAlDisponibleNoModificaNada() {
        Product p = product();
        assertThrows(InsufficientStockException.class, () -> p.decreaseStock(11));
        assertEquals(10, p.getStock());
    }

    @Test
    void descontarYDevolverStock() {
        Product p = product();
        p.decreaseStock(4);
        assertEquals(6, p.getStock());
        p.increaseStock(4);
        assertEquals(10, p.getStock());
    }

    @Test
    void rechazaCantidadNoPositiva() {
        Product p = product();
        assertThrows(ValidationException.class, () -> p.decreaseStock(0));
        assertThrows(ValidationException.class, () -> p.increaseStock(-2));
    }

    @Test
    void sinStockOInactivoNoEstaDisponible() {
        Product p = product();
        p.changeStock(0);
        assertFalse(p.isAvailable());

        Product q = product();
        q.changeAvailability(false);
        assertFalse(q.isAvailable());
    }
}
