package co.edu.javeriana.pedidos.saga.dto;

import java.time.Instant;
import java.util.List;

import co.edu.javeriana.pedidos.shared.contract.SagaStatus;

public record SagaView(
        Long sagaId,
        Long orderId,
        SagaStatus status,
        String currentStep,
        String failureReason,
        List<Step> steps,
        Instant createdAt,
        Instant updatedAt
) {
    public SagaView {
        steps = List.copyOf(steps);
    }

    public record Step(String step, String detail, Instant executedAt) {
    }
}
