package co.edu.javeriana.pedidos.inventario.model;

import co.edu.javeriana.pedidos.shared.contract.ReservationStatus;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Reserva de inventario de un pedido. Hay como maximo una por pedido (order_id unico). */
@Entity
@Table(name = "inventory_reservations")
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reservation_items", joinColumns = @JoinColumn(name = "reservation_id"))
    private List<ReservedItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected InventoryReservation() {
        // requerido por JPA
    }

    public InventoryReservation(Long orderId, List<ReservedItem> items) {
        this.orderId = orderId;
        this.items = new ArrayList<>(items);
        this.status = ReservationStatus.ACTIVA;
        this.createdAt = Instant.now();
    }

    public boolean isActive() {
        return status == ReservationStatus.ACTIVA;
    }

    public void markReleased() {
        this.status = ReservationStatus.LIBERADA;
        this.releasedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public List<ReservedItem> getItems() {
        return List.copyOf(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }
}
