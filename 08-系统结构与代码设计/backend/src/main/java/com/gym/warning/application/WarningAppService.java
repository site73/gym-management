package com.gym.warning.application;

import com.gym.booking.api.BookingFacade;
import com.gym.booking.api.BookingView;
import com.gym.course.api.CourseFacade;
import com.gym.course.api.CourseView;
import com.gym.membership.api.MemberView;
import com.gym.membership.api.MembershipFacade;
import com.gym.shared.audit.AuditLogger;
import com.gym.shared.rule.RuleEngine;
import com.gym.warning.api.WarningFacade;
import com.gym.warning.api.WarningViews;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预警用例（创新点）。
 *
 * <p>算法与 09 阶段 Node 参考实现保持一致（照搬，便于对照与回归）：
 * <ul>
 *   <li>SYS-R7 流失风险：连续未到店周数 或 近 30 天爽约率超阈值 → 高风险 + 跟进任务</li>
 *   <li>SYS-R8 爽约预测：0.1 基线 + 爽约占比×0.6 + 久未到店+0.2 + 新会员+0.15，上限 0.99</li>
 *   <li>SYS-R9 新会员首月跟进：办卡 ≤ D 天且到店次数 &lt; minVisits</li>
 * </ul>
 *
 * <p>阈值全部来自 {@code rule_config}（SYS-R7/R8/R9），改阈值无需发版。
 *
 * <p>跨模块数据一律经 Facade 获取，不直连对方数据表。
 */
@Service
public class WarningAppService implements WarningFacade {

    private final MembershipFacade membershipFacade;
    private final BookingFacade bookingFacade;
    private final CourseFacade courseFacade;
    private final RuleEngine ruleEngine;
    private final AuditLogger auditLogger;

    public WarningAppService(MembershipFacade membershipFacade, BookingFacade bookingFacade,
                             CourseFacade courseFacade, RuleEngine ruleEngine, AuditLogger auditLogger) {
        this.membershipFacade = membershipFacade;
        this.bookingFacade = bookingFacade;
        this.courseFacade = courseFacade;
        this.ruleEngine = ruleEngine;
        this.auditLogger = auditLogger;
    }

    /* ==================== SYS-R7 / SYS-R9：风险扫描 ==================== */

    @Override
    public WarningViews.RiskSummary riskScan() {
        List<MemberView> members = membershipFacade.listMembers();
        List<WarningViews.RiskTask> tasks = new ArrayList<>();
        int highRisk = 0, churn = 0, newMember = 0;

        for (MemberView m : members) {
            List<String> hits = new ArrayList<>();

            int lastVisitDays = stats(m.id()).lastVisitDays;
            if (lastVisitDays >= ruleEngine.noVisitWeeks() * 7) {
                hits.add("连续 " + ruleEngine.noVisitWeeks() + " 周未到店");
            }
            long bookings30 = bookingFacade.countBookings(m.id(), 30);
            long noShow30 = bookingFacade.countNoShow(m.id(), 30);
            if (bookings30 > 0 && (double) noShow30 / bookings30 > ruleEngine.riskNoShowRate()) {
                hits.add("近 30 天爽约率 " + Math.round(noShow30 * 100.0 / bookings30) + "%");
            }

            if (!hits.isEmpty()) {
                highRisk++;
                churn++;
                tasks.add(new WarningViews.RiskTask(m.id(), m.name(), "churn_risk", "SYS-R7",
                        String.join("；", hits), "high"));
            }

            int createdDaysAgo = m.createdAt() == null ? 999
                    : (int) ChronoUnit.DAYS.between(m.createdAt(), LocalDateTime.now());
            long visits30 = bookingFacade.countCheckedIn(m.id(), 30);
            if (createdDaysAgo <= ruleEngine.newMemberDays() && visits30 < ruleEngine.newMemberMinVisits()) {
                newMember++;
                tasks.add(new WarningViews.RiskTask(m.id(), m.name(), "new_member_30d", "SYS-R9",
                        "办卡 " + createdDaysAgo + " 天、到店 " + visits30 + " 次", "mid"));
            }
        }

        auditLogger.log("job.riskScore", "risk", null,
                "会员=" + members.size() + ", 高风险=" + highRisk + ", 任务=" + tasks.size());
        return new WarningViews.RiskSummary(members.size(), highRisk, churn, newMember, tasks);
    }

    /* ==================== SYS-R8：爽约预测 ==================== */

    @Override
    public WarningViews.PredictionResult predict(Long courseId) {
        List<CourseView> courses = courseFacade.listCourses();
        if (courses.isEmpty()) throw new IllegalStateException("暂无课程");
        CourseView course = (courseId == null)
                ? courses.get(0)
                : courses.stream().filter(c -> c.id().equals(courseId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("课程不存在：" + courseId));

        Map<Long, String> nameOf = new HashMap<>();
        for (MemberView m : membershipFacade.listMembers()) nameOf.put(m.id(), m.name());

        boolean full = course.remaining() <= 0;
        double threshold = ruleEngine.predictionThreshold();
        List<WarningViews.PredictionRow> rows = new ArrayList<>();

        for (BookingView b : bookingFacade.listBookings(null)) {
            if (!course.id().equals(b.courseId()) || !"booked".equals(b.status())) continue;
            double prob = predictProb(b.memberId());
            String action = prob > threshold ? (full ? "release" : "remind") : "none";
            rows.add(new WarningViews.PredictionRow(b.id(), b.memberId(),
                    nameOf.getOrDefault(b.memberId(), "会员" + b.memberId()), prob, action));
        }
        rows.sort((x, y) -> Double.compare(y.prob(), x.prob()));

        auditLogger.log("job.predict", "course", course.id(), "已约=" + rows.size());
        return new WarningViews.PredictionResult(course.id(), course.name(), threshold,
                course.remaining(), rows);
    }

    /* ==================== 概率算法（与 Node 参考实现一致） ==================== */

    private double predictProb(Long memberId) {
        Stats s = stats(memberId);
        long total = s.visits30 + s.noShow30;
        double p = 0.1;
        if (total > 0) p += ((double) s.noShow30 / total) * 0.6;
        if (s.lastVisitDays > 14) p += 0.2;
        if (s.createdDaysAgo <= 30) p += 0.15;
        return Math.min(0.99, Math.round(p * 1000) / 1000.0);
    }

    private record Stats(long visits30, long noShow30, int lastVisitDays, int createdDaysAgo) {}

    private Stats stats(Long memberId) {
        Integer last = bookingFacade.daysSinceLastCheckin(memberId);
        // 从未签到：视为长期未到店（999 天），与"高风险"语义一致
        int lastVisitDays = last == null ? 999 : last;
        var mv = membershipFacade.exists(memberId) ? membershipFacade.memberOf(memberId) : null;
        int createdDaysAgo = (mv == null || mv.createdAt() == null) ? 999
                : (int) ChronoUnit.DAYS.between(mv.createdAt(), LocalDateTime.now());
        return new Stats(bookingFacade.countCheckedIn(memberId, 30),
                bookingFacade.countNoShow(memberId, 30), lastVisitDays, createdDaysAgo);
    }
}
