package co.edu.javeriana.pedidos.shared.exception;

public class InvalidStateException extends DomainException {

    public InvalidStateException(String message) {
        super(ErrorCode.INVALID_STATE, message);
    }
}
