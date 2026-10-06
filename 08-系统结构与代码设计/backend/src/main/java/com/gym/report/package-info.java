/**
 * M8 报表与工单模块（← B8 运营报表与预警）。
 *
 * <p>职责：经营报表 RP1–RP9（REQ-B8-009）、投诉/报修/受伤工单闭环（REQ-B8-007/008）。
 * <p>对外契约：{@code com.gym.report.api.ReportFacade}（按角色获取报表数据）。
 * <p>依赖：shared、system；通过各模块契约读取统计口径（不直连对方数据库）。
 * <p>数据归属：ticket / ticket_log；报表为只读聚合，不落地业务主数据。
 * <p>口径：严格遵循《输出报表与统计需求说明》的统一口径，避免多口径。
 */
package com.gym.report;
