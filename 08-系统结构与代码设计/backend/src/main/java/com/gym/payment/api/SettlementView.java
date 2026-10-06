package com.gym.payment.api;

import java.math.BigDecimal;

/** 对账单对外视图。 */
public record SettlementView(
        Long id,
        String period,
        BigDecimal totalAmount,
        Integer orderCount,
        Integer abnormalCount,
        String status) {
}
