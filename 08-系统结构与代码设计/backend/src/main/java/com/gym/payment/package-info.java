/**
 * M5 收费与财务模块（← B5 收费与财务）。
 *
 * <p>职责：订单与支付（REQ-B5-001）、支付回调与幂等、异常订单告警（REQ-B5-004）、
 * 课包核销（REQ-B5-002、SYS-R5）、对账（REQ-B5-003）、提成核算（REQ-B5-005、SYS-R10）。
 * <p>对外契约：{@code com.gym.payment.api.PaymentFacade}（创建订单、查询订单状态）。
 * <p>依赖：shared、system，并通过契约通知 membership 延长会籍/增加课包。
 * <p>数据归属：payment_order / settlement / commission_rule / commission_record / course_consumption。
 * <p>外部依赖：微信支付（EXT2/EXT3），由 {@code infrastructure} 适配器隔离，便于 Mock。
 */
package com.gym.payment;
