/**
 * M4 预约与签到模块（← B4 预约与签到）。
 *
 * <p>职责：约课校验与创建（REQ-B4-001）、扫码/代签（REQ-B4-002）、爽约判定与惩罚（REQ-B4-003/004、SYS-R4）、
 * 取消释放名额（SYS-R3）、前台代签兜底（REQ-B4-005）。
 * <p>对外契约：{@code com.gym.booking.api.BookingFacade}
 * （查询会员有效预约数、统计爽约次数、内部签到入口）。
 * <p>依赖：shared、system，并通过契约调用 membership（会籍有效性）与 course（余位）。
 * <p>数据归属：booking。
 * <p>发布事件：{@code BookingCheckedInEvent}、{@code BookingNoShowEvent}（供 payment/warning 订阅，避免直接耦合）。
 */
package com.gym.booking;
