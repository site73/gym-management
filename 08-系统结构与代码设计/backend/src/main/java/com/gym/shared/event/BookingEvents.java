package com.gym.shared.event;

/**
 * 预约相关领域事件（模块间解耦通信；订阅方须幂等消费）。
 *
 * <p>发布方：booking。订阅方：
 * <ul>
 *   <li>{@code BookingCheckedInEvent} → payment（课包核销/课消）、warning（刷新活跃度）</li>
 *   <li>{@code BookingNoShowEvent} → warning（爽约率与风险）、report（统计）</li>
 * </ul>
 */
public final class BookingEvents {

    private BookingEvents() {}

    /** 签到成功 */
    public record BookingCheckedInEvent(Long bookingId, Long memberId, Long courseId) {}

    /** 判定爽约 */
    public record BookingNoShowEvent(Long bookingId, Long memberId, Long courseId, int noShowCount) {}
}
