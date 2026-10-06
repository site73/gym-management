package com.gym.assessment.api;

import java.util.List;

/** 私教业绩与提成视图（SYS-R10）。 */
public final class CommissionViews {

    private CommissionViews() {}

    /**
     * 教练提成行。
     *
     * @param coachId    教练 ID
     * @param times      近 N 天已核销课时数
     * @param unitPrice  单节课时费基准（元）
     * @param rate       提成比例（来自 rule_config SYS-R10，可配置）
     * @param commission 提成金额（元）
     */
    public record CommissionRow(Long coachId, long times, int unitPrice, double rate, double commission) {}

    /** 提成核算结果 */
    public record CommissionReport(String period, int days, double rate, double totalCommission,
                                   List<CommissionRow> rows) {}
}
