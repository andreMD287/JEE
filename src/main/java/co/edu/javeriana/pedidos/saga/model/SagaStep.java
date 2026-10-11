package co.edu.javeriana.pedidos.saga.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Un paso ejecutado por la SAGA (avance o compensacion), con su hora. */
@Embeddable
public class SagaStep {

    @Column(name = "step", nullable = false, length = 80)
    private String step;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    protected SagaStep() {
    }

    public SagaStep(String step, String detail) {
        this.step = step;
        this.detail = detail;
        this.executedAt = Instant.now();
    }

    public String getStep() {
        return step;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }
}
