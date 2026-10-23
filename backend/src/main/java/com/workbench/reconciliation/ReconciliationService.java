package com.workbench.reconciliation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationService {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final DiscrepancyRepository discrepancies;
    private final AuditRepository audits;
    private final ReconciliationStateRepository states;
    private final ReconciliationMatcher matcher = new ReconciliationMatcher();
    private final BigDecimal tolerance;

    public ReconciliationService(OrderRepository orders, PaymentRepository payments,
                                 DiscrepancyRepository discrepancies, AuditRepository audits,
                                 ReconciliationStateRepository states,
                                 @Value("${workbench.tolerance:0.02}") BigDecimal tolerance) {
        this.orders = orders;
        this.payments = payments;
        this.discrepancies = discrepancies;
        this.audits = audits;
        this.states = states;
        this.tolerance = tolerance;
    }

    @Transactional
    public void reconcile() {
        List<OrderRecord> orderRows = orders.findAll();
        List<PaymentRecord> paymentRows = payments.findAll();
        List<ReconciliationMatcher.Order> orderInputs = orderRows.stream()
                .map(row -> new ReconciliationMatcher.Order(row.getOrderId(), row.getAmount(), row.getCurrency())).toList();
        List<ReconciliationMatcher.Payment> paymentInputs = paymentRows.stream()
                .map(row -> new ReconciliationMatcher.Payment(row.getPaymentId(), row.getOrderReference(),
                        row.getAmount(), row.getCurrency(), row.getPaidAt(), row.getManualOrderId())).toList();
        var result = matcher.match(orderInputs, paymentInputs, tolerance);

        Map<String, OrderRecord> orderById = new HashMap<>();
        orderRows.forEach(row -> orderById.put(row.getOrderId(), row));
        Map<String, PaymentRecord> paymentById = new HashMap<>();
        paymentRows.forEach(row -> paymentById.put(row.getPaymentId(), row));
        Map<String, Discrepancy> existing = new HashMap<>();
        discrepancies.findAll().forEach(row -> existing.put(row.getSignature(), row));
        Set<String> generated = new HashSet<>();
        List<Discrepancy> toSave = new ArrayList<>();

        for (var issue : result.issues()) {
            String signature = issue.category() + "|" + safe(issue.orderId()) + "|" + safe(issue.paymentId());
            generated.add(signature);
            OrderRecord order = issue.orderId() == null ? null : orderById.get(issue.orderId());
            PaymentRecord payment = issue.paymentId() == null ? null : paymentById.get(issue.paymentId());
            Discrepancy discrepancy = existing.get(signature);
            if (discrepancy == null) {
                discrepancy = new Discrepancy(signature, issue.category().name(), order, payment, issue.detail());
            } else if (!"RESOLVED".equals(discrepancy.getStatus())) {
                discrepancy.refresh(issue.category().name(), order, payment, issue.detail());
            }
            toSave.add(discrepancy);
        }
        discrepancies.saveAll(toSave);
        List<Discrepancy> staleOpen = existing.values().stream()
                .filter(row -> !generated.contains(row.getSignature()) && "OPEN".equals(row.getStatus())).toList();
        discrepancies.deleteAll(staleOpen);
        ReconciliationState state = states.findById(1L)
            .orElseGet(() -> new ReconciliationState(1L, java.time.LocalDateTime.now()));
        state.setLastRunAt(java.time.LocalDateTime.now());
        states.save(state);
    }

    @Transactional(readOnly = true)
    public Page<Discrepancy> search(String category, String status, String query, int page, int size) {
        int boundedSize = Math.min(Math.max(size, 1), 100);
        return discrepancies.search(blankToNull(category), blankToNull(status), blankToEmpty(query),
                PageRequest.of(Math.max(page, 0), boundedSize, Sort.by(Sort.Direction.DESC, "updatedAt")));
    }

    @Transactional
    public void resolve(long id, String reason) {
        String safeReason = requireReason(reason);
        Discrepancy discrepancy = discrepancies.findById(id).orElseThrow(() -> new NotFoundException("Discrepancy not found."));
        if ("RESOLVED".equals(discrepancy.getStatus())) return;
        discrepancy.resolve(safeReason);
        audits.save(new AuditEvent("RESOLVE", "DISCREPANCY", id + "", null, discrepancy.getSignature(), safeReason));
    }

    @Transactional
    public void linkPayment(String paymentId, String orderId, String reason) {
        String safeReason = requireReason(reason);
        PaymentRecord payment = payments.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found."));
        OrderRecord order = orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order not found."));
        String before = payment.getManualOrderId();
        payment.setManualOrderId(order.getOrderId());
        audits.save(new AuditEvent("LINK_PAYMENT", "PAYMENT", paymentId, before,
                order.getOrderId(), safeReason));
        reconcile();
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        List<OrderRecord> orderRows = orders.findAll();
        List<PaymentRecord> paymentRows = payments.findAll();
        List<Discrepancy> issueRows = discrepancies.findAll();
        boolean hasReconciled = states.existsById(1L);
        Map<String, Long> counts = new HashMap<>();
        issueRows.stream().filter(row -> "OPEN".equals(row.getStatus()))
                .forEach(row -> counts.merge(row.getCategory(), 1L, Long::sum));
        Set<String> unmatchedOrderIds = new HashSet<>();
        Set<String> unmatchedPaymentIds = new HashSet<>();
        for (Discrepancy row : issueRows) {
            if (!"OPEN".equals(row.getStatus())) continue;
            if (row.getOrderRecord() != null && ("MISSING_PAYMENT".equals(row.getCategory())
                    || "AMOUNT_MISMATCH".equals(row.getCategory()))) unmatchedOrderIds.add(row.getOrderRecord().getOrderId());
            if (row.getPaymentRecord() != null) unmatchedPaymentIds.add(row.getPaymentRecord().getPaymentId());
        }
        if (!hasReconciled) {
            orderRows.forEach(row -> unmatchedOrderIds.add(row.getOrderId()));
            paymentRows.forEach(row -> unmatchedPaymentIds.add(row.getPaymentId()));
        }
        Map<String, BigDecimal> orderTotals = totalsByCurrency(orderRows);
        Map<String, BigDecimal> paymentTotals = paymentTotalsByCurrency(paymentRows);
        Map<String, BigDecimal> unmatchedOrderTotals = orderRows.stream()
            .filter(row -> unmatchedOrderIds.contains(row.getOrderId()))
            .collect(java.util.stream.Collectors.groupingBy(OrderRecord::getCurrency,
                java.util.stream.Collectors.reducing(BigDecimal.ZERO, OrderRecord::getAmount, BigDecimal::add)));
        Map<String, BigDecimal> unmatchedPaymentTotals = paymentRows.stream()
            .filter(row -> unmatchedPaymentIds.contains(row.getPaymentId()))
            .collect(java.util.stream.Collectors.groupingBy(PaymentRecord::getCurrency,
                java.util.stream.Collectors.reducing(BigDecimal.ZERO, PaymentRecord::getAmount, BigDecimal::add)));
        Map<String, BigDecimal> matchedOrderTotals = subtract(orderTotals, unmatchedOrderTotals);
        Map<String, BigDecimal> matchedPaymentTotals = subtract(paymentTotals, unmatchedPaymentTotals);
        return new Summary(orderRows.size(), paymentRows.size(), orderRows.size() - unmatchedOrderIds.size(),
            paymentRows.size() - unmatchedPaymentIds.size(), counts, hasReconciled, orderTotals, paymentTotals,
            matchedOrderTotals, matchedPaymentTotals, unmatchedOrderTotals, unmatchedPaymentTotals, tolerance);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> auditHistory() {
        return audits.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw new InvalidRequestException("A non-empty reason is required.");
        if (reason.trim().length() > 1000) throw new InvalidRequestException("Reason must be 1000 characters or fewer.");
        return reason.trim();
    }

    private static String safe(String value) { return value == null ? "-" : value; }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String blankToEmpty(String value) { return value == null || value.isBlank() ? "" : value.trim(); }

    private static Map<String, BigDecimal> totalsByCurrency(List<OrderRecord> rows) {
        return rows.stream().collect(java.util.stream.Collectors.groupingBy(OrderRecord::getCurrency,
                java.util.stream.Collectors.reducing(BigDecimal.ZERO, OrderRecord::getAmount, BigDecimal::add)));
    }

    private static Map<String, BigDecimal> paymentTotalsByCurrency(List<PaymentRecord> rows) {
        return rows.stream().collect(java.util.stream.Collectors.groupingBy(PaymentRecord::getCurrency,
                java.util.stream.Collectors.reducing(BigDecimal.ZERO, PaymentRecord::getAmount, BigDecimal::add)));
    }

    private static Map<String, BigDecimal> subtract(Map<String, BigDecimal> total, Map<String, BigDecimal> unmatched) {
        Map<String, BigDecimal> result = new HashMap<>(total);
        unmatched.forEach((currency, amount) -> result.merge(currency, amount.negate(), BigDecimal::add));
        result.replaceAll((currency, amount) -> amount.max(BigDecimal.ZERO));
        return result;
    }

    public record Summary(long orderCount, long paymentCount, long matchedOrders, long matchedPayments,
                          Map<String, Long> openDiscrepancies, boolean hasReconciled,
                          Map<String, BigDecimal> orderTotalsByCurrency,
                          Map<String, BigDecimal> paymentTotalsByCurrency,
                          Map<String, BigDecimal> matchedOrderTotalsByCurrency,
                          Map<String, BigDecimal> matchedPaymentTotalsByCurrency,
                          Map<String, BigDecimal> unmatchedOrderTotalsByCurrency,
                          Map<String, BigDecimal> unmatchedPaymentTotalsByCurrency, BigDecimal tolerance) {}
}