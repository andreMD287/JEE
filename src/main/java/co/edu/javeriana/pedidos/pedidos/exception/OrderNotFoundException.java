package co.edu.javeriana.pedidos.pedidos.exception;

import co.edu.javeriana.pedidos.shared.exception.DomainException;
import co.edu.javeriana.pedidos.shared.exception.ErrorCode;

public class OrderNotFoundException extends DomainException {

    public OrderNotFoundException(Long orderId) {
        super(
                ErrorCode.ORDER_NOT_FOUND,
                "No existe el pedido con id " + orderId
        );
    }
}