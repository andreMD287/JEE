package co.edu.javeriana.pedidos.pagos.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.javeriana.pedidos.pagos.model.Payment;
import co.edu.javeriana.pedidos.shared.contract.PaymentResult;
import co.edu.javeriana.pedidos.shared.contract.PaymentStatus;
import co.edu.javeriana.pedidos.shared.exception.InvalidStateException;
import co.edu.javeriana.pedidos.shared.exception.ValidationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;

class PaymentServiceImplTest {

    private EntityManager em;
    private TypedQuery<Payment> query;
    private PaymentServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        em = mock(EntityManager.class);
        query = mock(TypedQuery.class);

        service = new PaymentServiceImpl();
        service.em = em;

        when(em.createQuery(anyString(), eq(Payment.class)))
                .thenReturn(query);
        when(query.setParameter(eq("orderId"), any()))
                .thenReturn(query);
        when(query.setLockMode(any(LockModeType.class)))
                .thenReturn(query);
    }

    @Test
    void shouldApprovePayment() {
        mockExisting();

        PaymentResult result = service.process(
                1L,
                new BigDecimal("150000.00"),
                false
        );

        assertEquals(1L, result.orderId());
        assertEquals(0, result.amount().compareTo(
                new BigDecimal("150000.00")
        ));
        assertEquals(PaymentStatus.APROBADO, result.status());

        verify(em).persist(any(Payment.class));
        verify(em).flush();
    }

    @Test
    void shouldRejectPaymentWhenFailureIsSimulated() {
        mockExisting();

        PaymentResult result = service.process(
                2L,
                new BigDecimal("50000.00"),
                true
        );

        assertEquals(PaymentStatus.RECHAZADO, result.status());

        verify(em).persist(any(Payment.class));
        verify(em).flush();
    }

    @Test
    void shouldReturnExistingPaymentWithoutProcessingAgain() {
        Payment existing = approvedPayment(
                3L,
                new BigDecimal("80000.00")
        );
        mockExisting(existing);

        PaymentResult result = service.process(
                3L,
                new BigDecimal("80000.00"),
                true
        );

        assertEquals(PaymentStatus.APROBADO, result.status());
        verify(em, never()).persist(any(Payment.class));
        verify(em, never()).flush();
    }

    @Test
    void shouldRejectRetryWithDifferentAmount() {
        Payment existing = approvedPayment(
                4L,
                new BigDecimal("90000.00")
        );
        mockExisting(existing);

        assertThrows(
                InvalidStateException.class,
                () -> service.process(
                        4L,
                        new BigDecimal("100000.00"),
                        false
                )
        );

        verify(em, never()).persist(any(Payment.class));
    }

    @Test
    void shouldReverseApprovedPayment() {
        Payment payment = approvedPayment(
                5L,
                new BigDecimal("120000.00")
        );
        mockExisting(payment);

        service.reverse(5L);

        assertEquals(PaymentStatus.REVERSADO, payment.getStatus());
        assertNotNull(payment.getReversedAt());
    }

    @Test
    void shouldBeIdempotentWhenReversedTwice() {
        Payment payment = approvedPayment(
                6L,
                new BigDecimal("200000.00")
        );
        mockExisting(payment);

        service.reverse(6L);
        Instant firstReversal = payment.getReversedAt();

        service.reverse(6L);

        assertEquals(PaymentStatus.REVERSADO, payment.getStatus());
        assertEquals(firstReversal, payment.getReversedAt());
    }

    @Test
    void shouldDoNothingWhenPaymentDoesNotExist() {
        mockExisting();

        assertDoesNotThrow(() -> service.reverse(999L));
    }

    @Test
    void shouldValidateOrderId() {
        assertThrows(
                ValidationException.class,
                () -> service.process(
                        null,
                        new BigDecimal("10000.00"),
                        false
                )
        );
    }

    @Test
    void shouldValidatePositiveAmount() {
        assertThrows(
                ValidationException.class,
                () -> service.process(
                        7L,
                        BigDecimal.ZERO,
                        false
                )
        );
    }

    private Payment approvedPayment(
            Long orderId,
            BigDecimal amount
    ) {
        Payment payment = new Payment(orderId, amount);
        payment.approve();
        return payment;
    }

    private void mockExisting(Payment... payments) {
        when(query.getResultStream())
                .thenAnswer(invocation -> Arrays.stream(payments));
    }
}