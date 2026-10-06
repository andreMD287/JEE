package co.edu.javeriana.pedidos.shared.exception;

public class InsufficientStockException extends DomainException {

    public InsufficientStockException(Long productId, int requested, int available) {
        super(ErrorCode.INSUFFICIENT_STOCK,
                "Inventario insuficiente para el producto " + productId
                        + " (solicitado " + requested + ", disponible " + available + ")");
    }
}
