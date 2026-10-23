package com.workbench.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_state")
public class ReconciliationState {
    @Id
    private Long id;
    @Column(name = "last_run_at", nullable = false)
    private LocalDateTime lastRunAt;

    protected ReconciliationState() {}

    public ReconciliationState(Long id, LocalDateTime lastRunAt) {
        this.id = id;
        this.lastRunAt = lastRunAt;
    }

    public Long getId() { return id; }
    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
}