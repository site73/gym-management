package com.gym.booking.application;

import com.gym.booking.api.BookingDetailView;
import com.gym.booking.api.BookingFacade;
import com.gym.booking.api.BookingView;
import com.gym.booking.internal.BookingEntity;
import com.gym.booking.internal.BookingRepository;
import com.gym.course.api.CourseFacade;
import com.gym.membership.api.MembershipFacade;
import com.gym.shared.audit.AuditLogger;
import com.gym.shared.event.BookingEvents;
import com.gym.shared.rule.RuleEngine;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 约课用例编排（S1 切片核心）。
 *
 * <p>校验顺序严格对齐《系统规则与约束说明》：SYS-R1/R2 → SYS-R4 → SYS-R3，
 * 校验不通过返回带原因的结果（供前端提示与审计留痕）。
 *
 * <p>跨模块能力均通过契约调用：{@link RuleEngine}、{@link CourseFacade}，不直连对方数据表。
 */
@Service
public class BookingAppService implements BookingFacade {

    private final BookingRepository bookingRepository;
    private final RuleEngine ruleEngine;
    private final CourseFacade courseFacade;
    private final MembershipFacade membershipFacade;
    private final AuditLogger auditLogger;
    private final ApplicationEventPublisher events;

    public BookingAppService(BookingRepository bookingRepository,
                             RuleEngine ruleEngine,
                             CourseFacade courseFacade,
                             MembershipFacade membershipFacade,
                             AuditLogger auditLogger,
                             ApplicationEventPublisher events) {
        this.bookingRepository = bookingRepository;
        this.ruleEngine = ruleEngine;
        this.courseFacade = courseFacade;
        this.membershipFacade = membershipFacade;
        this.auditLogger = auditLogger;
        this.events = events;
    }

    /** 约课结果（含被拒绝时的原因与规则编号，便于追溯） */
    public record BookingResult(boolean ok, Long bookingId, String status, String ruleCode, String reason) {
        public static BookingResult ok(Long id) { return new BookingResult(true, id, "booked", null, null); }
        public static BookingResult reject(String rule, String reason) { return new BookingResult(false, null, null, rule, reason); }
    }

    /** 约课（REQ-B4-001） */
    @Transactional
    public BookingResult book(Long memberId, Long courseId) {
        // 幂等：同一会员同一课程（且处于有效状态）不重复生成；已取消的允许重新预约
        var exist = bookingRepository.findFirstByMemberIdAndCourseIdAndStatusIn(
                memberId, courseId, java.util.List.of("booked", "checked_in", "no_show"));
        if (exist.isPresent()) {
            return BookingResult.ok(exist.get().getId());
        }
        // SYS-R1 / SYS-R2
        var r1 = ruleEngine.checkMembershipEffective(memberId);
        if (!r1.passed()) return BookingResult.reject(r1.ruleCode(), r1.reason());
        // SYS-R4
        var r4 = ruleEngine.checkNoShowPenalty(memberId);
        if (!r4.passed()) return BookingResult.reject(r4.ruleCode(), r4.reason());
        // SYS-R3
        var r3 = ruleEngine.checkBookingAllowed(memberId, courseId);
        if (!r3.passed()) return BookingResult.reject(r3.ruleCode(), r3.reason());

        // 占用名额（原子，防超卖）
        if (!courseFacade.occupySeat(courseId)) {
            return BookingResult.reject("SYS-R3", "课程已满");
        }
        var saved = bookingRepository.save(new BookingEntity(memberId, courseId));
        auditLogger.log("booking.create", "booking", saved.getId(), "member=" + memberId + ",course=" + courseId);
        return BookingResult.ok(saved.getId());
    }

    /** 签到（REQ-B4-002；channel：scan / front_desk） */
    @Transactional
    public void checkIn(Long bookingId, String channel, Long operatorId) {
        var booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在：" + bookingId));
        booking.checkIn(channel, operatorId);
        bookingRepository.save(booking);
        auditLogger.log("booking.checkin", "booking", bookingId, "channel=" + channel);
        events.publishEvent(new BookingEvents.BookingCheckedInEvent(
                booking.getId(), booking.getMemberId(), booking.getCourseId()));
    }

    /**
     * 爽约判定（REQ-B4-003）。
     *
     * <p>累计爽约达到 SYS-R4 的 N 次时，经 {@link MembershipFacade} 施加 D 天预约限制。
     *
     * @return 近 30 天累计爽约次数
     */
    @Transactional
    public int markNoShow(Long bookingId) {
        var booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在：" + bookingId));
        booking.markNoShow();
        bookingRepository.save(booking);
        int count = Math.toIntExact(countNoShow(booking.getMemberId(), 30));
        if (count >= ruleEngine.penaltyThreshold()) {
            membershipFacade.applyPenalty(booking.getMemberId(), ruleEngine.penaltyDays());
            auditLogger.log("penalty.applied", "member", booking.getMemberId(),
                    "days=" + ruleEngine.penaltyDays() + ", 累计爽约=" + count);
        }
        auditLogger.log("booking.noShow", "booking", bookingId, "累计=" + count);
        events.publishEvent(new BookingEvents.BookingNoShowEvent(
                booking.getId(), booking.getMemberId(), booking.getCourseId(), count));
        return count;
    }

    /** 取消并释放名额（SYS-R3） */
    @Transactional
    public void cancel(Long bookingId) {
        var booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在：" + bookingId));
        booking.cancel();
        bookingRepository.save(booking);
        courseFacade.releaseSeat(booking.getCourseId());
        auditLogger.log("booking.cancel", "booking", bookingId, null);
    }

    /* ---------------- BookingFacade 实现（供其他模块调用） ---------------- */

    @Override
    public int countActiveBookings(Long memberId) {
        return Math.toIntExact(bookingRepository.countByMemberIdAndStatusAndBookedAtAfter(
                memberId, "booked", java.time.LocalDateTime.now().minusMonths(1)));
    }

    @Override
    public long countNoShow(Long memberId, int withinDays) {
        return bookingRepository.countByMemberIdAndStatusAndBookedAtAfter(
                memberId, "no_show", java.time.LocalDateTime.now().minusDays(withinDays));
    }

    @Override
    public long countBookings(Long memberId, int withinDays) {
        return bookingRepository.countByMemberIdAndBookedAtAfter(
                memberId, java.time.LocalDateTime.now().minusDays(withinDays));
    }

    @Override
    public boolean hasPendingPenalty(Long memberId) {
        return !ruleEngine.checkNoShowPenalty(memberId).passed();
    }

    /** 预约列表（管理后台）：memberId 为空则返回全部 */
    @Override
    public java.util.List<BookingView> listBookings(Long memberId) {
        java.util.List<BookingEntity> list = (memberId == null)
                ? bookingRepository.findAll()
                : bookingRepository.findByMemberIdOrderByIdDesc(memberId);
        return list.stream().map(BookingAppService::toView).toList();
    }

    private static BookingView toView(BookingEntity b) {
        return new BookingView(b.getId(), b.getMemberId(), b.getCourseId(), b.getStatus(),
                b.getCheckinChannel(), b.getOperatorId(), b.getBookedAt(), b.getCheckinAt());
    }

    @Override
    public BookingView getBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .map(BookingAppService::toView)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在：" + bookingId));
    }

    @Override
    public long countCheckedIn(Long memberId, int withinDays) {
        return bookingRepository.countByMemberIdAndStatusAndCheckinAtAfter(
                memberId, "checked_in", java.time.LocalDateTime.now().minusDays(withinDays));
    }

    @Override
    public Integer daysSinceLastCheckin(Long memberId) {
        return bookingRepository.findFirstByMemberIdAndStatusOrderByCheckinAtDesc(memberId, "checked_in")
                .map(b -> b.getCheckinAt() == null ? null
                        : (int) java.time.Duration.between(b.getCheckinAt(), java.time.LocalDateTime.now()).toDays())
                .orElse(null);
    }

    @Override
    public java.util.Map<String, Long> countByStatus() {
        java.util.Map<String, Long> map = new java.util.LinkedHashMap<>();
        for (Object[] row : bookingRepository.countGroupByStatus()) {
            map.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return map;
    }

    @Override
    public long countActiveBookingsOfCourse(Long courseId) {
        return bookingRepository.countByCourseIdAndStatus(courseId, "booked");
    }

    /**
     * 某课程的报名名单：一次取齐会员档案，避免逐条查询。
     */
    @Override
    public java.util.List<BookingDetailView> listBookingsByCourse(Long courseId) {
        var course = courseFacade.courseOf(courseId);
        var members = membershipFacade.listMembers().stream()
                .collect(java.util.stream.Collectors.toMap(m -> m.id(), m -> m, (a, b) -> a));
        return bookingRepository.findByCourseIdOrderByIdAsc(courseId).stream()
                .map(b -> {
                    var m = members.get(b.getMemberId());
                    return new BookingDetailView(b.getId(), b.getMemberId(),
                            m == null ? ("#" + b.getMemberId()) : m.name(),
                            m == null ? null : m.memberNo(),
                            courseId, course.name(), b.getStatus(),
                            b.getCheckinChannel(), b.getBookedAt(), b.getCheckinAt());
                })
                .toList();
    }

    @Override
    public java.util.List<CoachTimes> checkedInTimesByCoach(int withinDays) {
        java.util.List<CoachTimes> list = new java.util.ArrayList<>();
        for (Object[] row : bookingRepository.sumCheckedInTimesByCoach(
                java.time.LocalDateTime.now().minusDays(withinDays))) {
            list.add(new CoachTimes(((Number) row[0]).longValue(), ((Number) row[1]).longValue()));
        }
        return list;
    }
}
