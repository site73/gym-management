/**
 * 预警模块（← B8 运营报表与预警；微创新点）。
 *
 * <p>职责：流失风险评分（REQ-B8-001/002、SYS-R7）、爽约预测（REQ-B8-003/004/005、SYS-R8）、
 * 预警任务生成与超时升级（REQ-B8-006）。
 * <p>对外契约：{@code com.gym.warning.api.WarningFacade}（查询风险清单、触发评分）。
 * <p>依赖：shared、system，并通过契约读取 membership（到店/会籍）与 booking（爽约统计）的数据视图。
 * <p>数据归属：risk_score / warning_task / booking_prediction。
 * <p>演进约定：先规则式（rule-v1，参数取 rule_config），数据充足后再引入模型，
 * 表字段 {@code model_ver} 与 {@code detail_json} 保证可解释与可回溯。
 */
package com.gym.warning;
