/**
 * 共享内核（shared）—— 被所有业务模块允许依赖的唯一公共包。
 *
 * <p>提供：
 * <ul>
 *   <li>{@code rule}  规则引擎与规则参数读取（SYS-R1–R10，来源 rule_config）</li>
 *   <li>{@code event} 领域事件定义与发布（模块间解耦通信）</li>
 *   <li>{@code audit} 审计日志（权限/状态/财务操作留痕）</li>
 *   <li>{@code common} 通用类型：统一返回、错误码、分页、业务异常</li>
 * </ul>
 *
 * <p>约束：本包<b>不得</b>依赖任何业务模块；不得包含业务规则实现（规则以参数+策略形式提供）。
 */
package com.gym.shared;
