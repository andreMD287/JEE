package co.edu.javeriana.pedidos.api.resource;

import java.util.List;

import co.edu.javeriana.pedidos.pagos.dto.PaymentView;
import co.edu.javeriana.pedidos.pagos.dto.ProcessPaymentRequest;
import co.edu.javeriana.pedidos.pagos.query.PaymentQueryService;
import co.edu.javeriana.pedidos.shared.contract.PaymentResult;
import co.edu.javeriana.pedidos.shared.contract.PaymentService;
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

/**
 * Consulta de pagos.
 * POST y DELETE existen para probar el módulo aisladamente.
 * En una compra real, la SAGA invoca PaymentService internamente.
 */
@Path("/payments")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentResource {

    @Inject
    PaymentService payments;

    @Inject
    PaymentQueryService queries;

    @GET
    public List<PaymentView> list() {
        return queries.findAll();
    }

    @POST
    public Response process(
            @Valid @NotNull ProcessPaymentRequest request
    ) {
        PaymentResult result = payments.process(
                request.orderId(),
                request.amount(),
                request.simulateFailure()
        );

        return Response.status(Response.Status.CREATED)
                .entity(result)
                .build();
    }

    @DELETE
    @Path("/{orderId}")
    public Response reverse(
            @PathParam("orderId") Long orderId
    ) {
        payments.reverse(orderId);
        return Response.noContent().build();
    }
}