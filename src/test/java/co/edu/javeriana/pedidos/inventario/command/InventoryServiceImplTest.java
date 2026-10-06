package co.edu.javeriana.pedidos.inventario.command;

import co.edu.javeriana.pedidos.inventario.model.InventoryReservation;
import co.edu.javeriana.pedidos.inventario.model.ReservedItem;
import co.edu.javeriana.pedidos.productos.model.Product;
import co.edu.javeriana.pedidos.shared.contract.ItemRequest;
import co.edu.javeriana.pedidos.shared.contract.ReservationResult;
import co.edu.javeriana.pedidos.shared.contract.ReservationStatus;
import co.edu.javeriana.pedidos.shared.exception.InsufficientStockException;
import co.edu.javeriana.pedidos.shared.exception.InvalidStateException;
import co.edu.javeriana.pedidos.shared.exception.ProductNotFoundException;
import co.edu.javeriana.pedidos.shared.exception.ValidationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryServiceImplTest {

    @Mock
    EntityManager em;

    @Mock
    TypedQuery<InventoryReservation> query;

    InventoryServiceImpl service;

    Product teclado;
    Product mouse;

    @BeforeEach
    void setUp() {
        service = new InventoryServiceImpl();
        service.em = em;
        teclado = new Product("Teclado", null, new BigDecimal("100"), 10);
        mouse = new Product("Mouse", null, new BigDecimal("50"), 2);
        when(em.find(Product.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(teclado);
        when(em.find(Product.class, 2L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(mouse);

        when(em.createQuery(anyString(), eq(InventoryReservation.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setLockMode(any())).thenReturn(query);
        noExistingReservation();
    }

    private void noExistingReservation() {
        when(query.getResultStream()).thenAnswer(inv -> Stream.empty());
    }

    private void existingReservation(InventoryReservation reservation) {
        when(query.getResultStream()).thenAnswer(inv -> Stream.of(reservation));
    }

    @Test
    void reservaDescuentaStockYRegistraLaReserva() {
        ReservationResult result = service.reserve(100L, List.of(new ItemRequest(1L, 3), new ItemRequest(2L, 2)));

        assertEquals(7, teclado.getStock());
        assertEquals(0, mouse.getStock());
        assertEquals(ReservationStatus.ACTIVA, result.status());
        verify(em).persist(any(InventoryReservation.class));
    }

    @Test
    void agrupaItemsRepetidosDelMismoProducto() {
        service.reserve(100L, List.of(new ItemRequest(1L, 3), new ItemRequest(1L, 4)));

        ArgumentCaptor<InventoryReservation> captor = ArgumentCaptor.forClass(InventoryReservation.class);
        verify(em).persist(captor.capture());
        assertEquals(1, captor.getValue().getItems().size());
        assertEquals(7, captor.getValue().getItems().get(0).getQuantity());
        assertEquals(3, teclado.getStock());
    }

    @Test
    void sinStockSuficienteNoDescuentaNingunProducto() {
        // El teclado alcanza, el mouse no: ninguno debe modificarse
        assertThrows(InsufficientStockException.class,
                () -> service.reserve(100L, List.of(new ItemRequest(1L, 3), new ItemRequest(2L, 5))));

        assertEquals(10, teclado.getStock());
        assertEquals(2, mouse.getStock());
        verify(em, never()).persist(any());
    }

    @Test
    void productoInexistenteOInactivoFalla() {
        assertThrows(ProductNotFoundException.class,
                () -> service.reserve(100L, List.of(new ItemRequest(99L, 1))));

        mouse.changeAvailability(false);
        assertThrows(InvalidStateException.class,
                () -> service.reserve(100L, List.of(new ItemRequest(2L, 1))));
    }

    @Test
    void rechazaPedidoVacioOCantidadInvalida() {
        assertThrows(ValidationException.class, () -> service.reserve(100L, List.of()));
        assertThrows(ValidationException.class, () -> service.reserve(100L, List.of(new ItemRequest(1L, 0))));
    }

    @Test
    void reservarDosVecesElMismoPedidoDevuelveLaReservaExistente() {
        InventoryReservation activa = new InventoryReservation(100L, List.of(new ReservedItem(1L, 3)));
        existingReservation(activa);

        ReservationResult result = service.reserve(100L, List.of(new ItemRequest(1L, 3)));

        assertEquals(ReservationStatus.ACTIVA, result.status());
        assertEquals(10, teclado.getStock());
        verify(em, never()).persist(any());
    }

    @Test
    void liberarDevuelveElStock() {
        InventoryReservation activa = new InventoryReservation(100L, List.of(new ReservedItem(1L, 3)));
        existingReservation(activa);
        teclado.decreaseStock(3);

        service.release(100L);

        assertEquals(10, teclado.getStock());
        assertEquals(ReservationStatus.LIBERADA, activa.getStatus());
    }

    @Test
    void liberarDosVecesNoDuplicaElStock() {
        InventoryReservation activa = new InventoryReservation(100L, List.of(new ReservedItem(1L, 3)));
        existingReservation(activa);
        teclado.decreaseStock(3);

        service.release(100L);
        service.release(100L);

        assertEquals(10, teclado.getStock());
    }

    @Test
    void liberarSinReservaNoHaceNada() {
        service.release(100L);
        verify(em, never()).find(eq(Product.class), any(), any(LockModeType.class));
    }

    @Test
    void noSePuedeReservarUnPedidoYaLiberado() {
        InventoryReservation liberada = new InventoryReservation(100L, List.of(new ReservedItem(1L, 3)));
        liberada.markReleased();
        existingReservation(liberada);

        assertThrows(InvalidStateException.class,
                () -> service.reserve(100L, List.of(new ItemRequest(1L, 3))));
        assertSame(ReservationStatus.LIBERADA, liberada.getStatus());
    }
}
