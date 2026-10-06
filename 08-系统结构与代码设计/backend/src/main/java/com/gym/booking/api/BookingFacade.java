package com.gym.booking.api;

/**
 * 预约模块对外契约（模块边界的唯一入口）。
 *
 * <p>约定：其他模块<b>只能</b>通过本接口访问预约能力，
 * <b>不得</b>直接依赖 {@code com.gym.booking.internal} 下的实体、Repository 或 booking 表。
 *
 * <p>调用方：
 * <ul>
 *   <li>warning：统计近 30 天爽约次数与预约次数（SYS-R7/R8）</li>
 *   <li>report：报表 RP4 预约与爽约统计</li>
 *   <li>payment：上课/结课触发课消（配合课程结课事件）</li>
 * </ul>
 */
public interface BookingFacade {

    /** 会员当前有效（status=booked）的预约数量。 */
    int countActiveBookings(Long memberId);

    /** 会员在给定窗口内的爽约次数（用于 SYS-R4 惩罚与 SYS-R7 风险评分）。 */
    long countNoShow(Long memberId, int withinDays);

    /** 会员在给定窗口内的预约总次数（爽约率分母）。 */
    long countBookings(Long memberId, int withinDays);

    /**
     * 内部签到入口：供前台代签或设备扫码调用。
     *
     * @param bookingId 预约 ID
     * @param channel   scan（扫码）/ front_desk（前台代签）
     * @param operatorId 操作人（代签时非空，用于审计）
     */
    void checkIn(Long bookingId, String channel, Long operatorId);

    /** 会员是否有未处理的爽约（用于提示与限制说明）。 */
    boolean hasPendingPenalty(Long memberId);

    /**
     * 预约列表（管理后台用）。
     *
     * @param memberId 会员 ID；为 null 时返回全部
     */
    java.util.List<BookingView> listBookings(Long memberId);

    /** 单个预约详情（用于权限校验：是否本人预约） */
    BookingView getBooking(Long bookingId);

    /** 会员在给定窗口内的签到次数（SYS-R7/R8 的活跃度指标） */
    long countCheckedIn(Long memberId, int withinDays);

    /** 距上次签到的天数；从未签到返回 null */
    Integer daysSinceLastCheckin(Long memberId);

    /** 各状态的预约数量（报表用） */
    java.util.Map<String, Long> countByStatus();

    /** 按教练汇总近 N 天已签到的课时数（SYS-R10 提成核算） */
    java.util.List<CoachTimes> checkedInTimesByCoach(int withinDays);

    /** 教练课时汇总行 */
    record CoachTimes(Long coachId, long times) {}
}
