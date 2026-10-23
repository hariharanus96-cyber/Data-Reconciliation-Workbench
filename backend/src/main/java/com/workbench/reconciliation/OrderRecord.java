package com.workbench.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders", indexes = @Index(name = "idx_orders_customer", columnList = "customer_id"))
public class OrderRecord {
    @Id
    @Column(name = "order_id", nullable = false, length = 120)
    private String orderId;
    @Column(name = "customer_id", nullable = false, length = 120)
    private String customerId;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected OrderRecord() {}

    public OrderRecord(String orderId, String customerId, BigDecimal amount, String currency, LocalDateTime createdAt) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}