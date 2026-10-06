package co.edu.javeriana.pedidos.productos.query;

import co.edu.javeriana.pedidos.productos.dto.ProductView;
import co.edu.javeriana.pedidos.shared.contract.ProductInfo;
import co.edu.javeriana.pedidos.shared.contract.ProductQueryService;
import co.edu.javeriana.pedidos.shared.exception.ProductNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * Lado de lectura (CQRS). Proyecta directamente a DTO con JPQL; nunca modifica datos.
 * Implementa el contrato que consume la SAGA para consultar precios.
 */
@ApplicationScoped
@Transactional(Transactional.TxType.SUPPORTS)
public class ProductQueryServiceImpl implements ProductQueryService {

    private static final String SELECT_VIEW =
            "SELECT NEW co.edu.javeriana.pedidos.productos.dto.ProductView("
                    + "p.id, p.name, p.description, p.price, p.stock, p.active) FROM Product p";

    @PersistenceContext(unitName = "tallerPU")
    EntityManager em;

    @Override
    public ProductInfo getProduct(Long productId) {
        ProductView view = findById(productId);
        return new ProductInfo(view.id(), view.name(), view.price(), view.available());
    }

    public ProductView findById(Long productId) {
        return em.createQuery(SELECT_VIEW + " WHERE p.id = :id", ProductView.class)
                .setParameter("id", productId)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    public List<ProductView> findAll() {
        return em.createQuery(SELECT_VIEW + " ORDER BY p.id", ProductView.class).getResultList();
    }
}
