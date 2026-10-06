package co.edu.javeriana.pedidos.productos.command;

import co.edu.javeriana.pedidos.productos.dto.CreateProductRequest;
import co.edu.javeriana.pedidos.productos.model.Product;
import co.edu.javeriana.pedidos.shared.exception.ProductNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;

/** Lado de escritura (CQRS). Cada metodo es una transaccion JTA administrada por el contenedor. */
@ApplicationScoped
@Transactional
public class ProductCommandService {

    @PersistenceContext(unitName = "tallerPU")
    EntityManager em;

    public Long create(CreateProductRequest request) {
        Product product = new Product(request.name(), request.description(), request.price(), request.stock());
        em.persist(product);
        em.flush(); // IDENTITY: obliga a generar el id antes de devolverlo
        return product.getId();
    }

    public void updatePrice(Long productId, BigDecimal price) {
        find(productId).changePrice(price);
    }

    public void updateStock(Long productId, int stock) {
        find(productId).changeStock(stock);
    }

    public void updateAvailability(Long productId, boolean active) {
        find(productId).changeAvailability(active);
    }

    private Product find(Long productId) {
        Product product = em.find(Product.class, productId);
        if (product == null) {
            throw new ProductNotFoundException(productId);
        }
        return product;
    }
}
