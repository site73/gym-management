package com.gym.venue.api;

import java.time.LocalDateTime;

/**
 * 场地预约入参。
 *
 * <p>会员账号发起时 {@code memberId} 会被服务端改写为本人；门店后台可指定会员代约。
 *
 * @param venueId   场馆 ID
 * @param memberId  预约会员 ID
 * @param startTime 开始时间
 * @param endTime   结束时间（必须晚于开始时间）
 */
public record VenueBookingDraft(
        Long venueId,
        Long memberId,
        LocalDateTime startTime,
        LocalDateTime endTime) {
}
