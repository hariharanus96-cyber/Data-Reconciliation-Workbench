package com.workbench.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_reference", columnList = "order_reference"),
        @Index(name = "idx_payments_manual_order", columnList = "manual_order_id")
})
public class PaymentRecord {
    @Id
    @Column(name = "payment_id", nullable = false, length = 120)
    private String paymentId;
    @Column(name = "order_reference", nullable = false, length = 120)
    private String orderReference;
    @Column(name = "manual_order_id", length = 120)
    private String manualOrderId;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    protected PaymentRecord() {}

    public PaymentRecord(String paymentId, String orderReference, BigDecimal amount, String currency,
                         LocalDateTime paidAt) {
        this.paymentId = paymentId;
        this.orderReference = orderReference;
        this.amount = amount;
        this.currency = currency;
        this.paidAt = paidAt;
    }

    public String getPaymentId() { return paymentId; }
    public String getOrderReference() { return orderReference; }
    public String getManualOrderId() { return manualOrderId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setManualOrderId(String manualOrderId) { this.manualOrderId = manualOrderId; }
}