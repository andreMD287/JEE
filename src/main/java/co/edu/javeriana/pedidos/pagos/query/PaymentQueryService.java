package co.edu.javeriana.pedidos.pagos.query;

import java.util.List;

import co.edu.javeriana.pedidos.pagos.dto.PaymentView;
import co.edu.javeriana.pedidos.pagos.model.Payment;
import co.edu.javeriana.pedidos.shared.exception.DomainException;
import co.edu.javeriana.pedidos.shared.exception.ErrorCode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

/** Lado de lectura de pagos (CQRS). Solo consulta; nunca modifica datos. */
@ApplicationScoped
@Transactional(Transactional.TxType.SUPPORTS)
public class PaymentQueryService {

    @PersistenceContext(unitName = "tallerPU")
    EntityManager em;

    public List<PaymentView> findAll() {
        return em.createQuery(
                        "SELECT p FROM Payment p ORDER BY p.id",
                        Payment.class
                )
                .getResultList()
                .stream()
                .map(PaymentQueryService::toView)
                .toList();
    }

    public PaymentView findByOrderId(Long orderId) {
        return em.createQuery(
                        "SELECT p FROM Payment p WHERE p.orderId = :orderId",
                        Payment.class
                )
                .setParameter("orderId", orderId)
                .getResultStream()
                .findFirst()
                .map(PaymentQueryService::toView)
                .orElseThrow(() -> new DomainException(
                        ErrorCode.PAYMENT_NOT_FOUND,
                        "No existe un pago para el pedido " + orderId
                ));
    }

    private static PaymentView toView(Payment payment) {
        return new PaymentView(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getCreatedAt(),
                payment.getProcessedAt(),
                payment.getReversedAt()
        );
    }
}