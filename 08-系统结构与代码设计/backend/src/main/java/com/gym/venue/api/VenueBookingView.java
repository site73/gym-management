package com.gym.venue.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/** 场地预约视图（含会员姓名与场馆名，便于门店直接阅读）。 */
public record VenueBookingView(
        Long id,
        Long venueId,
        String venueName,
        String venueType,
        Long memberId,
        String memberName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        Long createdBy) {

    @JsonProperty("statusCn")
    public String statusCn() {
        return switch (status == null ? "" : status) {
            case "booked" -> "已预约";
            case "cancelled" -> "已取消";
            case "finished" -> "已结束";
            default -> status;
        };
    }

    /** 发起方：会员自助 / 门店代约 */
    @JsonProperty("sourceCn")
    public String sourceCn() {
        return createdBy == null ? "会员自助" : "门店代约";
    }

    @JsonProperty("venueTypeCn")
    public String venueTypeCn() {
        return "public".equals(venueType) ? "公共区域" : "私有场馆";
    }
}
