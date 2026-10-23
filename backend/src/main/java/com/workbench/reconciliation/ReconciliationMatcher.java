package com.workbench.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReconciliationMatcher {
    public enum Category { MISSING_PAYMENT, ORPHAN_PAYMENT, DUPLICATE_PAYMENT, AMOUNT_MISMATCH }

    public record Order(String orderId, BigDecimal amount, String currency) {}
    public record Payment(String paymentId, String orderReference, BigDecimal amount, String currency,
                          LocalDateTime paidAt, String manualOrderId) {}
    public record Issue(Category category, String orderId, String paymentId, String detail) {}
    public record Result(int matchedOrders, int matchedPayments, List<Issue> issues) {}

    public Result match(List<Order> orders, List<Payment> payments, BigDecimal tolerance) {
        Map<String, List<Payment>> paymentsByReference = new HashMap<>();
        for (Payment payment : payments) {
            String reference = normalize(payment.manualOrderId() == null || payment.manualOrderId().isBlank()
                    ? payment.orderReference() : payment.manualOrderId());
            paymentsByReference.computeIfAbsent(reference, key -> new ArrayList<>()).add(payment);
        }

        List<Issue> issues = new ArrayList<>();
        Set<String> consumed = new HashSet<>();
        int matchedOrders = 0;
        int matchedPayments = 0;

        for (Order order : orders) {
            List<Payment> candidates = paymentsByReference.getOrDefault(normalize(order.orderId()), List.of());
            if (candidates.isEmpty()) {
                issues.add(new Issue(Category.MISSING_PAYMENT, order.orderId(), null, "No payment references this order."));
                continue;
            }

            List<Payment> ordered = candidates.stream()
                    .sorted(Comparator.comparing((Payment payment) -> difference(order.amount(), payment.amount()))
                            .thenComparing(Payment::paidAt, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(Payment::paymentId))
                    .toList();
            Payment primary = ordered.getFirst();
            consumed.add(primary.paymentId());

            if (!order.currency().equalsIgnoreCase(primary.currency())) {
                issues.add(new Issue(Category.AMOUNT_MISMATCH, order.orderId(), primary.paymentId(),
                        "Currency differs: order " + order.currency() + ", payment " + primary.currency() + "."));
            } else if (difference(order.amount(), primary.amount()).compareTo(tolerance) > 0) {
                issues.add(new Issue(Category.AMOUNT_MISMATCH, order.orderId(), primary.paymentId(),
                        "Amount differs by " + difference(order.amount(), primary.amount()).toPlainString() + "."));
            } else {
                matchedOrders++;
                matchedPayments++;
            }

            for (int index = 1; index < ordered.size(); index++) {
                Payment duplicate = ordered.get(index);
                consumed.add(duplicate.paymentId());
                issues.add(new Issue(Category.DUPLICATE_PAYMENT, order.orderId(), duplicate.paymentId(),
                        "Additional payment references this order."));
            }
        }

        for (Payment payment : payments) {
            if (!consumed.contains(payment.paymentId())) {
                issues.add(new Issue(Category.ORPHAN_PAYMENT, null, payment.paymentId(),
                        "Payment reference does not match an order."));
            }
        }
        return new Result(matchedOrders, matchedPayments, List.copyOf(issues));
    }

    private static BigDecimal difference(BigDecimal left, BigDecimal right) {
        return left.subtract(right).abs();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}