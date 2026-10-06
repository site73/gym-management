package com.gym.membership.api;

import java.time.LocalDateTime;

/** 会员对外视图（契约只暴露 DTO；手机号已脱敏存储）。 */
public record MemberView(
        Long id,
        String memberNo,
        String name,
        String phone,
        String status,
        String riskLevel,
        Integer packageRemaining,
        int penaltyDaysRemaining,
        LocalDateTime createdAt) {
}
