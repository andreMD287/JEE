package co.edu.javeriana.pedidos.pedidos.query;

import java.util.List;

import co.edu.javeriana.pedidos.pedidos.dto.OrderItemView;
import co.edu.javeriana.pedidos.pedidos.dto.OrderView;
import co.edu.javeriana.pedidos.pedidos.exception.OrderNotFoundException;
import co.edu.javeriana.pedidos.pedidos.model.CustomerOrder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional(Transactional.TxType.SUPPORTS)
public class OrderQueryService {

    @PersistenceContext(unitName = "tallerPU")
    EntityManager em;

    public List<OrderView> findAll() {
        return em.createQuery(
                        """
                        SELECT DISTINCT o
                        FROM CustomerOrder o
                        ORDER BY o.id
                        """,
                        CustomerOrder.class
                )
                .getResultList()
                .stream()
                .map(this::toView)
                .toList();
    }

    public OrderView findById(Long orderId) {
        CustomerOrder order = em.find(CustomerOrder.class, orderId);

        if (order == null) {
            throw new OrderNotFoundException(orderId);
        }

        return toView(order);
    }

    private OrderView toView(CustomerOrder order) {
        List<OrderItemView> items = order.getItems()
                .stream()
                .map(item -> new OrderItemView(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.subtotal()
                ))
                .toList();

        return new OrderView(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                items,
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}