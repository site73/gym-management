package com.gym.report.api;

/** 报表模块对外视图。 */
public final class ReportViews {

    private ReportViews() {}

    /** 经营摘要（RP 系列报表的汇总口径） */
    public record Summary(
            int members,
            int activeMembers,
            int courses,
            long bookings,
            long checkedIn,
            long noShow,
            String noShowRate,
            int abnormalOrders,
            int warningTasks,
            int highRiskMembers) {}
}
