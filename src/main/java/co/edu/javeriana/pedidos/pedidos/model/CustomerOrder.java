package co.edu.javeriana.pedidos.pedidos.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import co.edu.javeriana.pedidos.shared.contract.OrderStatus;
import co.edu.javeriana.pedidos.shared.exception.InvalidStateException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "customer_orders")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "order_items",
            joinColumns = @JoinColumn(name = "order_id")
    )
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected CustomerOrder() {
    }

    public CustomerOrder(String customerId, List<OrderItem> items) {
        this.customerId = customerId;
        this.items = new ArrayList<>(items);
        this.status = OrderStatus.PENDIENTE;
        this.totalAmount = items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void transitionTo(OrderStatus newStatus) {
        if (!isValidTransition(status, newStatus)) {
            throw new InvalidStateException(
                    "No se puede cambiar el pedido de "
                            + status + " a " + newStatus
            );
        }

        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    private static boolean isValidTransition(
            OrderStatus current,
            OrderStatus next
    ) {
        return switch (current) {
            case PENDIENTE ->
                    next == OrderStatus.INVENTARIO_RESERVADO
                            || next == OrderStatus.CANCELADO;

            case INVENTARIO_RESERVADO ->
                    next == OrderStatus.PAGO_APROBADO
                            || next == OrderStatus.CANCELADO;

            case PAGO_APROBADO ->
                    next == OrderStatus.CONFIRMADO
                            || next == OrderStatus.CANCELADO;

            case CONFIRMADO, CANCELADO -> false;
        };
    }

    public Long getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}