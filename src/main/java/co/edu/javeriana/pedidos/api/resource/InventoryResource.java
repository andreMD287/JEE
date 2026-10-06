package co.edu.javeriana.pedidos.api.resource;

import co.edu.javeriana.pedidos.inventario.dto.InventoryView;
import co.edu.javeriana.pedidos.inventario.dto.ReservationView;
import co.edu.javeriana.pedidos.inventario.dto.ReserveRequest;
import co.edu.javeriana.pedidos.inventario.query.InventoryQueryService;
import co.edu.javeriana.pedidos.shared.contract.InventoryService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Consultas de inventario. POST/DELETE de reservas existen para probar el modulo aislado;
 * en el flujo de compra la reserva y la liberacion las invoca la SAGA via InventoryService.
 */
@Path("/inventory")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InventoryResource {

    @Inject
    InventoryService inventory;

    @Inject
    InventoryQueryService queries;

    @GET
    public List<InventoryView> list() {
        return queries.findAll();
    }

    @GET
    @Path("/{productId}")
    public InventoryView get(@PathParam("productId") Long productId) {
        return queries.findByProduct(productId);
    }

    @GET
    @Path("/reservations/{orderId}")
    public ReservationView reservation(@PathParam("orderId") Long orderId) {
        return queries.findReservationByOrder(orderId);
    }

    @POST
    @Path("/reservations")
    public Response reserve(@Valid @NotNull ReserveRequest request) {
        inventory.reserve(request.orderId(), request.items());
        return Response.status(Response.Status.CREATED)
                .entity(queries.findReservationByOrder(request.orderId()))
                .build();
    }

    @DELETE
    @Path("/reservations/{orderId}")
    public Response release(@PathParam("orderId") Long orderId) {
        inventory.release(orderId);
        return Response.noContent().build();
    }
}
