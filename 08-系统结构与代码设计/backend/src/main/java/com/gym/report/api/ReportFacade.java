package com.gym.report.api;

/** 报表契约。 */
public interface ReportFacade {

    /** 经营摘要（会员/课程/预约/爽约率/异常订单/预警任务） */
    ReportViews.Summary summary();
}
