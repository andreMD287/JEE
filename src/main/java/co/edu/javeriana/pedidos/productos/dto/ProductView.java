package co.edu.javeriana.pedidos.productos.dto;

import java.math.BigDecimal;

/** Modelo de lectura (CQRS): proyeccion plana, sin entidad administrada. */
public record ProductView(Long id, String name, String description, BigDecimal price, int stock, boolean active) {

    public boolean available() {
        return active && stock > 0;
    }
}
