package com.gym.assessment.api;

/** 私教业绩/提成契约（SYS-R10）。 */
public interface CommissionFacade {

    /**
     * 核算近 N 天提成。
     *
     * @param days 统计窗口（天）
     */
    CommissionViews.CommissionReport monthly(int days);
}
