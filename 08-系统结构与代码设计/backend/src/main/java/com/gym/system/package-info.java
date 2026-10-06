/**
 * M0 系统支撑模块（← 业务角色与基础数据）。
 *
 * <p>职责：用户与角色权限（REQ-M0-001）、基础数据维护（字典/课程目录/器材分类，REQ-M0-002）、审计日志与规则参数维护。
 * <p>对外契约：{@code com.gym.system.api.SystemFacade}（查询字典、校验权限、维护规则参数）。
 * <p>依赖：仅依赖 {@code com.gym.shared}。本模块被其他模块依赖，但不得反向依赖任何业务模块。
 * <p>数据归属：sys_user / sys_role / sys_user_role / dict_item / rule_config / audit_log。
 */
package com.gym.system;
