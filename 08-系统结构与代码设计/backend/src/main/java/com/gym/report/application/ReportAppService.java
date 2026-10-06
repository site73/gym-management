package com.gym.report.application;

import com.gym.booking.api.BookingFacade;
import com.gym.course.api.CourseFacade;
import com.gym.membership.api.MemberView;
import com.gym.membership.api.MembershipFacade;
import com.gym.payment.api.PaymentFacade;
import com.gym.report.api.ReportFacade;
import com.gym.report.api.ReportViews;
import com.gym.warning.api.WarningFacade;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 经营摘要（跨模块只经 Facade 取值，无重复计算口径）。 */
@Service
public class ReportAppService implements ReportFacade {

    private final MembershipFacade membershipFacade;
    private final CourseFacade courseFacade;
    private final BookingFacade bookingFacade;
    private final PaymentFacade paymentFacade;
    private final WarningFacade warningFacade;

    public ReportAppService(MembershipFacade membershipFacade, CourseFacade courseFacade,
                            BookingFacade bookingFacade, PaymentFacade paymentFacade,
                            WarningFacade warningFacade) {
        this.membershipFacade = membershipFacade;
        this.courseFacade = courseFacade;
        this.bookingFacade = bookingFacade;
        this.paymentFacade = paymentFacade;
        this.warningFacade = warningFacade;
    }

    @Override
    public ReportViews.Summary summary() {
        List<MemberView> members = membershipFacade.listMembers();
        int active = (int) members.stream().filter(m -> "active".equals(m.status())).count();

        Map<String, Long> byStatus = bookingFacade.countByStatus();
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long checkedIn = byStatus.getOrDefault("checked_in", 0L);
        long noShow = byStatus.getOrDefault("no_show", 0L);

        var risk = warningFacade.riskScan();

        return new ReportViews.Summary(
                members.size(),
                active,
                courseFacade.listCourses().size(),
                total,
                checkedIn,
                noShow,
                total == 0 ? "0%" : String.format("%.1f%%", noShow * 100.0 / total),
                paymentFacade.listAbnormalOrders().size(),
                risk.tasks().size(),
                risk.highRisk());
    }
}
