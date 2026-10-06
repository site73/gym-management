package com.gym.system.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 演示数据重置（管理员操作）。
 *
 * <p>用 JdbcTemplate 执行一组可移植 SQL，避免跨模块调用各模块的 internal 实现
 * （保持模块边界干净）。作用与 {@code verify/reset-demo-data.sql} 一致。
 */
@Component
public class DemoDataResetter {

    private static final Logger log = LoggerFactory.getLogger(DemoDataResetter.class);

    private final JdbcTemplate jdbc;

    public DemoDataResetter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void reset() {
        int bookings = jdbc.update("delete from booking");
        int orders = jdbc.update("delete from payment_order");
        int settlements = jdbc.update("delete from settlement");
        int audits = jdbc.update("delete from audit_log");

        jdbc.update("update course set booked_count = case id "
                + "when 11 then 18 when 12 then 15 when 13 then 5 else booked_count end");

        jdbc.update("update member set penalty_until = null, status = case id "
                + "when 1 then 'active' when 2 then 'expired' when 3 then 'active' when 4 then 'frozen' "
                + "else status end");

        jdbc.update("update membership set remaining_times = case id "
                + "when 2 then 1 else remaining_times end");

        log.info("[admin] 演示数据已重置：预约 {} 条、订单 {} 条、对账 {} 条、审计 {} 条",
                bookings, orders, settlements, audits);
    }
}
