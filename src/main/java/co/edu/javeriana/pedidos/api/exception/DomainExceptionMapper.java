package co.edu.javeriana.pedidos.api.exception;

import java.time.Instant;

import co.edu.javeriana.pedidos.api.response.ErrorResponse;
import co.edu.javeriana.pedidos.shared.exception.DomainException;
import co.edu.javeriana.pedidos.shared.exception.ErrorCode;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class DomainExceptionMapper implements ExceptionMapper<DomainException> {

    @Override
    public Response toResponse(DomainException exception) {
        ErrorCode errorCode = exception.getCode();

        ErrorResponse errorResponse = new ErrorResponse(
                errorCode.name(),
                exception.getMessage(),
                Instant.now().toString()
        );

        return Response.status(errorCode.httpStatus())
                .type(MediaType.APPLICATION_JSON)
                .entity(errorResponse)
                .build();
    }
}