package co.edu.javeriana.pedidos.inventario.query;

import co.edu.javeriana.pedidos.inventario.dto.InventoryView;
import co.edu.javeriana.pedidos.inventario.dto.ReservationView;
import co.edu.javeriana.pedidos.inventario.model.InventoryReservation;
import co.edu.javeriana.pedidos.shared.contract.ReservationStatus;
import co.edu.javeriana.pedidos.shared.exception.DomainException;
import co.edu.javeriana.pedidos.shared.exception.ErrorCode;
import co.edu.javeriana.pedidos.shared.exception.ProductNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lado de lectura del inventario (CQRS). Solo consultas; nunca modifica datos. */
@ApplicationScoped
@Transactional(Transactional.TxType.SUPPORTS)
public class InventoryQueryService {

    @PersistenceContext(unitName = "tallerPU")
    EntityManager em;

    /** Inventario disponible de todos los productos, con lo reservado en reservas activas. */
    public List<InventoryView> findAll() {
        Map<Long, Long> reserved = reservedByProduct();
        return em.createQuery("SELECT p.id, p.name, p.stock, p.active FROM Product p ORDER BY p.id", Object[].class)
                .getResultList()
                .stream()
                .map(row -> toView(row, reserved))
                .toList();
    }

    public InventoryView findByProduct(Long productId) {
        Map<Long, Long> reserved = reservedByProduct();
        return em.createQuery("SELECT p.id, p.name, p.stock, p.active FROM Product p WHERE p.id = :id", Object[].class)
                .setParameter("id", productId)
                .getResultStream()
                .findFirst()
                .map(row -> toView(row, reserved))
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    public ReservationView findReservationByOrder(Long orderId) {
        InventoryReservation r = em.createQuery(
                        "SELECT r FROM InventoryReservation r WHERE r.orderId = :orderId", InventoryReservation.class)
                .setParameter("orderId", orderId)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.RESERVATION_NOT_FOUND,
                        "No existe reserva para el pedido " + orderId));
        List<ReservationView.Item> items = r.getItems().stream()
                .map(i -> new ReservationView.Item(i.getProductId(), i.getQuantity()))
                .toList();
        return new ReservationView(r.getId(), r.getOrderId(), r.getStatus(), items, r.getCreatedAt(), r.getReleasedAt());
    }

    private Map<Long, Long> reservedByProduct() {
        Map<Long, Long> reserved = new HashMap<>();
        em.createQuery("SELECT i.productId, SUM(i.quantity) FROM InventoryReservation r JOIN r.items i "
                        + "WHERE r.status = :status GROUP BY i.productId", Object[].class)
                .setParameter("status", ReservationStatus.ACTIVA)
                .getResultList()
                .forEach(row -> reserved.put((Long) row[0], ((Number) row[1]).longValue()));
        return reserved;
    }

    private static InventoryView toView(Object[] row, Map<Long, Long> reserved) {
        Long id = (Long) row[0];
        return new InventoryView(id, (String) row[1], (Integer) row[2], reserved.getOrDefault(id, 0L), (Boolean) row[3]);
    }
}
