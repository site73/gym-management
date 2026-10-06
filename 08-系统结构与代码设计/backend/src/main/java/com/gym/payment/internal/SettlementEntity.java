package com.gym.payment.internal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 月度对账单实体（对应表 settlement；A10 输出）。 */
@Entity
@Table(name = "settlement", uniqueConstraints = @UniqueConstraint(name = "uk_settlement_period", columnNames = "period"))
public class SettlementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** yyyy-MM */
    @Column(nullable = false, length = 7)
    private String period;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "order_count", nullable = false)
    private Integer orderCount = 0;

    @Column(name = "abnormal_count", nullable = false)
    private Integer abnormalCount = 0;

    /** draft / confirmed */
    @Column(nullable = false, length = 16)
    private String status = "draft";

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected SettlementEntity() {}

    public SettlementEntity(String period, BigDecimal totalAmount, int orderCount, int abnormalCount) {
        this.period = period;
        this.totalAmount = totalAmount;
        this.orderCount = orderCount;
        this.abnormalCount = abnormalCount;
    }

    public void confirm(Long operatorId) {
        this.status = "confirmed";
        this.confirmedBy = operatorId;
        this.confirmedAt = LocalDateTime.now();
    }

    /** 重新生成时更新统计值（幂等：同一期间重复生成覆盖） */
    public void update(BigDecimal totalAmount, int orderCount, int abnormalCount) {
        this.totalAmount = totalAmount;
        this.orderCount = orderCount;
        this.abnormalCount = abnormalCount;
    }

    public Long getId() { return id; }
    public String getPeriod() { return period; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Integer getOrderCount() { return orderCount; }
    public Integer getAbnormalCount() { return abnormalCount; }
    public String getStatus() { return status; }
}
