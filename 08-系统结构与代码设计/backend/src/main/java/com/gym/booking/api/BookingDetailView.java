package com.gym.booking.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * 预约明细视图（含<b>会员姓名</b>与课程名）。
 *
 * <p>用于"某门课都有谁报了"的场景：课程满员时（如 15/15），
 * 门店后台与任课教练可以查到全部报名会员。
 */
public record BookingDetailView(
        Long id,
        Long memberId,
        String memberName,
        String memberNo,
        Long courseId,
        String courseName,
        String status,
        String checkinChannel,
        LocalDateTime bookedAt,
        LocalDateTime checkinAt) {

    @JsonProperty("statusText")
    public String statusText() {
        return switch (status == null ? "" : status) {
            case "booked" -> "已预约";
            case "checked_in" -> "已签到";
            case "cancelled" -> "已取消";
            case "no_show" -> "爽约";
            default -> status;
        };
    }

    @JsonProperty("channelText")
    public String channelText() {
        if (checkinChannel == null) return "—";
        return "front_desk".equals(checkinChannel) ? "前台代签" : "扫码";
    }
}
