package co.edu.javeriana.pedidos.saga.dto;

import java.util.List;

import co.edu.javeriana.pedidos.shared.contract.ItemRequest;

public record ProcessOrderCommand(
        String customerId,
        List<ItemRequest> items,
        boolean simulatePaymentFailure,
        boolean simulateConfirmationFailure
) {
    public ProcessOrderCommand {
        items = items == null ? List.of() : List.copyOf(items);
    }
}