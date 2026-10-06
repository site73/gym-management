package com.gym.membership.api;

/**
 * 会籍模块对外契约（唯一入口）。
 * 其他模块只能通过本接口访问会员/会籍能力，不得直接读写 member / membership 表。
 */
public interface MembershipFacade {

    /** 会籍是否有效（REQ-B4-001 / SYS-R1） */
    boolean isEffective(Long memberId);

    /** 会籍状态原始值：active / frozen / expired（供规则引擎判定） */
    String statusOf(Long memberId);

    /**
     * 私教课包核销（REQ-B5-002 / SYS-R5）。
     *
     * @return 核销后的剩余次数
     */
    int deductPackage(Long memberId, int times);

    /** 课包余次（用于排课前置校验） */
    int packageRemaining(Long memberId);

    /** 发起跟进任务（REQ-B2-001 / SYS-R9、SYS-R7） */
    Long createFollowTask(Long memberId, String taskType, String ruleCode);

    /** 支付成功后延长会籍/增加课包（由支付事件驱动） */
    void activateMembership(Long memberId, String bizType, int packageTimes);

    /** 会员列表（后台管理页用） */
    java.util.List<MemberView> listMembers();

    /** 单个会员详情（会员端"我的信息"用） */
    MemberView memberOf(Long memberId);

    /**
     * 会籍到期提醒扫描（SYS-R6，D=7）。
     *
     * @param daysBefore 提前天数
     * @return 需要提醒的会员姓名列表
     */
    java.util.List<String> remindExpiring(int daysBefore);

    /**
     * 施加爽约限制（SYS-R4）。
     *
     * @param days 限制天数
     */
    void applyPenalty(Long memberId, int days);

    /** 预约限制剩余天数；0 表示未被限制 */
    int penaltyDaysRemaining(Long memberId);

    /** 会员是否存在 */
    boolean exists(Long memberId);
}
