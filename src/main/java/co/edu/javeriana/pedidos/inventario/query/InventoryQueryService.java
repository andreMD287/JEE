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

    private static final String SELECT_PRODUCT = "SELECT p.id, p.name, p.stock, p.active FROM Product p";

    private static final String SUM_BY_PRODUCT =
            "SELECT i.productId, r.status, SUM(i.quantity) FROM InventoryReservation r JOIN r.items i "
                    + "WHERE r.status IN :statuses";

    @PersistenceContext(unitName = "tallerPU")
    EntityManager em;

    /** Inventario de todos los productos: disponible, reservado por pedidos en curso y vendido. */
    public List<InventoryView> findAll() {
        Map<Long, Map<ReservationStatus, Long>> totals = totalsByProduct(null);
        return em.createQuery(SELECT_PRODUCT + " ORDER BY p.id", Object[].class)
                .getResultList()
                .stream()
                .map(row -> toView(row, totals))
                .toList();
    }

    public InventoryView findByProduct(Long productId) {
        Object[] row = em.createQuery(SELECT_PRODUCT + " WHERE p.id = :id", Object[].class)
                .setParameter("id", productId)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return toView(row, totalsByProduct(productId));
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
        return new ReservationView(r.getId(), r.getOrderId(), r.getStatus(), items,
                r.getCreatedAt(), r.getConfirmedAt(), r.getReleasedAt());
    }

    /** Suma de unidades por producto y estado de reserva (ACTIVA y CONFIRMADA). */
    private Map<Long, Map<ReservationStatus, Long>> totalsByProduct(Long productId) {
        String jpql = SUM_BY_PRODUCT + (productId != null ? " AND i.productId = :productId" : "")
                + " GROUP BY i.productId, r.status";
        var query = em.createQuery(jpql, Object[].class)
                .setParameter("statuses", List.of(ReservationStatus.ACTIVA, ReservationStatus.CONFIRMADA));
        if (productId != null) {
            query.setParameter("productId", productId);
        }
        Map<Long, Map<ReservationStatus, Long>> totals = new HashMap<>();
        for (Object[] row : query.getResultList()) {
            totals.computeIfAbsent((Long) row[0], id -> new HashMap<>())
                    .put((ReservationStatus) row[1], ((Number) row[2]).longValue());
        }
        return totals;
    }

    private static InventoryView toView(Object[] row, Map<Long, Map<ReservationStatus, Long>> totals) {
        Long id = (Long) row[0];
        Map<ReservationStatus, Long> byStatus = totals.getOrDefault(id, Map.of());
        return new InventoryView(id, (String) row[1], (Integer) row[2],
                byStatus.getOrDefault(ReservationStatus.ACTIVA, 0L),
                byStatus.getOrDefault(ReservationStatus.CONFIRMADA, 0L),
                (Boolean) row[3]);
    }
}
