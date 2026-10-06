package co.edu.javeriana.pedidos.shared.exception;

/** Base de las excepciones de negocio. Al ser RuntimeException, @Transactional hace rollback. */
public class DomainException extends RuntimeException {

    private final ErrorCode code;

    public DomainException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
