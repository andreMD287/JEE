package co.edu.javeriana.pedidos.pagos.command;

import java.math.BigDecimal;
import java.util.Optional;

import co.edu.javeriana.pedidos.pagos.model.Payment;
import co.edu.javeriana.pedidos.shared.contract.PaymentResult;
import co.edu.javeriana.pedidos.shared.contract.PaymentService;
import co.edu.javeriana.pedidos.shared.exception.InvalidStateException;
import co.edu.javeriana.pedidos.shared.exception.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

/**
 * Lado de escritura del modulo de pagos.
 * Cada operación constituye un paso local independiente de la SAGA.
 */
@ApplicationScoped
public class PaymentServiceImpl implements PaymentService {

    @PersistenceContext(unitName = "tallerPU")
    EntityManager em;

    /**
     * Procesa un pago de forma idempotente.
     * Un rechazo es un resultado de negocio y no lanza una excepción.
     */
    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public PaymentResult process(
            Long orderId,
            BigDecimal amount,
            boolean simulateFailure
    ) {
        validate(orderId, amount);

        Optional<Payment> existing =
                findByOrder(orderId, LockModeType.PESSIMISTIC_WRITE);

        if (existing.isPresent()) {
            Payment payment = existing.get();

            if (payment.getAmount().compareTo(amount) != 0) {
                throw new InvalidStateException(
                        "El pedido " + orderId
                                + " ya tiene un pago registrado con otro valor"
                );
            }

            return toResult(payment);
        }

        Payment payment = new Payment(orderId, amount);

        if (simulateFailure) {
            payment.reject();
        } else {
            payment.approve();
        }

        em.persist(payment);
        em.flush();

        return toResult(payment);
    }

    /**
     * Compensación idempotente:
     * - Si no existe pago, no hace nada.
     * - Si está rechazado o reversado, no hace nada.
     * - Si está aprobado, lo marca como reversado.
     */
    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void reverse(Long orderId) {
        if (orderId == null) {
            throw new ValidationException(
                    "El id del pedido es obligatorio para reversar el pago"
            );
        }

        Optional<Payment> found =
                findByOrder(orderId, LockModeType.PESSIMISTIC_WRITE);

        if (found.isEmpty()) {
            return;
        }

        found.get().reverse();
    }

    private void validate(Long orderId, BigDecimal amount) {
        if (orderId == null) {
            throw new ValidationException(
                    "El id del pedido es obligatorio"
            );
        }

        if (amount == null) {
            throw new ValidationException(
                    "El valor del pago es obligatorio"
            );
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "El valor del pago debe ser mayor que cero"
            );
        }
    }

    private Optional<Payment> findByOrder(
            Long orderId,
            LockModeType lockMode
    ) {
        return em.createQuery(
                        "SELECT p FROM Payment p WHERE p.orderId = :orderId",
                        Payment.class
                )
                .setParameter("orderId", orderId)
                .setLockMode(lockMode)
                .getResultStream()
                .findFirst();
    }

    private static PaymentResult toResult(Payment payment) {
        return new PaymentResult(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getStatus()
        );
    }
}