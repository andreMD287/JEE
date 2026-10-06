package co.edu.javeriana.pedidos.shared.exception;

/** Codigos publicos de la API; desacoplan la respuesta HTTP de los nombres de clase Java. */
public enum ErrorCode {
    PRODUCT_NOT_FOUND(404),
    INSUFFICIENT_STOCK(409),
    RESERVATION_NOT_FOUND(404),
    PAYMENT_REJECTED(402),
    ORDER_NOT_FOUND(404),
    INVALID_STATE(409),
    VALIDATION_ERROR(400),
    COMPENSATION_FAILED(500),
    INTERNAL_ERROR(500);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
