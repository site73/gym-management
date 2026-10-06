package com.gym.shared.rule.internal;

import com.gym.shared.rule.RuleEngine;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 规则引擎实现（SYS-R1–R10）。
 *
 * <p>参数全部来自数据库 {@code rule_config}（见 07 的种子数据），
 * 本类只负责"取参数 + 执行策略"，不硬编码任何阈值。
 *
 * <p>说明：会籍/课程的判定需要跨模块数据，本实现通过构造器注入的查询接口
 * （由对应模块的 Facade 提供）获取，避免直接读对方数据表。
 */
@Component
public class RuleEngineImpl implements RuleEngine {

    private final RuleConfigRepository ruleConfigRepository;
    private final MembershipQuery membershipQuery;   // 由 membership 模块提供的只读查询
    private final CourseQuery courseQuery;           // 由 course 模块提供的只读查询
    private final BookingQuery bookingQuery;         // 由 booking 模块自身提供

    public RuleEngineImpl(RuleConfigRepository ruleConfigRepository,
                          MembershipQuery membershipQuery,
                          CourseQuery courseQuery,
                          BookingQuery bookingQuery) {
        this.ruleConfigRepository = ruleConfigRepository;
        this.membershipQuery = membershipQuery;
        this.courseQuery = courseQuery;
        this.bookingQuery = bookingQuery;
    }

    @Override
    public Map<String, Object> paramsOf(String ruleCode) {
        return ruleConfigRepository.findByRuleCode(ruleCode)
                .map(cfg -> cfg.getParamsJson())
                .orElseThrow(() -> new IllegalStateException("规则参数未配置：" + ruleCode));
    }

    /** SYS-R1 会籍有效性 */
    @Override
    public RuleResult checkMembershipEffective(Long memberId) {
        String status = membershipQuery.statusOf(memberId);
        if ("active".equals(status)) {
            return RuleResult.ok("SYS-R1");
        }
        String reason = "frozen".equals(status) ? "会籍冻结中，暂不可约课" : "会籍已过期，请先续费";
        return RuleResult.reject("frozen".equals(status) ? "SYS-R2" : "SYS-R1", reason);
    }

    /** SYS-R4 爽约惩罚：存在生效中的限制 → 拒绝；否则按累计爽约次数判断 */
    @Override
    public RuleResult checkNoShowPenalty(Long memberId) {
        int left = membershipQuery.penaltyDaysRemaining(memberId);
        if (left > 0) {
            return RuleResult.reject("SYS-R4", "已被限制预约，剩余 " + left + " 天");
        }
        if (bookingQuery.noShowCount(memberId) >= penaltyThreshold()) {
            return RuleResult.reject("SYS-R4", "累计爽约已达 " + penaltyThreshold() + " 次，暂不可约课");
        }
        return RuleResult.ok("SYS-R4");
    }

    @Override
    public int penaltyThreshold() { return intParam("SYS-R4", "N", 3); }

    @Override
    public int penaltyDays() { return intParam("SYS-R4", "restrictDays", 7); }

    @Override
    public double predictionThreshold() { return doubleParam("SYS-R8", "T", 0.60); }

    @Override
    public int newMemberDays() { return intParam("SYS-R9", "days", 30); }

    @Override
    public int newMemberMinVisits() { return intParam("SYS-R9", "minVisits", 2); }

    @Override
    public int noVisitWeeks() { return intParam("SYS-R7", "noVisitWeeks", 4); }

    @Override
    public double riskNoShowRate() { return doubleParam("SYS-R7", "noShowRate", 0.30); }

    @Override
    public double commissionRate() { return doubleParam("SYS-R10", "rate", 0.20); }

    /** SYS-R3 约课校验：容量 + 时间冲突 */
    @Override
    public RuleResult checkBookingAllowed(Long memberId, Long courseId) {
        if (courseQuery.remainingSeats(courseId) <= 0) {
            return RuleResult.reject("SYS-R3", "课程已满");
        }
        Long conflictCourseId = bookingQuery.conflictCourseId(memberId, courseId);
        if (conflictCourseId != null) {
            return RuleResult.reject("SYS-R3", "该时段已有预约，存在时间冲突");
        }
        return RuleResult.ok("SYS-R3");
    }

    private int intParam(String ruleCode, String key, int defaultValue) {
        Object v = paramsOf(ruleCode).get(key);
        return v instanceof Number num ? num.intValue() : defaultValue;
    }

    private double doubleParam(String ruleCode, String key, double defaultValue) {
        Object v = paramsOf(ruleCode).get(key);
        return v instanceof Number num ? num.doubleValue() : defaultValue;
    }

    /* ---- 跨模块只读查询接口（由各模块实现，避免直接访问对方数据表） ---- */
    public interface MembershipQuery {
        String statusOf(Long memberId);
        /** SYS-R4：预约限制剩余天数（0 = 未被限制） */
        int penaltyDaysRemaining(Long memberId);
    }
    public interface CourseQuery { int remainingSeats(Long courseId); }
    public interface BookingQuery {
        int noShowCount(Long memberId);
        Long conflictCourseId(Long memberId, Long courseId);
    }
}
