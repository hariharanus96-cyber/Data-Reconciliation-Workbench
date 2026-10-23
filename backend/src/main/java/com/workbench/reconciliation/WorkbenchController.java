package com.workbench.reconciliation;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api")
public class WorkbenchController {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final DiscrepancyRepository discrepancies;
    private final ReconciliationService reconciliation;
    private final JobManager jobs;
    private final JobProcessor processor;

    public WorkbenchController(OrderRepository orders, PaymentRepository payments, DiscrepancyRepository discrepancies,
                               ReconciliationService reconciliation, JobManager jobs, JobProcessor processor) {
        this.orders = orders;
        this.payments = payments;
        this.discrepancies = discrepancies;
        this.reconciliation = reconciliation;
        this.jobs = jobs;
        this.processor = processor;
    }

    @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobManager.JobView importCsv(@RequestPart("orders") MultipartFile ordersFile,
                                        @RequestPart("payments") MultipartFile paymentsFile) throws java.io.IOException {
        if (ordersFile.isEmpty() || paymentsFile.isEmpty()) throw new InvalidRequestException("Both CSV files are required.");
        String id = jobs.create("IMPORT");
        processor.importFiles(id, ordersFile.getBytes(), paymentsFile.getBytes());
        return jobs.get(id);
    }

    @PostMapping("/reconciliations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobManager.JobView startReconciliation() {
        String id = jobs.create("RECONCILIATION");
        processor.reconcile(id);
        return jobs.get(id);
    }

    @GetMapping("/jobs/{id}")
    public JobManager.JobView job(@PathVariable String id) { return jobs.get(id); }

    @GetMapping("/summary")
    public ReconciliationService.Summary summary() { return reconciliation.summary(); }

    @GetMapping("/discrepancies")
    public Page<Discrepancy> discrepancies(@RequestParam(required = false) String category,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "25") int size) {
        return reconciliation.search(category, status, q, page, size);
    }

    @PostMapping("/discrepancies/{id}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@PathVariable long id, @RequestBody ReasonRequest request) {
        reconciliation.resolve(id, request.reason());
    }

    @PostMapping("/payments/{paymentId}/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void linkPayment(@PathVariable String paymentId, @RequestBody LinkRequest request) {
        reconciliation.linkPayment(paymentId, request.orderId(), request.reason());
    }

    @GetMapping("/orders")
    public List<OrderRecord> orderSearch(@RequestParam(required = false) String q) {
        return orders.search(q == null || q.isBlank() ? "" : q.trim(), PageRequest.of(0, 100));
    }

    @GetMapping("/payments/{paymentId}")
    public PaymentRecord payment(@PathVariable String paymentId) {
        return payments.findById(paymentId).orElseThrow(() -> new NotFoundException("Payment not found."));
    }

    @GetMapping("/audit")
    public List<AuditEvent> audit() { return reconciliation.auditHistory(); }

    public record ReasonRequest(String reason) {}
    public record LinkRequest(String orderId, String reason) {}
}