package co.edu.javeriana.pedidos.api.exception;

import java.util.stream.Collectors;

import co.edu.javeriana.pedidos.shared.exception.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Errores de Bean Validation (@Valid en los recursos) con el mismo formato JSON que el resto. */
@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(ConstraintViolationExceptionMapper::describe)
                .sorted()
                .collect(Collectors.joining("; "));
        return GlobalExceptionMapper.build(ErrorCode.VALIDATION_ERROR,
                message.isEmpty() ? "La petición contiene datos inválidos" : message);
    }

    /** "create.arg0.price: debe ser mayor que 0" -> "price: debe ser mayor que 0". */
    private static String describe(ConstraintViolation<?> violation) {
        String field = null;
        for (Path.Node node : violation.getPropertyPath()) {
            field = node.getName();
        }
        boolean isParameter = field == null || field.startsWith("arg");
        return isParameter ? violation.getMessage() : field + ": " + violation.getMessage();
    }
}
