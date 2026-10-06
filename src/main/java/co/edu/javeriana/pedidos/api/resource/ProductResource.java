package co.edu.javeriana.pedidos.api.resource;

import co.edu.javeriana.pedidos.productos.command.ProductCommandService;
import co.edu.javeriana.pedidos.productos.dto.CreateProductRequest;
import co.edu.javeriana.pedidos.productos.dto.ProductView;
import co.edu.javeriana.pedidos.productos.dto.UpdateAvailabilityRequest;
import co.edu.javeriana.pedidos.productos.dto.UpdatePriceRequest;
import co.edu.javeriana.pedidos.productos.dto.UpdateStockRequest;
import co.edu.javeriana.pedidos.productos.query.ProductQueryServiceImpl;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.util.List;

/** Comandos (POST/PUT) van a ProductCommandService; consultas (GET) a ProductQueryServiceImpl. */
@Path("/products")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProductResource {

    @Inject
    ProductCommandService commands;

    @Inject
    ProductQueryServiceImpl queries;

    @POST
    public Response create(@Valid @NotNull CreateProductRequest request, @Context UriInfo uriInfo) {
        Long id = commands.create(request);
        return Response.created(uriInfo.getAbsolutePathBuilder().path(id.toString()).build())
                .entity(queries.findById(id))
                .build();
    }

    @GET
    public List<ProductView> list() {
        return queries.findAll();
    }

    @GET
    @Path("/{id}")
    public ProductView get(@PathParam("id") Long id) {
        return queries.findById(id);
    }

    @PUT
    @Path("/{id}/price")
    public ProductView updatePrice(@PathParam("id") Long id, @Valid @NotNull UpdatePriceRequest request) {
        commands.updatePrice(id, request.price());
        return queries.findById(id);
    }

    @PUT
    @Path("/{id}/stock")
    public ProductView updateStock(@PathParam("id") Long id, @Valid @NotNull UpdateStockRequest request) {
        commands.updateStock(id, request.stock());
        return queries.findById(id);
    }

    @PUT
    @Path("/{id}/availability")
    public ProductView updateAvailability(@PathParam("id") Long id,
                                          @Valid @NotNull UpdateAvailabilityRequest request) {
        commands.updateAvailability(id, request.active());
        return queries.findById(id);
    }
}
