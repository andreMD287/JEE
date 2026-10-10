package co.edu.javeriana.pedidos.saga.query;

import java.util.List;

import co.edu.javeriana.pedidos.saga.dto.SagaView;
import co.edu.javeriana.pedidos.saga.model.SagaExecution;
import co.edu.javeriana.pedidos.shared.exception.DomainException;
import co.edu.javeriana.pedidos.shared.exception.ErrorCode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional(Transactional.TxType.SUPPORTS)
public class SagaQueryService {

    @PersistenceContext(unitName = "tallerPU")
    EntityManager em;

    public List<SagaView> findAll() {
        return em.createQuery(
                        """
                        SELECT s
                        FROM SagaExecution s
                        ORDER BY s.id
                        """,
                        SagaExecution.class
                )
                .getResultList()
                .stream()
                .map(this::toView)
                .toList();
    }

    public SagaView findByOrderId(Long orderId) {
        return em.createQuery(
                        """
                        SELECT s
                        FROM SagaExecution s
                        WHERE s.orderId = :orderId
                        """,
                        SagaExecution.class
                )
                .setParameter("orderId", orderId)
                .getResultStream()
                .findFirst()
                .map(this::toView)
                .orElseThrow(() -> new DomainException(
                        ErrorCode.ORDER_NOT_FOUND,
                        "No existe una SAGA para el pedido " + orderId
                ));
    }

    private SagaView toView(SagaExecution saga) {
        return new SagaView(
                saga.getId(),
                saga.getOrderId(),
                saga.getStatus(),
                saga.getCurrentStep(),
                saga.getFailureReason(),
                saga.getCreatedAt(),
                saga.getUpdatedAt()
        );
    }
}