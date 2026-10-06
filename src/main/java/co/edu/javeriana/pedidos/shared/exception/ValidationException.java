package co.edu.javeriana.pedidos.shared.exception;

/** Dato de negocio invalido (precio no positivo, cantidad <= 0, ...). */
public class ValidationException extends DomainException {

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }
}
