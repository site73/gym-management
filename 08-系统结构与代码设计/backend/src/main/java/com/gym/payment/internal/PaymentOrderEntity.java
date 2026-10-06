package com.gym.payment.internal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 收费订单实体（对应表 payment_order，见 07 的 V2 迁移脚本）。 */
@Entity
@Table(name = "payment_order",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_order_no", columnNames = "order_no"),
           @UniqueConstraint(name = "uk_out_trade_no", columnNames = "out_trade_no")
       })
public class PaymentOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 40)
    private String orderNo;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** membership / pt_package */
    @Column(name = "biz_type", nullable = false, length = 16)
    private String bizType;

    @Column(name = "ref_id")
    private Long refId;

    /** 课包次数（biz_type=pt_package 时有效），见 V7 迁移 */
    @Column(name = "times")
    private Integer times;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_amount", precision = 10, scale = 2)
    private BigDecimal paidAmount;

    @Column(nullable = false, length = 16)
    private String channel = "wechat";

    /** pending / paid / cancelled / refunded / abnormal */
    @Column(nullable = false, length = 16)
    private String status = "pending";

    @Column(name = "out_trade_no", length = 64)
    private String outTradeNo;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "abnormal_reason", length = 255)
    private String abnormalReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected PaymentOrderEntity() {}

    public PaymentOrderEntity(String orderNo, Long memberId, String bizType, BigDecimal amount) {
        this.orderNo = orderNo;
        this.memberId = memberId;
        this.bizType = bizType;
        this.amount = amount;
    }

    /** 支付成功（幂等：已支付则直接返回 false） */
    public boolean markPaid(BigDecimal paidAmount, String outTradeNo) {
        if ("paid".equals(this.status)) return false;
        this.status = "paid";
        this.paidAmount = paidAmount;
        this.outTradeNo = outTradeNo;
        this.paidAt = LocalDateTime.now();
        return true;
    }

    /** 金额不符 / 回调失败 → 标记异常（REQ-B5-004：不得变更会籍） */
    public void markAbnormal(String reason, BigDecimal paidAmount) {
        this.status = "abnormal";
        this.paidAmount = paidAmount;
        this.abnormalReason = reason;
    }

    public Long getId() { return id; }
    public void setTimes(Integer times) { this.times = times; }
    public Integer getTimes() { return times; }
    public String getOrderNo() { return orderNo; }
    public Long getMemberId() { return memberId; }
    public String getBizType() { return bizType; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public String getStatus() { return status; }
    public String getAbnormalReason() { return abnormalReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
}
