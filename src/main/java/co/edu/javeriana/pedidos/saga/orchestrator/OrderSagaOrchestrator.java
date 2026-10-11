package co.edu.javeriana.pedidos.saga.orchestrator;

import java.util.logging.Level;
import java.util.logging.Logger;

import co.edu.javeriana.pedidos.pedidos.command.OrderCommandService;
import co.edu.javeriana.pedidos.pedidos.dto.CreateOrderCommand;
import co.edu.javeriana.pedidos.pedidos.dto.OrderView;
import co.edu.javeriana.pedidos.pedidos.query.OrderQueryService;
import co.edu.javeriana.pedidos.saga.dto.ProcessOrderCommand;
import co.edu.javeriana.pedidos.shared.contract.InventoryService;
import co.edu.javeriana.pedidos.shared.contract.PaymentResult;
import co.edu.javeriana.pedidos.shared.contract.PaymentService;
import co.edu.javeriana.pedidos.shared.contract.PaymentStatus;
import co.edu.javeriana.pedidos.shared.exception.DomainException;
import co.edu.javeriana.pedidos.shared.exception.ErrorCode;
import co.edu.javeriana.pedidos.shared.exception.InvalidStateException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Orquestador de la SAGA de compra.
 *
 * <p>No abre una transaccion global (NOT_SUPPORTED): cada paso que invoca corre en su propia
 * transaccion JTA local (REQUIRES_NEW). Si un paso falla, ejecuta las compensaciones de los
 * pasos ya confirmados en orden inverso: reversar pago, liberar inventario, cancelar pedido.
 */
@ApplicationScoped
@Transactional(Transactional.TxType.NOT_SUPPORTED)
public class OrderSagaOrchestrator {

    private static final Logger LOGGER =
            Logger.getLogger(OrderSagaOrchestrator.class.getName());

    @Inject
    OrderCommandService orders;

    @Inject
    OrderQueryService orderQueries;

    @Inject
    InventoryService inventory;

    @Inject
    PaymentService payments;

    public OrderView process(ProcessOrderCommand command) {
        // Paso 1: consulta precios y registra el pedido PENDIENTE junto con su SAGA (misma transaccion)
        Long orderId = orders.create(new CreateOrderCommand(
                command.customerId(),
                command.items()
        ));

        boolean inventoryReserved = false;
        boolean paymentApproved = false;

        try {
            // Paso 2: reservar inventario
            inventory.reserve(orderId, command.items());
            inventoryReserved = true;
            orders.markInventoryReserved(orderId);

            // Paso 3: procesar el pago por el total calculado con los precios guardados
            OrderView order = orderQueries.findById(orderId);
            PaymentResult payment = payments.process(
                    orderId,
                    order.totalAmount(),
                    command.simulatePaymentFailure()
            );

            if (payment.status() == PaymentStatus.RECHAZADO) {
                throw new DomainException(
                        ErrorCode.PAYMENT_REJECTED,
                        "El pago del pedido " + orderId + " fue rechazado"
                );
            }

            if (payment.status() != PaymentStatus.APROBADO) {
                throw new InvalidStateException(
                        "El pago del pedido " + orderId
                                + " terminó en estado " + payment.status()
                );
            }

            paymentApproved = true;
            orders.markPaymentApproved(orderId);

            // Paso 4: confirmar el pedido (la reserva queda vendida)
            if (command.simulateConfirmationFailure()) {
                throw new DomainException(
                        ErrorCode.CONFIRMATION_FAILED,
                        "Fallo simulado al confirmar el pedido " + orderId
                );
            }

            inventory.confirm(orderId);
            orders.confirm(orderId);

            return orderQueries.findById(orderId);

        } catch (RuntimeException originalFailure) {
            RuntimeException compensationFailure = compensate(
                    orderId,
                    inventoryReserved,
                    paymentApproved,
                    originalFailure.getMessage()
            );

            if (compensationFailure != null) {
                compensationFailure.addSuppressed(originalFailure);
                throw compensationFailure;
            }

            if (originalFailure instanceof DomainException domainFailure) {
                throw domainFailure;
            }

            LOGGER.log(Level.SEVERE, "Fallo inesperado en la SAGA del pedido " + orderId, originalFailure);
            throw new DomainException(
                    ErrorCode.INTERNAL_ERROR,
                    "No fue posible procesar el pedido " + orderId
            );
        }
    }

    /**
     * Ejecuta las compensaciones en orden inverso. Cada una es idempotente y corre en su propia
     * transaccion; si alguna falla se intentan las demas y la SAGA termina en FAILED.
     */
    private RuntimeException compensate(
            Long orderId,
            boolean inventoryReserved,
            boolean paymentApproved,
            String reason
    ) {
        RuntimeException failure = null;

        try {
            orders.startCompensation(orderId, reason);
        } catch (RuntimeException exception) {
            failure = remember(failure, exception);
        }

        if (paymentApproved) {
            try {
                payments.reverse(orderId);
                recordStep(orderId, "PAGO_REVERSADO");
            } catch (RuntimeException exception) {
                failure = remember(failure, exception);
            }
        }

        if (inventoryReserved) {
            try {
                inventory.release(orderId);
                recordStep(orderId, "INVENTARIO_LIBERADO");
            } catch (RuntimeException exception) {
                failure = remember(failure, exception);
            }
        }

        if (failure == null) {
            try {
                orders.completeCompensation(orderId, reason);
            } catch (RuntimeException exception) {
                failure = remember(failure, exception);
            }
        }

        if (failure != null) {
            try {
                orders.failSaga(
                        orderId,
                        "Falló la compensación: " + failure.getMessage()
                );
            } catch (RuntimeException exception) {
                failure.addSuppressed(exception);
            }

            LOGGER.log(Level.SEVERE, "No fue posible compensar el pedido " + orderId, failure);
            DomainException compensationException = new DomainException(
                    ErrorCode.COMPENSATION_FAILED,
                    "No fue posible compensar completamente el pedido "
                            + orderId
            );
            compensationException.addSuppressed(failure);
            return compensationException;
        }

        return null;
    }

    /** La bitacora es informativa: si no se puede escribir, la compensacion no se considera fallida. */
    private void recordStep(Long orderId, String step) {
        try {
            orders.recordSagaStep(orderId, step, null);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "No se pudo registrar el paso " + step + " del pedido " + orderId, exception);
        }
    }

    private RuntimeException remember(
            RuntimeException current,
            RuntimeException next
    ) {
        if (current == null) {
            return next;
        }

        current.addSuppressed(next);
        return current;
    }
}
