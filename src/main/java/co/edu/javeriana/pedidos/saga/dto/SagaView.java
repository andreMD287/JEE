package co.edu.javeriana.pedidos.saga.dto;

import java.time.Instant;

import co.edu.javeriana.pedidos.shared.contract.SagaStatus;

public record SagaView(
        Long sagaId,
        Long orderId,
        SagaStatus status,
        String currentStep,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
}