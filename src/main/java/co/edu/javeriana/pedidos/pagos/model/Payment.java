package co.edu.javeriana.pedidos.pagos.model;

import java.math.BigDecimal;
import java.time.Instant;

import co.edu.javeriana.pedidos.shared.contract.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    protected Payment() {
        // requerido por JPA
    }

    public Payment(Long orderId, BigDecimal amount) {
        this.orderId = orderId;
        this.amount = amount;
        this.status = PaymentStatus.PENDIENTE;
        this.createdAt = Instant.now();
    }

    public void approve() {
        this.status = PaymentStatus.APROBADO;
        this.processedAt = Instant.now();
    }

    public void reject() {
        this.status = PaymentStatus.RECHAZADO;
        this.processedAt = Instant.now();
    }

    /**
     * Revierte el pago solamente si fue aprobado.
     * Si ya estaba reversado o fue rechazado, no modifica nada.
     */
    public void reverse() {
        if (status == PaymentStatus.APROBADO) {
            this.status = PaymentStatus.REVERSADO;
            this.reversedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }
}