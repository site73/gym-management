package com.gym.booking;

import com.gym.booking.application.BookingAppService;
import com.gym.booking.internal.BookingRepository;
import com.gym.course.api.CourseFacade;
import com.gym.shared.audit.AuditLogger;
import com.gym.shared.rule.RuleEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    @DisplayName("会籍过期时拒绝约课，且不占用名额")
    void reject_when_membership_expired() {
        when(repo.findByMemberIdAndCourseId(1L, 10L)).thenReturn(java.util.Optional.empty());
        when(ruleEngine.checkMembershipEffective(1L))
                .thenReturn(RuleEngine.RuleResult.reject("SYS-R1", "会籍已过期，请先续费"));

        var result = service.book(1L, 10L);

        assertThat(result.ok()).isFalse();
        assertThat(result.ruleCode()).isEqualTo("SYS-R1");
        assertThat(result.reason()).isEqualTo("会籍已过期，请先续费");
        verify(courseFacade, never()).occupySeat(anyLong());
    }

    @Test
    @DisplayName("课程满员时拒绝约课（SYS-R3）")
    void reject_when_course_full() {
        when(repo.findByMemberIdAndCourseId(1L, 10L)).thenReturn(java.util.Optional.empty());
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
        when(repo.findByMemberIdAndCourseId(1L, 10L)).thenReturn(java.util.Optional.of(exist));

        var result = service.book(1L, 10L);

        assertThat(result.ok()).isTrue();
        verify(courseFacade, never()).occupySeat(anyLong());
        verify(ruleEngine, never()).checkMembershipEffective(anyLong());
    }

    @Test
    @DisplayName("累计爽约达 3 次时拒绝约课（SYS-R4，N=3）")
    void reject_when_penalty_active() {
        when(repo.findByMemberIdAndCourseId(1L, 10L)).thenReturn(java.util.Optional.empty());
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
        when(repo.findByMemberIdAndCourseId(1L, 10L)).thenReturn(java.util.Optional.empty());
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
}
