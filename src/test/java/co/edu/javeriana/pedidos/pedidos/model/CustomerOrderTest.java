package co.edu.javeriana.pedidos.pedidos.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import co.edu.javeriana.pedidos.shared.contract.OrderStatus;
import co.edu.javeriana.pedidos.shared.exception.InvalidStateException;

class CustomerOrderTest {

    private CustomerOrder order() {
        return new CustomerOrder("CLIENTE-1", List.of(
                new OrderItem(1L, "Teclado", 2, new BigDecimal("100.50")),
                new OrderItem(2L, "Mouse", 1, new BigDecimal("50.00"))
        ));
    }

    @Test
    void nuevoPedidoQuedaPendienteConElTotalCalculado() {
        CustomerOrder order = order();
        assertEquals(OrderStatus.PENDIENTE, order.getStatus());
        assertEquals(new BigDecimal("251.00"), order.getTotalAmount());
    }

    @Test
    void recorreElCaminoExitoso() {
        CustomerOrder order = order();
        order.transitionTo(OrderStatus.INVENTARIO_RESERVADO);
        order.transitionTo(OrderStatus.PAGO_APROBADO);
        order.transitionTo(OrderStatus.CONFIRMADO);
        assertEquals(OrderStatus.CONFIRMADO, order.getStatus());
    }

    @Test
    void sePuedeCancelarDesdeCualquierEstadoIntermedio() {
        for (OrderStatus intermedio : List.of(OrderStatus.PENDIENTE, OrderStatus.INVENTARIO_RESERVADO,
                OrderStatus.PAGO_APROBADO)) {
            CustomerOrder order = order();
            avanzarHasta(order, intermedio);
            order.transitionTo(OrderStatus.CANCELADO);
            assertEquals(OrderStatus.CANCELADO, order.getStatus());
        }
    }

    @Test
    void noPermiteSaltarPasos() {
        CustomerOrder order = order();
        assertThrows(InvalidStateException.class, () -> order.transitionTo(OrderStatus.PAGO_APROBADO));
        assertThrows(InvalidStateException.class, () -> order.transitionTo(OrderStatus.CONFIRMADO));
    }

    @Test
    void losEstadosFinalesNoCambian() {
        CustomerOrder confirmado = order();
        avanzarHasta(confirmado, OrderStatus.CONFIRMADO);
        assertThrows(InvalidStateException.class, () -> confirmado.transitionTo(OrderStatus.CANCELADO));

        CustomerOrder cancelado = order();
        cancelado.transitionTo(OrderStatus.CANCELADO);
        assertThrows(InvalidStateException.class, () -> cancelado.transitionTo(OrderStatus.CONFIRMADO));
    }

    private static void avanzarHasta(CustomerOrder order, OrderStatus destino) {
        List<OrderStatus> camino = List.of(OrderStatus.INVENTARIO_RESERVADO, OrderStatus.PAGO_APROBADO,
                OrderStatus.CONFIRMADO);
        for (OrderStatus paso : camino) {
            if (order.getStatus() == destino) {
                return;
            }
            order.transitionTo(paso);
        }
    }
}
