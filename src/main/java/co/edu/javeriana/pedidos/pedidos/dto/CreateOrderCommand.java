package co.edu.javeriana.pedidos.pedidos.dto;

import java.util.List;

import co.edu.javeriana.pedidos.shared.contract.ItemRequest;

public record CreateOrderCommand(
        String customerId,
        List<ItemRequest> items
) {
    public CreateOrderCommand {
        items = items == null ? List.of() : List.copyOf(items);
    }
}