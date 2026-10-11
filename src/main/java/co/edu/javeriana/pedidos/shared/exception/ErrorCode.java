package co.edu.javeriana.pedidos.shared.exception;

/** Codigos publicos de la API; desacoplan la respuesta HTTP de los nombres de clase Java. */
public enum ErrorCode {
    PRODUCT_NOT_FOUND(404),
    INSUFFICIENT_STOCK(409),
    RESERVATION_NOT_FOUND(404),
    PAYMENT_NOT_FOUND(404),
    PAYMENT_REJECTED(402),
    ORDER_NOT_FOUND(404),
    SAGA_NOT_FOUND(404),
    INVALID_STATE(409),
    VALIDATION_ERROR(400),
    CONFIRMATION_FAILED(500),
    COMPENSATION_FAILED(500),

    // Errores del protocolo HTTP (ruta, metodo o cuerpo invalidos)
    MALFORMED_REQUEST(400),
    RESOURCE_NOT_FOUND(404),
    METHOD_NOT_ALLOWED(405),
    NOT_ACCEPTABLE(406),
    UNSUPPORTED_MEDIA_TYPE(415),

    INTERNAL_ERROR(500);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
