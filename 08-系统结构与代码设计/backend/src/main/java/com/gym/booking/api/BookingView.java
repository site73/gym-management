package com.gym.booking.api;

import java.time.LocalDateTime;

/** 预约对外视图（契约只暴露 DTO）。 */
public record BookingView(
        Long id,
        Long memberId,
        Long courseId,
        String status,
        String checkinChannel,
        Long operatorId,
        LocalDateTime bookedAt,
        LocalDateTime checkinAt) {

    /** 状态中文名（前端展示用，保持一致口径） */
    public String statusText() {
        return switch (status) {
            case "booked" -> "已约";
            case "checked_in" -> "已签到";
            case "no_show" -> "爽约";
            case "cancelled" -> "已取消";
            default -> status;
        };
    }
}
