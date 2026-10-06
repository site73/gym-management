/**
 * M6 器材与场地模块（← B6 器材与场地）。
 *
 * <p>职责：器材台账、状态变更（REQ-B6-001）、场地占用登记、报修工单入口。
 * <p>对外契约：{@code com.gym.equipment.api.EquipmentFacade}（查询器材可用状态）。
 * <p>依赖：shared、system。属于独立性最高的切片（S5），可最后接入而不影响 S1。
 * <p>数据归属：equipment / equipment_log。
 */
package com.gym.equipment;
