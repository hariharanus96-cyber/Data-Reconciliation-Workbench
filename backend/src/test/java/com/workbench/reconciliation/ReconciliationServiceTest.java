package com.workbench.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class ReconciliationServiceTest {
    @Test
    void rerunDoesNotDuplicateIssuesAndPreservesResolution() {
        OrderRepository orders = org.mockito.Mockito.mock(OrderRepository.class);
        PaymentRepository payments = org.mockito.Mockito.mock(PaymentRepository.class);
        DiscrepancyRepository discrepancies = org.mockito.Mockito.mock(DiscrepancyRepository.class);
        AuditRepository audits = org.mockito.Mockito.mock(AuditRepository.class);
        ReconciliationStateRepository states = org.mockito.Mockito.mock(ReconciliationStateRepository.class);
        var order = new OrderRecord("ORD-9", "CUS-9", new BigDecimal("20.00"), "USD", LocalDateTime.now());
        var payment = new PaymentRecord("PAY-9", "ORD-9", new BigDecimal("21.00"), "USD", LocalDateTime.now());
        when(orders.findAll()).thenReturn(List.of(order));
        when(payments.findAll()).thenReturn(List.of(payment));
        AtomicReference<List<Discrepancy>> persisted = new AtomicReference<>(List.of());
        when(discrepancies.findAll()).thenAnswer(invocation -> persisted.get());
        doAnswer(invocation -> {
            Iterable<Discrepancy> incoming = invocation.getArgument(0);
            java.util.ArrayList<Discrepancy> saved = new java.util.ArrayList<>();
            incoming.forEach(saved::add);
            persisted.set(saved);
            return saved;
        }).when(discrepancies).saveAll(any());

        var service = new ReconciliationService(orders, payments, discrepancies, audits, states, new BigDecimal("0.02"));
        service.reconcile();
        assertThat(persisted.get()).hasSize(1);
        String signature = persisted.get().getFirst().getSignature();
        persisted.get().getFirst().resolve("Reviewed against provider settlement report");

        service.reconcile();

        assertThat(persisted.get()).hasSize(1);
        assertThat(persisted.get().getFirst().getSignature()).isEqualTo(signature);
        assertThat(persisted.get().getFirst().getStatus()).isEqualTo("RESOLVED");
        assertThat(persisted.get().getFirst().getResolutionReason()).isEqualTo("Reviewed against provider settlement report");
    }
}