package com.gym.payment.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单对外视图（契约只暴露 DTO）。 */
public record PaymentOrderView(
        Long id,
        String orderNo,
        Long memberId,
        String bizType,
        Integer times,
        BigDecimal amount,
        BigDecimal paidAmount,
        String status,
        String abnormalReason,
        LocalDateTime createdAt,
        LocalDateTime paidAt) {
}
