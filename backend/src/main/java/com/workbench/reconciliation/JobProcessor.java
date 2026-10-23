package com.workbench.reconciliation;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobProcessor {
    private final JobManager jobs;
    private final CsvImportService csvImport;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final ReconciliationService reconciliation;

    public JobProcessor(JobManager jobs, CsvImportService csvImport, OrderRepository orders,
                        PaymentRepository payments, ReconciliationService reconciliation) {
        this.jobs = jobs;
        this.csvImport = csvImport;
        this.orders = orders;
        this.payments = payments;
        this.reconciliation = reconciliation;
    }

    @Async
    @Transactional
    public void importFiles(String jobId, byte[] ordersFile, byte[] paymentsFile) {
        jobs.running(jobId);
        try {
            var result = csvImport.parse(new String(ordersFile, StandardCharsets.UTF_8),
                    new String(paymentsFile, StandardCharsets.UTF_8));
            List<String> errors = new java.util.ArrayList<>(result.errors());
            Set<String> orderIds = new HashSet<>();
            orders.findAllById(result.orders().stream().map(OrderRecord::getOrderId).toList())
                    .forEach(row -> orderIds.add(row.getOrderId()));
            List<OrderRecord> newOrders = result.orders().stream().filter(row -> {
                boolean isNew = orderIds.add(row.getOrderId());
                if (!isNew) errors.add("Orders " + row.getOrderId() + ": ID already exists in the workbench.");
                return isNew;
            }).toList();
            Set<String> paymentIds = new HashSet<>();
            payments.findAllById(result.payments().stream().map(PaymentRecord::getPaymentId).toList())
                    .forEach(row -> paymentIds.add(row.getPaymentId()));
            List<PaymentRecord> newPayments = result.payments().stream().filter(row -> {
                boolean isNew = paymentIds.add(row.getPaymentId());
                if (!isNew) errors.add("Payments " + row.getPaymentId() + ": ID already exists in the workbench.");
                return isNew;
            }).toList();
            orders.saveAll(newOrders);
            payments.saveAll(newPayments);
            jobs.completeImport(jobId, newOrders.size(), newPayments.size(), errors,
                    result.invalidRows() + result.orders().size() - newOrders.size()
                            + result.payments().size() - newPayments.size());
        } catch (Exception exception) {
            jobs.fail(jobId, exception.getMessage() == null ? "Import failed." : exception.getMessage());
        }
    }

    @Async
    public void reconcile(String jobId) {
        jobs.running(jobId);
        try {
            reconciliation.reconcile();
            jobs.completeReconciliation(jobId, "Reconciliation completed.");
        } catch (Exception exception) {
            jobs.fail(jobId, exception.getMessage() == null ? "Reconciliation failed." : exception.getMessage());
        }
    }
}