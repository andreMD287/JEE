package co.edu.javeriana.pedidos.inventario.command;

import co.edu.javeriana.pedidos.inventario.model.InventoryReservation;
import co.edu.javeriana.pedidos.inventario.model.ReservedItem;
import co.edu.javeriana.pedidos.productos.model.Product;
import co.edu.javeriana.pedidos.shared.contract.InventoryService;
import co.edu.javeriana.pedidos.shared.contract.ItemRequest;
import co.edu.javeriana.pedidos.shared.contract.ReservationResult;
import co.edu.javeriana.pedidos.shared.exception.InsufficientStockException;
import co.edu.javeriana.pedidos.shared.exception.InvalidStateException;
import co.edu.javeriana.pedidos.shared.exception.ProductNotFoundException;
import co.edu.javeriana.pedidos.shared.exception.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Lado de escritura del inventario (CQRS). Cada operacion es un paso local de la SAGA y corre
 * en su propia transaccion JTA (REQUIRES_NEW): si falla, el contenedor hace rollback de todo
 * lo que hizo ese paso y nada mas.
 */
@ApplicationScoped
public class InventoryServiceImpl implements InventoryService {

    @PersistenceContext(unitName = "tallerPU")
    EntityManager em;

    /**
     * Reserva atomica: bloquea los productos (SELECT ... FOR UPDATE), valida todo el pedido y solo
     * entonces descuenta stock y registra la reserva. Si falla cualquier producto no se modifica nada.
     * Idempotente: reservar de nuevo el mismo pedido devuelve la reserva activa existente.
     */
    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public ReservationResult reserve(Long orderId, List<ItemRequest> items) {
        if (orderId == null) {
            throw new ValidationException("El id del pedido es obligatorio");
        }
        Map<Long, Integer> quantities = groupByProduct(items);

        Optional<InventoryReservation> existing = findByOrder(orderId, LockModeType.NONE);
        if (existing.isPresent()) {
            InventoryReservation reservation = existing.get();
            if (!reservation.isActive()) {
                throw new InvalidStateException("La reserva del pedido " + orderId + " ya fue liberada");
            }
            return toResult(reservation);
        }

        // Orden ascendente de ids: todas las transacciones bloquean en el mismo orden (evita deadlocks)
        Map<Long, Product> locked = new TreeMap<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Long productId = entry.getKey();
            Product product = em.find(Product.class, productId, LockModeType.PESSIMISTIC_WRITE);
            if (product == null) {
                throw new ProductNotFoundException(productId);
            }
            if (!product.isActive()) {
                throw new InvalidStateException("El producto " + productId + " no esta disponible para la venta");
            }
            if (product.getStock() < entry.getValue()) {
                throw new InsufficientStockException(productId, entry.getValue(), product.getStock());
            }
            locked.put(productId, product);
        }

        List<ReservedItem> reserved = new ArrayList<>();
        locked.forEach((productId, product) -> {
            int quantity = quantities.get(productId);
            product.decreaseStock(quantity);
            reserved.add(new ReservedItem(productId, quantity));
        });

        InventoryReservation reservation = new InventoryReservation(orderId, reserved);
        em.persist(reservation);
        em.flush();
        return toResult(reservation);
    }

    /**
     * Compensacion: devuelve el stock reservado. Idempotente: si no hay reserva o ya fue liberada,
     * no hace nada, asi la SAGA puede invocarla sin conocer el estado previo.
     */
    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void release(Long orderId) {
        Optional<InventoryReservation> found = findByOrder(orderId, LockModeType.PESSIMISTIC_WRITE);
        if (found.isEmpty() || !found.get().isActive()) {
            return;
        }
        InventoryReservation reservation = found.get();

        reservation.getItems().stream()
                .sorted((a, b) -> a.getProductId().compareTo(b.getProductId()))
                .forEach(item -> {
                    Product product = em.find(Product.class, item.getProductId(), LockModeType.PESSIMISTIC_WRITE);
                    if (product == null) {
                        throw new ProductNotFoundException(item.getProductId());
                    }
                    product.increaseStock(item.getQuantity());
                });
        reservation.markReleased();
    }

    private Map<Long, Integer> groupByProduct(List<ItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new ValidationException("El pedido debe tener al menos un producto");
        }
        Map<Long, Integer> quantities = new TreeMap<>();
        for (ItemRequest item : items) {
            if (item == null || item.productId() == null) {
                throw new ValidationException("Cada item debe indicar el producto");
            }
            if (item.quantity() <= 0) {
                throw new ValidationException("La cantidad del producto " + item.productId() + " debe ser mayor que cero");
            }
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }
        return quantities;
    }

    private Optional<InventoryReservation> findByOrder(Long orderId, LockModeType lock) {
        return em.createQuery(
                        "SELECT r FROM InventoryReservation r WHERE r.orderId = :orderId", InventoryReservation.class)
                .setParameter("orderId", orderId)
                .setLockMode(lock)
                .getResultStream()
                .findFirst();
    }

    private static ReservationResult toResult(InventoryReservation reservation) {
        return new ReservationResult(reservation.getId(), reservation.getOrderId(), reservation.getStatus());
    }
}
