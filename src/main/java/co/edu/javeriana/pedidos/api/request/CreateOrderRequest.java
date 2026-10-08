package co.edu.javeriana.pedidos.api.request;

import java.util.List;

import co.edu.javeriana.pedidos.shared.contract.ItemRequest;

public record CreateOrderRequest(
        String customerId,
        List<ItemRequest> items,
        boolean simulatePaymentFailure,
        boolean simulateConfirmationFailure
) {
}