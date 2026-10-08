package co.edu.javeriana.pedidos.api.resource;

import java.util.List;

import co.edu.javeriana.pedidos.saga.dto.SagaView;
import co.edu.javeriana.pedidos.saga.query.SagaQueryService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/sagas")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SagaResource {

    @Inject
    SagaQueryService queries;

    @GET
    public List<SagaView> list() {
        return queries.findAll();
    }

    @GET
    @Path("/order/{orderId}")
    public SagaView getByOrder(
            @PathParam("orderId") Long orderId
    ) {
        return queries.findByOrderId(orderId);
    }
}