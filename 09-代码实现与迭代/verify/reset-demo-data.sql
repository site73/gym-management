-- 重置演示业务数据（MySQL 持久库下保证冒烟测试可重复执行）
-- 保留基础数据（member / membership / course / rule_config / dict_item），仅清理交易数据并复位计数
DELETE FROM booking;
DELETE FROM payment_order;
DELETE FROM settlement;
DELETE FROM audit_log;

-- 复位课程已约人数（与 07/db/seed/R__seed_base_data.sql 的初始值一致）
UPDATE course SET booked_count = CASE id
    WHEN 11 THEN 18
    WHEN 12 THEN 15
    WHEN 13 THEN 5
    ELSE booked_count END;

-- 复位会员状态与课包（避免上一轮支付/核销影响）
UPDATE member SET status = CASE id
    WHEN 1 THEN 'active'
    WHEN 2 THEN 'expired'
    WHEN 3 THEN 'active'
    WHEN 4 THEN 'frozen'
    ELSE status END;
UPDATE membership SET remaining_times = CASE id
    WHEN 2 THEN 1
    ELSE remaining_times END;
