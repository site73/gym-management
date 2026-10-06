/**
 * M1 会籍管理 + M2 会员服务模块（← B1 会籍管理、B2 会员服务）。
 *
 * <p>职责：会员档案（REQ-B1-001）、办卡/续费（REQ-B1-002）、冻结/过期（SYS-R1/R2）、
 * 到期提醒触发（SYS-R6）、跟进任务与记录（REQ-B2-001/002、SYS-R9）。
 * <p>对外契约：{@code com.gym.membership.api.MembershipFacade}
 * （查询会籍有效性、扣减课包次数、发起跟进任务）。
 * <p>依赖：shared、system。被 booking / payment / warning / report 以接口方式调用。
 * <p>数据归属：member / membership / follow_task。
 */
package com.gym.membership;
