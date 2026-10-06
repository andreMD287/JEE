package co.edu.javeriana.pedidos.shared.contract;

import java.math.BigDecimal;

public record ProductInfo(Long id, String name, BigDecimal currentPrice, boolean available) {
}
