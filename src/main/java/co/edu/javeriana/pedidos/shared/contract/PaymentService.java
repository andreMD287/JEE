package co.edu.javeriana.pedidos.shared.contract;

import java.math.BigDecimal;

public interface PaymentService {

    /** Un pago rechazado devuelve estado RECHAZADO; no lanza excepcion. */
    PaymentResult process(Long orderId, BigDecimal amount, boolean simulateFailure);

    /** Compensacion. Idempotente: reversar dos veces no genera dos reversiones. */
    void reverse(Long orderId);
}
