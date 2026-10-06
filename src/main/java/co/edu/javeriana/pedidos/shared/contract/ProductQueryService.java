package co.edu.javeriana.pedidos.shared.contract;

public interface ProductQueryService {

    /** @throws co.edu.javeriana.pedidos.shared.exception.ProductNotFoundException si no existe */
    ProductInfo getProduct(Long productId);
}
