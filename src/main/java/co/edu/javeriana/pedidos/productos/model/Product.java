package co.edu.javeriana.pedidos.productos.model;

import co.edu.javeriana.pedidos.shared.exception.InsufficientStockException;
import co.edu.javeriana.pedidos.shared.exception.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

/**
 * Modelo de escritura del producto. Las reglas de negocio (precio y stock validos)
 * viven aqui; las consultas usan proyecciones (ProductView) y no cargan la entidad.
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Existencias disponibles (ya descontadas las reservas activas). */
    @Column(nullable = false)
    private int stock;

    /** Disponibilidad comercial: un producto inactivo no se puede reservar. */
    @Column(nullable = false)
    private boolean active;

    /** Bloqueo optimista: detecta actualizaciones concurrentes sobre el mismo producto. */
    @Version
    private long version;

    protected Product() {
        // requerido por JPA
    }

    public Product(String name, String description, BigDecimal price, int stock) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("El nombre del producto es obligatorio");
        }
        this.name = name.trim();
        this.description = description;
        changePrice(price);
        changeStock(stock);
        this.active = true;
    }

    public void changePrice(BigDecimal newPrice) {
        if (newPrice == null || newPrice.signum() <= 0) {
            throw new ValidationException("El precio debe ser mayor que cero");
        }
        this.price = newPrice;
    }

    public void changeStock(int newStock) {
        if (newStock < 0) {
            throw new ValidationException("El stock no puede ser negativo");
        }
        this.stock = newStock;
    }

    public void changeAvailability(boolean active) {
        this.active = active;
    }

    /** Usado por inventario al reservar. */
    public void decreaseStock(int quantity) {
        requirePositive(quantity);
        if (quantity > stock) {
            throw new InsufficientStockException(id, quantity, stock);
        }
        this.stock -= quantity;
    }

    /** Usado por inventario al liberar una reserva (compensacion). */
    public void increaseStock(int quantity) {
        requirePositive(quantity);
        this.stock += quantity;
    }

    public boolean isAvailable() {
        return active && stock > 0;
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new ValidationException("La cantidad debe ser mayor que cero");
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
