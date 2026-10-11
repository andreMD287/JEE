package co.edu.javeriana.pedidos.saga.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import co.edu.javeriana.pedidos.shared.contract.SagaStatus;

class SagaExecutionTest {

    @Test
    void registraElCaminoExitosoEnOrden() {
        SagaExecution saga = new SagaExecution(1L);
        saga.advanceTo("INVENTARIO_RESERVADO");
        saga.advanceTo("PAGO_APROBADO");
        saga.complete();

        assertEquals(SagaStatus.COMPLETED, saga.getStatus());
        assertEquals("PEDIDO_CONFIRMADO", saga.getCurrentStep());
        assertEquals(
                List.of("PEDIDO_CREADO", "INVENTARIO_RESERVADO", "PAGO_APROBADO", "PEDIDO_CONFIRMADO"),
                steps(saga)
        );
    }

    @Test
    void registraLasCompensacionesConSuMotivo() {
        SagaExecution saga = new SagaExecution(1L);
        saga.advanceTo("INVENTARIO_RESERVADO");
        saga.advanceTo("PAGO_APROBADO");
        saga.startCompensation("Fallo al confirmar");
        saga.recordStep("PAGO_REVERSADO", null);
        saga.recordStep("INVENTARIO_LIBERADO", null);
        saga.compensated("Fallo al confirmar");

        assertEquals(SagaStatus.COMPENSATED, saga.getStatus());
        assertEquals("Fallo al confirmar", saga.getFailureReason());
        assertEquals(
                List.of("PEDIDO_CREADO", "INVENTARIO_RESERVADO", "PAGO_APROBADO", "COMPENSANDO",
                        "PAGO_REVERSADO", "INVENTARIO_LIBERADO", "PEDIDO_CANCELADO"),
                steps(saga)
        );
        assertEquals("Fallo al confirmar", saga.getSteps().get(3).getDetail());
    }

    @Test
    void registraElFalloTecnico() {
        SagaExecution saga = new SagaExecution(1L);
        saga.startCompensation("Pago rechazado");
        saga.failed("No se pudo liberar el inventario");

        assertEquals(SagaStatus.FAILED, saga.getStatus());
        assertEquals("FALLO_TECNICO", saga.getCurrentStep());
    }

    private static List<String> steps(SagaExecution saga) {
        return saga.getSteps().stream().map(SagaStep::getStep).toList();
    }
}
