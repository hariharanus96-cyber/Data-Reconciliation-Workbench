package com.workbench.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_events", indexes = @Index(name = "idx_audit_created", columnList = "created_at"))
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 40)
    private String action;
    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;
    @Column(name = "entity_id", nullable = false, length = 120)
    private String entityId;
    @Column(length = 1000)
    private String beforeValue;
    @Column(length = 1000)
    private String afterValue;
    @Column(nullable = false, length = 1000)
    private String reason;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected AuditEvent() {}

    public AuditEvent(String action, String entityType, String entityId, String beforeValue, String afterValue,
                      String reason) {
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getBeforeValue() { return beforeValue; }
    public String getAfterValue() { return afterValue; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}