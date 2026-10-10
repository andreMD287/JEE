package co.edu.javeriana.pedidos.api.resource;

import java.net.URI;
import java.util.List;

import co.edu.javeriana.pedidos.api.request.CreateOrderRequest;
import co.edu.javeriana.pedidos.pedidos.dto.OrderView;
import co.edu.javeriana.pedidos.pedidos.query.OrderQueryService;
import co.edu.javeriana.pedidos.saga.dto.ProcessOrderCommand;
import co.edu.javeriana.pedidos.saga.orchestrator.OrderSagaOrchestrator;
import co.edu.javeriana.pedidos.shared.exception.ValidationException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Path("/orders")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    @Inject
    OrderSagaOrchestrator orchestrator;

    @Inject
    OrderQueryService queries;

    @Context
    UriInfo uriInfo;

    @GET
    public List<OrderView> list() {
        return queries.findAll();
    }

    @GET
    @Path("/{orderId}")
    public OrderView get(@PathParam("orderId") Long orderId) {
        return queries.findById(orderId);
    }

    @POST
    public Response create(CreateOrderRequest request) {
        if (request == null) {
            throw new ValidationException(
                    "La información del pedido es obligatoria"
            );
        }

        ProcessOrderCommand command = new ProcessOrderCommand(
                request.customerId(),
                request.items(),
                request.simulatePaymentFailure(),
                request.simulateConfirmationFailure()
        );

        OrderView order = orchestrator.process(command);

        URI location = uriInfo.getAbsolutePathBuilder()
                .path(order.orderId().toString())
                .build();

        return Response.created(location)
                .entity(order)
                .build();
    }
}