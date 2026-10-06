package co.edu.javeriana.pedidos.shared.contract;

public enum SagaStatus {
    IN_PROGRESS,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    FAILED
}
