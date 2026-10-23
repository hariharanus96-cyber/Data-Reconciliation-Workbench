package com.workbench.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReconciliationMatcherTest {
    private final ReconciliationMatcher matcher = new ReconciliationMatcher();

    @Test
    void acceptsTheConfiguredToleranceBoundaryAndLatePayments() {
        var order = new ReconciliationMatcher.Order("ORD-1", new BigDecimal("25.00"), "USD");
        var payment = new ReconciliationMatcher.Payment("PAY-1", "ord-1", new BigDecimal("25.05"), "USD",
                LocalDateTime.of(2026, 2, 2, 10, 0), null);

        var result = matcher.match(List.of(order), List.of(payment), new BigDecimal("0.05"));

        assertThat(result.matchedOrders()).isEqualTo(1);
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void reportsAboveTolerance() {
        var order = new ReconciliationMatcher.Order("ORD-2", new BigDecimal("10.00"), "USD");
        var payment = new ReconciliationMatcher.Payment("PAY-1", "ORD-2", new BigDecimal("10.06"), "USD", null, null);

        var result = matcher.match(List.of(order), List.of(payment), new BigDecimal("0.05"));

        assertThat(result.matchedOrders()).isZero();
        assertThat(result.issues()).extracting(ReconciliationMatcher.Issue::category)
                .containsExactly(ReconciliationMatcher.Category.AMOUNT_MISMATCH);
    }

    @Test
    void flagsAdditionalPaymentsAsDuplicates() {
        var order = new ReconciliationMatcher.Order("ORD-4", new BigDecimal("10.00"), "USD");
        var payments = List.of(
                new ReconciliationMatcher.Payment("PAY-4", "ORD-4", new BigDecimal("10.00"), "USD", null, null),
                new ReconciliationMatcher.Payment("PAY-5", "ORD-4", new BigDecimal("10.00"), "USD", null, null));

        var result = matcher.match(List.of(order), payments, BigDecimal.ZERO);

        assertThat(result.issues()).extracting(ReconciliationMatcher.Issue::category)
                .containsExactly(ReconciliationMatcher.Category.DUPLICATE_PAYMENT);
    }

    @Test
    void reportsMissingAndOrphanRecords() {
        var orders = List.of(new ReconciliationMatcher.Order("ORD-3", new BigDecimal("1.00"), "USD"));
        var payments = List.of(new ReconciliationMatcher.Payment("PAY-3", "UNKNOWN", new BigDecimal("1.00"),
                "USD", null, null));

        var result = matcher.match(orders, payments, BigDecimal.ZERO);

        assertThat(result.issues()).extracting(ReconciliationMatcher.Issue::category)
                .containsExactly(ReconciliationMatcher.Category.MISSING_PAYMENT,
                        ReconciliationMatcher.Category.ORPHAN_PAYMENT);
    }
}