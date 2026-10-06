package com.gym.warning.api;

/**
 * 预警模块对外契约（创新点：SYS-R7 流失风险 / SYS-R8 爽约预测 / SYS-R9 新会员跟进）。
 */
public interface WarningFacade {

    /** 执行风险扫描：返回高风险与新会员跟进任务（SYS-R7 / SYS-R9） */
    WarningViews.RiskSummary riskScan();

    /** 爽约预测：给出某课程下每位已约会员的爽约概率与建议动作（SYS-R8） */
    WarningViews.PredictionResult predict(Long courseId);
}
