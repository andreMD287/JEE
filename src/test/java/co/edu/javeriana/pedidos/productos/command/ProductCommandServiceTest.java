package co.edu.javeriana.pedidos.productos.command;

import co.edu.javeriana.pedidos.productos.dto.CreateProductRequest;
import co.edu.javeriana.pedidos.productos.model.Product;
import co.edu.javeriana.pedidos.shared.exception.ProductNotFoundException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCommandServiceTest {

    @Mock
    EntityManager em;

    ProductCommandService service;

    @BeforeEach
    void setUp() {
        service = new ProductCommandService();
        service.em = em;
    }

    @Test
    void crearPersisteElProducto() {
        service.create(new CreateProductRequest("Mouse", null, new BigDecimal("50000"), 5));
        verify(em).persist(any(Product.class));
        verify(em).flush();
    }

    @Test
    void actualizarPrecioModificaLaEntidad() {
        Product p = new Product("Mouse", null, new BigDecimal("50000"), 5);
        when(em.find(Product.class, 1L)).thenReturn(p);

        service.updatePrice(1L, new BigDecimal("45000"));

        assertEquals(new BigDecimal("45000"), p.getPrice());
    }

    @Test
    void actualizarStockYDisponibilidad() {
        Product p = new Product("Mouse", null, new BigDecimal("50000"), 5);
        when(em.find(Product.class, 1L)).thenReturn(p);

        service.updateStock(1L, 20);
        service.updateAvailability(1L, false);

        assertEquals(20, p.getStock());
        assertFalse(p.isActive());
    }

    @Test
    void productoInexistenteLanzaExcepcion() {
        when(em.find(Product.class, 99L)).thenReturn(null);
        assertThrows(ProductNotFoundException.class, () -> service.updatePrice(99L, BigDecimal.TEN));
    }
}
