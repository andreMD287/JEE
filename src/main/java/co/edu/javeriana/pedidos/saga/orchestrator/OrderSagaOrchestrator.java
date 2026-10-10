package co.edu.javeriana.pedidos.saga.orchestrator;

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

@ApplicationScoped
@Transactional(Transactional.TxType.NOT_SUPPORTED)
public class OrderSagaOrchestrator {

    @Inject
    OrderCommandService orders;

    @Inject
    OrderQueryService orderQueries;

    @Inject
    InventoryService inventory;

    @Inject
    PaymentService payments;

    public OrderView process(ProcessOrderCommand command) {
        Long orderId = orders.create(new CreateOrderCommand(
                command.customerId(),
                command.items()
        ));

        boolean inventoryReserved = false;
        boolean paymentApproved = false;

        try {
            inventory.reserve(orderId, command.items());
            inventoryReserved = true;

            orders.markInventoryReserved(orderId);

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

            if (command.simulateConfirmationFailure()) {
                throw new DomainException(
                        ErrorCode.INTERNAL_ERROR,
                        "Fallo simulado al confirmar el pedido " + orderId
                );
            }

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
                throw compensationFailure;
            }

            if (originalFailure instanceof DomainException domainFailure) {
                throw domainFailure;
            }

            throw new DomainException(
                    ErrorCode.INTERNAL_ERROR,
                    "No fue posible procesar el pedido " + orderId
            );
        }
    }

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
            } catch (RuntimeException exception) {
                failure = remember(failure, exception);
            }
        }

        if (inventoryReserved) {
            try {
                inventory.release(orderId);
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