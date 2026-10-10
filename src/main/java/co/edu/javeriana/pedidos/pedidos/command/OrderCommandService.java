package co.edu.javeriana.pedidos.pedidos.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import co.edu.javeriana.pedidos.pedidos.dto.CreateOrderCommand;
import co.edu.javeriana.pedidos.pedidos.exception.OrderNotFoundException;
import co.edu.javeriana.pedidos.pedidos.model.CustomerOrder;
import co.edu.javeriana.pedidos.pedidos.model.OrderItem;
import co.edu.javeriana.pedidos.saga.model.SagaExecution;
import co.edu.javeriana.pedidos.shared.contract.ItemRequest;
import co.edu.javeriana.pedidos.shared.contract.OrderStatus;
import co.edu.javeriana.pedidos.shared.contract.ProductInfo;
import co.edu.javeriana.pedidos.shared.contract.ProductQueryService;
import co.edu.javeriana.pedidos.shared.contract.SagaStatus;
import co.edu.javeriana.pedidos.shared.exception.DomainException;
import co.edu.javeriana.pedidos.shared.exception.ErrorCode;
import co.edu.javeriana.pedidos.shared.exception.InvalidStateException;
import co.edu.javeriana.pedidos.shared.exception.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRES_NEW)
public class OrderCommandService {

    @PersistenceContext(unitName = "tallerPU")
    EntityManager em;

    @Inject
    ProductQueryService products;

    public Long create(CreateOrderCommand command) {
        validate(command);

        List<OrderItem> orderItems = new ArrayList<>();

        for (ItemRequest requestedItem : command.items()) {
            ProductInfo product = products.getProduct(requestedItem.productId());

            if (!product.available()) {
                throw new InvalidStateException(
                        "El producto " + product.id()
                                + " no está disponible"
                );
            }

            orderItems.add(new OrderItem(
                    product.id(),
                    product.name(),
                    requestedItem.quantity(),
                    product.currentPrice()
            ));
        }

        CustomerOrder order = new CustomerOrder(
                command.customerId().trim(),
                orderItems
        );

        em.persist(order);
        em.flush();

        SagaExecution saga = new SagaExecution(order.getId());
        em.persist(saga);
        em.flush();

        return order.getId();
    }

    public void markInventoryReserved(Long orderId) {
        changeOrderAndSaga(
                orderId,
                OrderStatus.INVENTARIO_RESERVADO,
                "INVENTARIO_RESERVADO"
        );
    }

    public void markPaymentApproved(Long orderId) {
        changeOrderAndSaga(
                orderId,
                OrderStatus.PAGO_APROBADO,
                "PAGO_APROBADO"
        );
    }

    public void confirm(Long orderId) {
        CustomerOrder order = lockOrder(orderId);
        SagaExecution saga = lockSaga(orderId);

        if (order.getStatus() == OrderStatus.CONFIRMADO
                && saga.getStatus() == SagaStatus.COMPLETED) {
            return;
        }

        order.transitionTo(OrderStatus.CONFIRMADO);
        saga.complete();
    }

    public void startCompensation(Long orderId, String reason) {
        SagaExecution saga = lockSaga(orderId);

        if (saga.getStatus() == SagaStatus.COMPENSATING) {
            return;
        }

        saga.startCompensation(reason);
    }

    public void completeCompensation(Long orderId, String reason) {
        CustomerOrder order = lockOrder(orderId);
        SagaExecution saga = lockSaga(orderId);

        if (order.getStatus() == OrderStatus.CANCELADO
                && saga.getStatus() == SagaStatus.COMPENSATED) {
            return;
        }

        if (order.getStatus() != OrderStatus.CANCELADO) {
            order.transitionTo(OrderStatus.CANCELADO);
        }

        saga.compensated(reason);
    }

    public void failSaga(Long orderId, String reason) {
        SagaExecution saga = lockSaga(orderId);

        if (saga.getStatus() == SagaStatus.FAILED) {
            return;
        }

        saga.failed(reason);
    }

    private void changeOrderAndSaga(
            Long orderId,
            OrderStatus targetStatus,
            String sagaStep
    ) {
        CustomerOrder order = lockOrder(orderId);
        SagaExecution saga = lockSaga(orderId);

        if (order.getStatus() == targetStatus) {
            return;
        }

        order.transitionTo(targetStatus);
        saga.advanceTo(sagaStep);
    }

    private CustomerOrder lockOrder(Long orderId) {
        CustomerOrder order = em.find(
                CustomerOrder.class,
                orderId,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (order == null) {
            throw new OrderNotFoundException(orderId);
        }

        return order;
    }

    private SagaExecution lockSaga(Long orderId) {
        return em.createQuery(
                        """
                        SELECT s
                        FROM SagaExecution s
                        WHERE s.orderId = :orderId
                        """,
                        SagaExecution.class
                )
                .setParameter("orderId", orderId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new DomainException(
                        ErrorCode.INTERNAL_ERROR,
                        "No existe la SAGA del pedido " + orderId
                ));
    }

    private void validate(CreateOrderCommand command) {
        if (command == null) {
            throw new ValidationException(
                    "La información del pedido es obligatoria"
            );
        }

        if (command.customerId() == null
                || command.customerId().isBlank()) {
            throw new ValidationException(
                    "La identificación del cliente es obligatoria"
            );
        }

        if (command.items().isEmpty()) {
            throw new ValidationException(
                    "El pedido debe contener al menos un producto"
            );
        }

        Set<Long> productIds = new HashSet<>();

        for (ItemRequest item : command.items()) {
            if (item == null || item.productId() == null) {
                throw new ValidationException(
                        "Todos los productos deben tener identificador"
                );
            }

            if (item.quantity() <= 0) {
                throw new ValidationException(
                        "La cantidad del producto "
                                + item.productId()
                                + " debe ser mayor que cero"
                );
            }

            if (!productIds.add(item.productId())) {
                throw new ValidationException(
                        "El producto " + item.productId()
                                + " está repetido en el pedido"
                );
            }
        }
    }
}