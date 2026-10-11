package co.edu.javeriana.pedidos.saga.orchestrator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InOrder;
import org.mockito.Mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.javeriana.pedidos.pedidos.command.OrderCommandService;
import co.edu.javeriana.pedidos.pedidos.dto.OrderView;
import co.edu.javeriana.pedidos.pedidos.query.OrderQueryService;
import co.edu.javeriana.pedidos.saga.dto.ProcessOrderCommand;
import co.edu.javeriana.pedidos.shared.contract.InventoryService;
import co.edu.javeriana.pedidos.shared.contract.ItemRequest;
import co.edu.javeriana.pedidos.shared.contract.OrderStatus;
import co.edu.javeriana.pedidos.shared.contract.PaymentResult;
import co.edu.javeriana.pedidos.shared.contract.PaymentService;
import co.edu.javeriana.pedidos.shared.contract.PaymentStatus;
import co.edu.javeriana.pedidos.shared.exception.DomainException;
import co.edu.javeriana.pedidos.shared.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorTest {

    private static final Long ORDER_ID = 10L;
    private static final BigDecimal TOTAL = new BigDecimal("150000.00");

    @Mock
    OrderCommandService orders;

    @Mock
    OrderQueryService orderQueries;

    @Mock
    InventoryService inventory;

    @Mock
    PaymentService payments;

    OrderSagaOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new OrderSagaOrchestrator();
        orchestrator.orders = orders;
        orchestrator.orderQueries = orderQueries;
        orchestrator.inventory = inventory;
        orchestrator.payments = payments;
    }

    @Test
    void shouldCompleteSagaWhenEveryStepSucceeds() {
        ProcessOrderCommand command = command(false, false);
        OrderView pending = order(OrderStatus.INVENTARIO_RESERVADO);
        OrderView confirmed = order(OrderStatus.CONFIRMADO);

        when(orders.create(any())).thenReturn(ORDER_ID);
        when(orderQueries.findById(ORDER_ID))
                .thenReturn(pending, confirmed);
        when(payments.process(ORDER_ID, TOTAL, false))
                .thenReturn(new PaymentResult(
                        1L,
                        ORDER_ID,
                        TOTAL,
                        PaymentStatus.APROBADO
                ));

        OrderView result = orchestrator.process(command);

        assertEquals(OrderStatus.CONFIRMADO, result.status());

        InOrder sequence = inOrder(orders, inventory, payments);
        sequence.verify(orders).create(any());
        sequence.verify(inventory).reserve(ORDER_ID, command.items());
        sequence.verify(orders).markInventoryReserved(ORDER_ID);
        sequence.verify(payments).process(ORDER_ID, TOTAL, false);
        sequence.verify(orders).markPaymentApproved(ORDER_ID);
        sequence.verify(inventory).confirm(ORDER_ID);
        sequence.verify(orders).confirm(ORDER_ID);

        verify(inventory, never()).release(ORDER_ID);
        verify(payments, never()).reverse(ORDER_ID);
    }

    @Test
    void shouldReleaseInventoryWhenPaymentIsRejected() {
        ProcessOrderCommand command = command(true, false);

        when(orders.create(any())).thenReturn(ORDER_ID);
        when(orderQueries.findById(ORDER_ID))
                .thenReturn(order(OrderStatus.INVENTARIO_RESERVADO));
        when(payments.process(ORDER_ID, TOTAL, true))
                .thenReturn(new PaymentResult(
                        1L,
                        ORDER_ID,
                        TOTAL,
                        PaymentStatus.RECHAZADO
                ));

        DomainException exception = assertThrows(
                DomainException.class,
                () -> orchestrator.process(command)
        );

        assertEquals(ErrorCode.PAYMENT_REJECTED, exception.getCode());

        verify(orders).startCompensation(
                ORDER_ID,
                "El pago del pedido 10 fue rechazado"
        );
        verify(inventory).release(ORDER_ID);
        verify(orders).completeCompensation(
                ORDER_ID,
                "El pago del pedido 10 fue rechazado"
        );
        verify(payments, never()).reverse(ORDER_ID);
        verify(orders, never()).confirm(ORDER_ID);
    }

    @Test
    void shouldReversePaymentAndReleaseInventoryWhenConfirmationFails() {
        ProcessOrderCommand command = command(false, true);

        when(orders.create(any())).thenReturn(ORDER_ID);
        when(orderQueries.findById(ORDER_ID))
                .thenReturn(order(OrderStatus.INVENTARIO_RESERVADO));
        when(payments.process(ORDER_ID, TOTAL, false))
                .thenReturn(new PaymentResult(
                        1L,
                        ORDER_ID,
                        TOTAL,
                        PaymentStatus.APROBADO
                ));

        DomainException exception = assertThrows(
                DomainException.class,
                () -> orchestrator.process(command)
        );

        assertEquals(ErrorCode.CONFIRMATION_FAILED, exception.getCode());

        verify(orders).markPaymentApproved(ORDER_ID);
        verify(orders).startCompensation(
                ORDER_ID,
                "Fallo simulado al confirmar el pedido 10"
        );
        InOrder compensation = inOrder(orders, payments, inventory);
        compensation.verify(payments).reverse(ORDER_ID);
        compensation.verify(orders).recordSagaStep(ORDER_ID, "PAGO_REVERSADO", null);
        compensation.verify(inventory).release(ORDER_ID);
        compensation.verify(orders).recordSagaStep(ORDER_ID, "INVENTARIO_LIBERADO", null);
        compensation.verify(orders).completeCompensation(
                ORDER_ID,
                "Fallo simulado al confirmar el pedido 10"
        );
        verify(inventory, never()).confirm(ORDER_ID);
        verify(orders, never()).confirm(ORDER_ID);
    }

    @Test
    void shouldCancelOrderWithoutReleasingWhenInventoryReservationFails() {
        ProcessOrderCommand command = command(false, false);

        when(orders.create(any())).thenReturn(ORDER_ID);

        DomainException stockFailure = new DomainException(
                ErrorCode.INSUFFICIENT_STOCK,
                "No hay inventario suficiente"
        );

        when(inventory.reserve(ORDER_ID, command.items()))
                .thenThrow(stockFailure);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> orchestrator.process(command)
        );

        assertEquals(ErrorCode.INSUFFICIENT_STOCK, exception.getCode());

        verify(orders).startCompensation(
                ORDER_ID,
                "No hay inventario suficiente"
        );
        verify(orders).completeCompensation(
                ORDER_ID,
                "No hay inventario suficiente"
        );
        verify(inventory, never()).release(ORDER_ID);
        verify(payments, never()).process(
                any(),
                any(),
                any(Boolean.class)
        );
    }

    @Test
    void shouldMarkSagaFailedWhenCompensationFails() {
        ProcessOrderCommand command = command(true, false);

        when(orders.create(any())).thenReturn(ORDER_ID);
        when(orderQueries.findById(ORDER_ID))
                .thenReturn(order(OrderStatus.INVENTARIO_RESERVADO));
        when(payments.process(ORDER_ID, TOTAL, true))
                .thenReturn(new PaymentResult(
                        1L,
                        ORDER_ID,
                        TOTAL,
                        PaymentStatus.RECHAZADO
                ));

        RuntimeException releaseFailure =
                new RuntimeException("No fue posible liberar inventario");

        org.mockito.Mockito.doThrow(releaseFailure)
                .when(inventory)
                .release(ORDER_ID);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> orchestrator.process(command)
        );

        assertEquals(ErrorCode.COMPENSATION_FAILED, exception.getCode());

        verify(orders).failSaga(
                ORDER_ID,
                "Falló la compensación: No fue posible liberar inventario"
        );
        verify(orders, never()).completeCompensation(
                any(),
                any()
        );
    }

    @Test
    void shouldReleaseConfirmedReservationWhenOrderConfirmationFails() {
        ProcessOrderCommand command = command(false, false);

        when(orders.create(any())).thenReturn(ORDER_ID);
        when(orderQueries.findById(ORDER_ID))
                .thenReturn(order(OrderStatus.INVENTARIO_RESERVADO));
        when(payments.process(ORDER_ID, TOTAL, false))
                .thenReturn(new PaymentResult(
                        1L,
                        ORDER_ID,
                        TOTAL,
                        PaymentStatus.APROBADO
                ));
        org.mockito.Mockito.doThrow(new DomainException(
                        ErrorCode.INVALID_STATE,
                        "No se pudo confirmar"
                ))
                .when(orders)
                .confirm(ORDER_ID);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> orchestrator.process(command)
        );

        assertEquals(ErrorCode.INVALID_STATE, exception.getCode());
        verify(inventory).confirm(ORDER_ID);
        verify(payments).reverse(ORDER_ID);
        verify(inventory).release(ORDER_ID);
        verify(orders).completeCompensation(ORDER_ID, "No se pudo confirmar");
    }

    @Test
    void shouldNotTreatStepLogFailureAsCompensationFailure() {
        ProcessOrderCommand command = command(true, false);

        when(orders.create(any())).thenReturn(ORDER_ID);
        when(orderQueries.findById(ORDER_ID))
                .thenReturn(order(OrderStatus.INVENTARIO_RESERVADO));
        when(payments.process(ORDER_ID, TOTAL, true))
                .thenReturn(new PaymentResult(
                        1L,
                        ORDER_ID,
                        TOTAL,
                        PaymentStatus.RECHAZADO
                ));
        org.mockito.Mockito.doThrow(new RuntimeException("bitacora no disponible"))
                .when(orders)
                .recordSagaStep(any(), any(), any());

        DomainException exception = assertThrows(
                DomainException.class,
                () -> orchestrator.process(command)
        );

        assertEquals(ErrorCode.PAYMENT_REJECTED, exception.getCode());
        verify(orders).completeCompensation(
                ORDER_ID,
                "El pago del pedido 10 fue rechazado"
        );
        verify(orders, never()).failSaga(any(), any());
    }

    private ProcessOrderCommand command(
            boolean paymentFailure,
            boolean confirmationFailure
    ) {
        return new ProcessOrderCommand(
                "CLIENTE-123",
                List.of(new ItemRequest(1L, 2)),
                paymentFailure,
                confirmationFailure
        );
    }

    private OrderView order(OrderStatus status) {
        Instant now = Instant.now();

        return new OrderView(
                ORDER_ID,
                "CLIENTE-123",
                status,
                List.of(),
                TOTAL,
                now,
                now
        );
    }
}