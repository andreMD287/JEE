package co.edu.javeriana.pedidos.saga.model;

import java.time.Instant;

import co.edu.javeriana.pedidos.shared.contract.SagaStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "saga_executions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_saga_order",
                columnNames = "order_id"
        )
)
public class SagaExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SagaStatus status;

    @Column(name = "current_step", nullable = false, length = 80)
    private String currentStep;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected SagaExecution() {
    }

    public SagaExecution(Long orderId) {
        this.orderId = orderId;
        this.status = SagaStatus.IN_PROGRESS;
        this.currentStep = "PEDIDO_CREADO";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void advanceTo(String step) {
        this.status = SagaStatus.IN_PROGRESS;
        this.currentStep = step;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        this.status = SagaStatus.COMPLETED;
        this.currentStep = "PEDIDO_CONFIRMADO";
        this.updatedAt = Instant.now();
    }

    public void startCompensation(String reason) {
        this.status = SagaStatus.COMPENSATING;
        this.currentStep = "COMPENSANDO";
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void compensated(String reason) {
        this.status = SagaStatus.COMPENSATED;
        this.currentStep = "PEDIDO_CANCELADO";
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void failed(String reason) {
        this.status = SagaStatus.FAILED;
        this.currentStep = "FALLO_TECNICO";
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public SagaStatus getStatus() {
        return status;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}