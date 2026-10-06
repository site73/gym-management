package com.gym.booking.application;

import com.gym.booking.api.BookingFacade;
import com.gym.booking.internal.BookingEntity;
import com.gym.booking.internal.BookingRepository;
import com.gym.course.api.CourseFacade;
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
    private final AuditLogger auditLogger;
    private final ApplicationEventPublisher events;

    public BookingAppService(BookingRepository bookingRepository,
                             RuleEngine ruleEngine,
                             CourseFacade courseFacade,
                             AuditLogger auditLogger,
                             ApplicationEventPublisher events) {
        this.bookingRepository = bookingRepository;
        this.ruleEngine = ruleEngine;
        this.courseFacade = courseFacade;
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
        // 幂等：同一会员同一课程不重复生成
        var exist = bookingRepository.findByMemberIdAndCourseId(memberId, courseId);
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

    /** 爽约判定（REQ-B4-003；通常由定时任务驱动） */
    @Transactional
    public int markNoShow(Long bookingId) {
        var booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在：" + bookingId));
        booking.markNoShow();
        bookingRepository.save(booking);
        int count = Math.toIntExact(countNoShow(booking.getMemberId(), 30));
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
}
