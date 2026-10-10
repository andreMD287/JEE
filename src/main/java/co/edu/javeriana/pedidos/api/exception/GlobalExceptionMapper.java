package co.edu.javeriana.pedidos.api.exception;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

import co.edu.javeriana.pedidos.api.response.ErrorResponse;
import co.edu.javeriana.pedidos.shared.exception.ErrorCode;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOGGER =
            Logger.getLogger(GlobalExceptionMapper.class.getName());

    @Override
    public Response toResponse(Throwable exception) {
        LOGGER.log(Level.SEVERE, "Error no controlado en la aplicación", exception);

        ErrorResponse errorResponse = new ErrorResponse(
                ErrorCode.INTERNAL_ERROR.name(),
                "Ocurrió un error interno en el servidor",
                Instant.now().toString()
        );

        return Response.status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .type(MediaType.APPLICATION_JSON)
                .entity(errorResponse)
                .build();
    }
}