package co.edu.javeriana.pedidos.shared.exception;

public class ProductNotFoundException extends DomainException {

    public ProductNotFoundException(Long productId) {
        super(ErrorCode.PRODUCT_NOT_FOUND, "No existe el producto con id " + productId);
    }
}
