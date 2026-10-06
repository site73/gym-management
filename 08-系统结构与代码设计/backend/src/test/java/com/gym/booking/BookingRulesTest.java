package com.gym.booking;

import com.gym.booking.application.BookingAppService;
import com.gym.booking.internal.BookingRepository;
import com.gym.course.api.CourseFacade;
import com.gym.shared.audit.AuditLogger;
import com.gym.shared.rule.RuleEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 约课规则单元测试（REQ-B4-001 / SYS-R1、SYS-R3、SYS-R4）。
 * 对应《关键规则—需求—测试溯源表》。
 */
class BookingRulesTest {

    private final BookingRepository repo = mock(BookingRepository.class);
    private final RuleEngine ruleEngine = mock(RuleEngine.class);
    private final CourseFacade courseFacade = mock(CourseFacade.class);
    private final AuditLogger audit = mock(AuditLogger.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final BookingAppService service =
            new BookingAppService(repo, ruleEngine, courseFacade, audit, events);

    private void noExistingBooking() {
        when(repo.findFirstByMemberIdAndCourseIdAndStatusIn(anyLong(), anyLong(), anyCollection()))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("会籍过期时拒绝约课，且不占用名额")
    void reject_when_membership_expired() {
        noExistingBooking();
        when(ruleEngine.checkMembershipEffective(1L))
                .thenReturn(RuleEngine.RuleResult.reject("SYS-R1", "会籍已过期，请先续费"));

        var result = service.book(1L, 10L);

        assertThat(result.ok()).isFalse();
        assertThat(result.ruleCode()).isEqualTo("SYS-R1");
        assertThat(result.reason()).isEqualTo("会籍已过期，请先续费");
        verify(courseFacade, never()).occupySeat(anyLong());
    }

    @Test
    @DisplayName("会籍冻结时以 SYS-R2 拒绝约课")
    void reject_when_membership_frozen() {
        noExistingBooking();
        when(ruleEngine.checkMembershipEffective(1L))
                .thenReturn(RuleEngine.RuleResult.reject("SYS-R2", "会籍冻结中，暂不可约课"));

        var result = service.book(1L, 10L);

        assertThat(result.ok()).isFalse();
        assertThat(result.ruleCode()).isEqualTo("SYS-R2");
        verify(courseFacade, never()).occupySeat(anyLong());
    }

    @Test
    @DisplayName("课程满员时拒绝约课（SYS-R3）")
    void reject_when_course_full() {
        noExistingBooking();
        when(ruleEngine.checkMembershipEffective(1L)).thenReturn(RuleEngine.RuleResult.ok("SYS-R1"));
        when(ruleEngine.checkNoShowPenalty(1L)).thenReturn(RuleEngine.RuleResult.ok("SYS-R4"));
        when(ruleEngine.checkBookingAllowed(1L, 10L))
                .thenReturn(RuleEngine.RuleResult.reject("SYS-R3", "课程已满"));

        var result = service.book(1L, 10L);

        assertThat(result.ok()).isFalse();
        assertThat(result.ruleCode()).isEqualTo("SYS-R3");
        verify(courseFacade, never()).occupySeat(anyLong());
    }

    @Test
    @DisplayName("同一会员同一课程重复提交保持幂等，不占用额外名额")
    void idempotent_when_duplicate_submit() {
        var exist = new com.gym.booking.internal.BookingEntity(1L, 10L);
        when(repo.findFirstByMemberIdAndCourseIdAndStatusIn(eq(1L), eq(10L), anyCollection()))
                .thenReturn(Optional.of(exist));

        var result = service.book(1L, 10L);

        assertThat(result.ok()).isTrue();
        verify(courseFacade, never()).occupySeat(anyLong());
        verify(ruleEngine, never()).checkMembershipEffective(anyLong());
    }

    @Test
    @DisplayName("累计爽约达 3 次时拒绝约课（SYS-R4，N=3）")
    void reject_when_penalty_active() {
        noExistingBooking();
        when(ruleEngine.checkMembershipEffective(1L)).thenReturn(RuleEngine.RuleResult.ok("SYS-R1"));
        when(ruleEngine.checkNoShowPenalty(1L))
                .thenReturn(RuleEngine.RuleResult.reject("SYS-R4", "已被限制预约，剩余 7 天"));

        var result = service.book(1L, 10L);

        assertThat(result.ok()).isFalse();
        assertThat(result.ruleCode()).isEqualTo("SYS-R4");
    }

    @Test
    @DisplayName("校验全部通过时成功创建预约并占用名额")
    void success_path() {
        noExistingBooking();
        when(ruleEngine.checkMembershipEffective(1L)).thenReturn(RuleEngine.RuleResult.ok("SYS-R1"));
        when(ruleEngine.checkNoShowPenalty(1L)).thenReturn(RuleEngine.RuleResult.ok("SYS-R4"));
        when(ruleEngine.checkBookingAllowed(1L, 10L)).thenReturn(RuleEngine.RuleResult.ok("SYS-R3"));
        when(courseFacade.occupySeat(10L)).thenReturn(true);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.book(1L, 10L);

        assertThat(result.ok()).isTrue();
        assertThat(result.status()).isEqualTo("booked");
        verify(courseFacade).occupySeat(10L);
        verify(audit).log(eq("booking.create"), eq("booking"), any(), anyString());
    }

    @Test
    @DisplayName("占位失败（并发下名额被抢）时返回课程已满")
    void reject_when_seat_occupied_by_others() {
        noExistingBooking();
        when(ruleEngine.checkMembershipEffective(1L)).thenReturn(RuleEngine.RuleResult.ok("SYS-R1"));
        when(ruleEngine.checkNoShowPenalty(1L)).thenReturn(RuleEngine.RuleResult.ok("SYS-R4"));
        when(ruleEngine.checkBookingAllowed(1L, 10L)).thenReturn(RuleEngine.RuleResult.ok("SYS-R3"));
        when(courseFacade.occupySeat(10L)).thenReturn(false);

        var result = service.book(1L, 10L);

        assertThat(result.ok()).isFalse();
        assertThat(result.ruleCode()).isEqualTo("SYS-R3");
        assertThat(result.reason()).isEqualTo("课程已满");
    }
}
