package com.gym.report;

import com.gym.booking.api.BookingFacade;
import com.gym.course.api.CourseFacade;
import com.gym.course.api.CourseView;
import com.gym.membership.api.MemberView;
import com.gym.membership.api.MembershipFacade;
import com.gym.payment.api.PaymentFacade;
import com.gym.payment.api.PaymentOrderView;
import com.gym.report.application.ReportAppService;
import com.gym.warning.api.WarningFacade;
import com.gym.warning.api.WarningViews;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 经营摘要口径单元测试（爽约率、异常订单、预警任务数）。 */
class ReportRulesTest {

    private final MembershipFacade membership = mock(MembershipFacade.class);
    private final CourseFacade course = mock(CourseFacade.class);
    private final BookingFacade booking = mock(BookingFacade.class);
    private final PaymentFacade payment = mock(PaymentFacade.class);
    private final WarningFacade warning = mock(WarningFacade.class);
    private final ReportAppService service =
            new ReportAppService(membership, course, booking, payment, warning);

    @Test
    @DisplayName("摘要正确汇总会员/课程/预约/爽约率/异常订单/预警任务")
    void summary_aggregates_correctly() {
        when(membership.listMembers()).thenReturn(List.of(
                new MemberView(1L, "M001", "张三", null, "active", null, 1, 0, LocalDateTime.now()),
                new MemberView(2L, "M002", "李四", null, "expired", null, 0, 0, LocalDateTime.now())));
        when(course.listCourses()).thenReturn(List.of(
                new CourseView(11L, "C001", "动感单车", 101L, "A 厅", LocalDateTime.now(),
                        LocalDateTime.now().plusHours(1), 20, 18, 2, "published")));
        when(booking.countByStatus()).thenReturn(Map.of(
                "booked", 3L, "checked_in", 5L, "no_show", 2L));     // 总 10，爽约 2 → 20.0%
        when(payment.listAbnormalOrders()).thenReturn(List.of(
                new PaymentOrderView(1L, "O1", 2L, "membership", 0, null, null, "abnormal", "金额不符",
                        LocalDateTime.now(), null)));
        when(warning.riskScan()).thenReturn(new WarningViews.RiskSummary(2, 1, 1, 1, List.of(
                new WarningViews.RiskTask(1L, "张三", "churn_risk", "SYS-R7", "连续 4 周未到店", "high"))));

        var s = service.summary();

        assertThat(s.members()).isEqualTo(2);
        assertThat(s.activeMembers()).isEqualTo(1);
        assertThat(s.courses()).isEqualTo(1);
        assertThat(s.bookings()).isEqualTo(10);
        assertThat(s.checkedIn()).isEqualTo(5);
        assertThat(s.noShow()).isEqualTo(2);
        assertThat(s.noShowRate()).isEqualTo("20.0%");
        assertThat(s.abnormalOrders()).isEqualTo(1);
        assertThat(s.warningTasks()).isEqualTo(1);
        assertThat(s.highRiskMembers()).isEqualTo(1);
    }

    @Test
    @DisplayName("无预约时爽约率为 0%（不出现除零异常）")
    void no_booking_no_division_error() {
        when(membership.listMembers()).thenReturn(List.of());
        when(course.listCourses()).thenReturn(List.of());
        when(booking.countByStatus()).thenReturn(Map.of());
        when(payment.listAbnormalOrders()).thenReturn(List.of());
        when(warning.riskScan()).thenReturn(new WarningViews.RiskSummary(0, 0, 0, 0, List.of()));

        assertThat(service.summary().noShowRate()).isEqualTo("0%");
    }
}
