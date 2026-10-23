package com.workbench.reconciliation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class JobManager {
    public record JobView(String id, String type, String status, int ordersImported, int paymentsImported,
                          int invalidRows, List<String> errors, String message, LocalDateTime createdAt,
                          LocalDateTime updatedAt) {}

    private static final int MAX_ERRORS = 500;
    private final ConcurrentMap<String, MutableJob> jobs = new ConcurrentHashMap<>();

    public String create(String type) {
        String id = java.util.UUID.randomUUID().toString();
        jobs.put(id, new MutableJob(type));
        return id;
    }

    public void running(String id) { jobs.get(id).update("RUNNING", null); }

    public void completeImport(String id, int orderCount, int paymentCount, List<String> errors, int invalidCount) {
        MutableJob job = jobs.get(id);
        job.ordersImported = orderCount;
        job.paymentsImported = paymentCount;
        job.errors = errors.stream().limit(MAX_ERRORS).toList();
        job.invalidRows = invalidCount;
        job.update("COMPLETED", "Import finished; valid rows are available for reconciliation.");
    }

    public void completeReconciliation(String id, String message) {
        jobs.get(id).update("COMPLETED", message);
    }

    public void fail(String id, String message) { jobs.get(id).update("FAILED", message); }

    public JobView get(String id) {
        MutableJob job = jobs.get(id);
        if (job == null) throw new NotFoundException("Job not found.");
        return job.view(id);
    }

    private static final class MutableJob {
        private final String type;
        private final LocalDateTime createdAt = LocalDateTime.now();
        private volatile LocalDateTime updatedAt = createdAt;
        private volatile String status = "QUEUED";
        private volatile int ordersImported;
        private volatile int paymentsImported;
        private volatile int invalidRows;
        private volatile List<String> errors = List.of();
        private volatile String message = "Waiting to start.";

        private MutableJob(String type) { this.type = type; }
        private void update(String status, String message) {
            this.status = status;
            this.message = message;
            this.updatedAt = LocalDateTime.now();
        }
        private JobView view(String id) {
            return new JobView(id, type, status, ordersImported, paymentsImported, invalidRows, errors,
                    message, createdAt, updatedAt);
        }
    }
}