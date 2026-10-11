package co.edu.javeriana.pedidos.api.exception;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

import co.edu.javeriana.pedidos.api.response.ErrorResponse;
import co.edu.javeriana.pedidos.shared.exception.ErrorCode;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Red de seguridad para todo lo que no es un error de negocio.
 *
 * <p>Los errores propios de JAX-RS (ruta inexistente, metodo no permitido, JSON mal formado,
 * tipo de contenido no soportado) conservan su codigo HTTP; solo los fallos realmente
 * inesperados se responden como 500.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOGGER =
            Logger.getLogger(GlobalExceptionMapper.class.getName());

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof WebApplicationException webException) {
            return fromHttpStatus(webException.getResponse().getStatus());
        }

        if (exception instanceof ProcessingException) {
            // Jersey/JSON-B no pudo leer el cuerpo de la peticion
            return build(ErrorCode.MALFORMED_REQUEST,
                    "El cuerpo de la petición no es un JSON válido o no coincide con el formato esperado");
        }

        LOGGER.log(Level.SEVERE, "Error no controlado en la aplicación", exception);
        return build(ErrorCode.INTERNAL_ERROR, "Ocurrió un error interno en el servidor");
    }

    private static Response fromHttpStatus(int status) {
        return switch (status) {
            case 400 -> build(ErrorCode.MALFORMED_REQUEST, "La petición no es válida");
            case 404 -> build(ErrorCode.RESOURCE_NOT_FOUND, "El recurso solicitado no existe");
            case 405 -> build(ErrorCode.METHOD_NOT_ALLOWED, "El método HTTP no está permitido para este recurso");
            case 406 -> build(ErrorCode.NOT_ACCEPTABLE, "El tipo de respuesta solicitado no está disponible");
            case 415 -> build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "El contenido debe enviarse como application/json");
            default -> status >= 500
                    ? build(ErrorCode.INTERNAL_ERROR, "Ocurrió un error interno en el servidor")
                    : Response.status(status)
                            .type(MediaType.APPLICATION_JSON)
                            .entity(new ErrorResponse("HTTP_" + status,
                                    "La petición no pudo procesarse", Instant.now().toString()))
                            .build();
        };
    }

    static Response build(ErrorCode code, String message) {
        return Response.status(code.httpStatus())
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(code.name(), message, Instant.now().toString()))
                .build();
    }
}
