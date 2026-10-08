package com.gym.warning;

import com.gym.booking.api.BookingFacade;
import com.gym.booking.api.BookingView;
import com.gym.course.api.CourseFacade;
import com.gym.course.api.CourseView;
import com.gym.membership.api.MemberView;
import com.gym.membership.api.MembershipFacade;
import com.gym.shared.audit.AuditLogger;
import com.gym.shared.rule.RuleEngine;
import com.gym.warning.application.WarningAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 预警规则单元测试（创新点）：SYS-R7 流失风险 / SYS-R8 爽约预测 / SYS-R9 新会员跟进。
 *
 * <p>阈值全部取自 RuleEngine（即 rule_config），测试中固定为 N/D/T 的默认口径。
 */
class WarningRulesTest {

    private final MembershipFacade membership = mock(MembershipFacade.class);
    private final BookingFacade booking = mock(BookingFacade.class);
    private final CourseFacade course = mock(CourseFacade.class);
    private final RuleEngine rules = mock(RuleEngine.class);
    private final AuditLogger audit = mock(AuditLogger.class);
    private final WarningAppService service =
            new WarningAppService(membership, booking, course, rules, audit);

    private static final LocalDateTime NOW = LocalDateTime.now();

    private MemberView member(long id, String name, int createdDaysAgo) {
        return new MemberView(id, "M00" + id, name, null, "active", null, 0, 0,
                NOW.minusDays(createdDaysAgo));
    }

    @BeforeEach
    void setUp() {
        when(rules.noVisitWeeks()).thenReturn(4);          // SYS-R7：连续 4 周未到店 = 28 天
        when(rules.riskNoShowRate()).thenReturn(0.30);     // SYS-R7：爽约率阈值 30%
        when(rules.predictionThreshold()).thenReturn(0.60);// SYS-R8：T=0.6
        when(rules.newMemberDays()).thenReturn(30);        // SYS-R9：30 天
        when(rules.newMemberMinVisits()).thenReturn(2);    // SYS-R9：至少 2 次
    }

    @Test
    @DisplayName("连续 4 周未到店 → 命中 SYS-R7，标记高风险")
    void churn_risk_when_long_absent() {
        when(membership.listMembers()).thenReturn(List.of(member(1, "张三", 200)));
        when(booking.daysSinceLastCheckin(1L)).thenReturn(40);       // 40 天 > 28 天
        when(booking.countBookings(anyLong(), anyInt())).thenReturn(0L);
        when(booking.countNoShow(anyLong(), anyInt())).thenReturn(0L);
        when(booking.countCheckedIn(anyLong(), anyInt())).thenReturn(5L);   // 老会员，不触发 R9

        var r = service.riskScan();

        assertThat(r.highRisk()).isEqualTo(1);
        assertThat(r.tasks()).anyMatch(t -> "SYS-R7".equals(t.ruleCode())
                && t.hit().contains("未到店") && "high".equals(t.level()));
    }

    @Test
    @DisplayName("近 30 天爽约率超阈值 → 命中 SYS-R7")
    void churn_risk_when_high_no_show_rate() {
        when(membership.listMembers()).thenReturn(List.of(member(2, "李四", 200)));
        when(booking.daysSinceLastCheckin(2L)).thenReturn(1);        // 最近来过，不触发"未到店"
        when(booking.countBookings(2L, 30)).thenReturn(4L);
        when(booking.countNoShow(2L, 30)).thenReturn(2L);            // 50% > 30%
        when(booking.countCheckedIn(anyLong(), anyInt())).thenReturn(5L);

        var r = service.riskScan();

        assertThat(r.tasks()).anyMatch(t -> "SYS-R7".equals(t.ruleCode()) && t.hit().contains("爽约率"));
    }

    @Test
    @DisplayName("新会员首月到店不足 → 命中 SYS-R9")
    void new_member_follow_up() {
        when(membership.listMembers()).thenReturn(List.of(member(3, "王五", 10)));   // 办卡 10 天
        when(booking.daysSinceLastCheckin(anyLong())).thenReturn(3);
        when(booking.countBookings(anyLong(), anyInt())).thenReturn(0L);
        when(booking.countNoShow(anyLong(), anyInt())).thenReturn(0L);
        when(booking.countCheckedIn(anyLong(), anyInt())).thenReturn(1L);            // 1 < 2

        var r = service.riskScan();

        assertThat(r.newMemberTasks()).isEqualTo(1);
        assertThat(r.tasks()).anyMatch(t -> "SYS-R9".equals(t.ruleCode()));
    }

    @Test
    @DisplayName("活跃老会员无预警任务")
    void healthy_member_has_no_task() {
        when(membership.listMembers()).thenReturn(List.of(member(4, "赵六", 300)));
        when(booking.daysSinceLastCheckin(anyLong())).thenReturn(2);
        when(booking.countBookings(anyLong(), anyInt())).thenReturn(10L);
        when(booking.countNoShow(anyLong(), anyInt())).thenReturn(1L);   // 10% < 30%
        when(booking.countCheckedIn(anyLong(), anyInt())).thenReturn(9L);

        var r = service.riskScan();

        assertThat(r.tasks()).isEmpty();
        assertThat(r.highRisk()).isZero();
    }

    @Test
    @DisplayName("SYS-R8：高概率 + 课程已满 → 建议释放名额；未满 → 建议提醒")
    void prediction_actions() {
        CourseView full = new CourseView(12L, "C002", "瑜伽", "group", 102L, "B 厅",
                NOW, NOW.plusHours(1), 15, 15, 0, "full");
        when(course.listCourses()).thenReturn(List.of(full));
        when(membership.listMembers()).thenReturn(List.of(member(1, "张三", 200)));
        when(booking.listBookings(null)).thenReturn(List.of(
                new BookingView(9L, 1L, 12L, "booked", null, null, NOW, null)));
        when(booking.countCheckedIn(anyLong(), anyInt())).thenReturn(0L);
        when(booking.countNoShow(anyLong(), anyInt())).thenReturn(5L);   // 高爽约 → 高概率
        when(booking.daysSinceLastCheckin(anyLong())).thenReturn(30);

        var r = service.predict(12L);

        assertThat(r.threshold()).isEqualTo(0.60);
        assertThat(r.rows()).hasSize(1);
        assertThat(r.rows().get(0).prob()).isGreaterThan(0.6);
        assertThat(r.rows().get(0).action()).isEqualTo("release");
    }

    @Test
    @DisplayName("SYS-R8：低概率 → 无需处理")
    void prediction_none_for_low_risk() {
        CourseView open = new CourseView(11L, "C001", "动感单车", "group", 101L, "A 厅",
                NOW, NOW.plusHours(1), 20, 18, 2, "published");
        when(course.listCourses()).thenReturn(List.of(open));
        when(membership.listMembers()).thenReturn(List.of(member(1, "张三", 400)));
        when(booking.listBookings(null)).thenReturn(List.of(
                new BookingView(9L, 1L, 11L, "booked", null, null, NOW, null)));
        when(booking.countCheckedIn(anyLong(), anyInt())).thenReturn(20L);
        when(booking.countNoShow(anyLong(), anyInt())).thenReturn(0L);
        when(booking.daysSinceLastCheckin(anyLong())).thenReturn(1);

        var r = service.predict(11L);

        assertThat(r.rows().get(0).prob()).isLessThan(0.6);
        assertThat(r.rows().get(0).action()).isEqualTo("none");
    }
}
