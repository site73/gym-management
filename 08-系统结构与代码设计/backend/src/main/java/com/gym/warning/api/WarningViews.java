package com.gym.warning.api;

import java.util.List;

/** 预警模块对外视图（嵌套记录集中声明，避免过多小文件）。 */
public final class WarningViews {

    private WarningViews() {}

    /** 一条跟进/预警任务（SYS-R7 / SYS-R9） */
    public record RiskTask(
            Long memberId,
            String memberName,
            String taskType,     // churn_risk / new_member_30d
            String ruleCode,     // SYS-R7 / SYS-R9
            String hit,          // 命中原因（可读）
            String level) {}     // high / low

    /** 风险扫描结果 */
    public record RiskSummary(
            int totalMembers,
            int highRisk,
            int churnTasks,
            int newMemberTasks,
            List<RiskTask> tasks) {}

    /** 爽约预测单行（SYS-R8） */
    public record PredictionRow(
            Long bookingId,
            Long memberId,
            String memberName,
            double prob,
            String action) {}    // reminder: remind / release / none

    /** 爽约预测结果 */
    public record PredictionResult(
            Long courseId,
            String courseName,
            double threshold,
            int remaining,
            List<PredictionRow> rows) {}
}
