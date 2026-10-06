package com.gym.shared.rule;

import java.util.Map;

/**
 * 规则引擎契约（SYS-R1–SYS-R10）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>规则参数外置在数据库 {@code rule_config}，本接口只负责"取参数 + 执行策略"，不含硬编码阈值；</li>
 *   <li>改阈值（N/D/T、时间窗、提成比例）只需改数据，无需发版；</li>
 *   <li>执行结果需可追溯（哪条规则、作用于谁、何时、结果）。</li>
 * </ul>
 *
 * <p>实现位于 {@code com.gym.shared.rule.internal}，业务模块只能通过本接口使用规则能力。
 */
public interface RuleEngine {

    /** 读取规则参数，如 SYS-R4 的 {"N":3,"restrictDays":7}。 */
    Map<String, Object> paramsOf(String ruleCode);

    /**
     * 执行"会籍有效性"判定（SYS-R1）。
     *
     * @param memberId 会员 ID
     * @return 判定结果与原因（原因用于前端提示与审计留痕）
     */
    RuleResult checkMembershipEffective(Long memberId);

    /**
     * 执行"约课校验"（SYS-R3：容量 + 时间冲突）。
     */
    RuleResult checkBookingAllowed(Long memberId, Long courseId);

    /**
     * 执行"爽约惩罚"判定（SYS-R4：累计爽约 ≥ N → 限制预约 D 天）。
     */
    RuleResult checkNoShowPenalty(Long memberId);

    /** 规则结果：passed=false 时 reason 必填，供提示与追溯。 */
    record RuleResult(boolean passed, String ruleCode, String reason) {
        public static RuleResult ok(String ruleCode) {
            return new RuleResult(true, ruleCode, null);
        }
        public static RuleResult reject(String ruleCode, String reason) {
            return new RuleResult(false, ruleCode, reason);
        }
    }
}
