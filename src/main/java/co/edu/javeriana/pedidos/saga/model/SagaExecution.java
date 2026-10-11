package co.edu.javeriana.pedidos.saga.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import co.edu.javeriana.pedidos.shared.contract.SagaStatus;
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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * Estado de la SAGA de un pedido y bitacora de todos los pasos ejecutados,
 * incluidas las compensaciones.
 */
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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "saga_steps", joinColumns = @JoinColumn(name = "saga_id"))
    @OrderColumn(name = "step_order")
    private List<SagaStep> steps = new ArrayList<>();

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
        this.createdAt = Instant.now();
        record("PEDIDO_CREADO", "Pedido registrado en estado PENDIENTE");
    }

    public void advanceTo(String step) {
        this.status = SagaStatus.IN_PROGRESS;
        record(step, null);
    }

    /** Registra un paso sin cambiar el estado de la SAGA (p. ej. una accion compensatoria). */
    public void recordStep(String step, String detail) {
        record(step, detail);
    }

    public void complete() {
        this.status = SagaStatus.COMPLETED;
        record("PEDIDO_CONFIRMADO", null);
    }

    public void startCompensation(String reason) {
        this.status = SagaStatus.COMPENSATING;
        this.failureReason = reason;
        record("COMPENSANDO", reason);
    }

    public void compensated(String reason) {
        this.status = SagaStatus.COMPENSATED;
        this.failureReason = reason;
        record("PEDIDO_CANCELADO", reason);
    }

    public void failed(String reason) {
        this.status = SagaStatus.FAILED;
        this.failureReason = reason;
        record("FALLO_TECNICO", reason);
    }

    private void record(String step, String detail) {
        this.currentStep = step;
        this.steps.add(new SagaStep(step, detail));
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

    public List<SagaStep> getSteps() {
        return List.copyOf(steps);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
