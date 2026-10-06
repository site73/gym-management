/**
 * M7 体测与数据模块（← B7 体测与数据）。
 *
 * <p>职责：体测建档（REQ-B7-001）、指标录入（身高/体重/BMI/体脂率/围度）、运动档案与历史对比（REQ-B7-002）。
 * <p>对外契约：{@code com.gym.assessment.api.AssessmentFacade}（查询会员体测档案）。
 * <p>依赖：shared、system。被 report 以接口方式调用。
 * <p>数据归属：assessment。
 * <p>合规：属健康类敏感数据，接口须做权限校验与脱敏输出。
 */
package com.gym.assessment;
