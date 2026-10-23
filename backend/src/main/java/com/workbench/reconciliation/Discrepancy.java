package com.workbench.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(name = "discrepancies", uniqueConstraints = @UniqueConstraint(name = "uk_discrepancy_signature", columnNames = "signature"),
        indexes = @Index(name = "idx_discrepancy_category_status", columnList = "category,status"))
public class Discrepancy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 400)
    private String signature;
    @Column(nullable = false, length = 40)
    private String category;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id")
    private OrderRecord orderRecord;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "payment_id")
    private PaymentRecord paymentRecord;
    @Column(nullable = false, length = 20)
    private String status = "OPEN";
    @Column(nullable = false, length = 500)
    private String detail;
    @Column(length = 1000)
    private String resolutionReason;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;

    protected Discrepancy() {}

    public Discrepancy(String signature, String category, OrderRecord orderRecord,
                       PaymentRecord paymentRecord, String detail) {
        this.signature = signature;
        this.category = category;
        this.orderRecord = orderRecord;
        this.paymentRecord = paymentRecord;
        this.detail = detail;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void refresh(String category, OrderRecord orderRecord, PaymentRecord paymentRecord, String detail) {
        this.category = category;
        this.orderRecord = orderRecord;
        this.paymentRecord = paymentRecord;
        this.detail = detail;
        this.updatedAt = LocalDateTime.now();
    }

    public void resolve(String reason) {
        this.status = "RESOLVED";
        this.resolutionReason = reason;
        this.resolvedAt = LocalDateTime.now();
        this.updatedAt = this.resolvedAt;
    }

    public Long getId() { return id; }
    public String getSignature() { return signature; }
    public String getCategory() { return category; }
    public OrderRecord getOrderRecord() { return orderRecord; }
    public PaymentRecord getPaymentRecord() { return paymentRecord; }
    public String getStatus() { return status; }
    public String getDetail() { return detail; }
    public String getResolutionReason() { return resolutionReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
}