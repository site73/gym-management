package com.gym.system.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 演示数据对齐（保证「周课表」有内容、且**报名名单与课程余位严格闭合**）。
 *
 * <p><b>为什么需要（一）</b>：种子脚本里的演示课程用 {@code CURDATE()} 落库，但 Flyway 的
 * R__ 脚本只在内容变化时重跑，时间一长这些课程就漂移到过去的某一周，
 * 于是「本周课表」「场馆占用课表」全是空的——功能没坏，演示却像坏了。
 *
 * <p><b>为什么需要（二）</b>：种子里 {@code course.booked_count} 是直接写死的（18/15/5），
 * 却没有对应的 {@code booking} 行，于是出现「课程显示 15/15 已满，报名名单却是共 0 人」的
 * <b>账实不符</b>。报名名单的数据源是 booking 表，余位的数据源是 booked_count，
 * 两者必须闭合：{@code booked_count == count(booking where status in ('booked','checked_in'))}。
 *
 * <p><b>做法与边界</b>：
 * <ul>
 *   <li>排课 / 场地对齐：只在<b>本周没有任何排课 / 占用</b>时才介入，已有真实数据立刻退出；</li>
 *   <li>名册对齐：只为 3 门基础演示课程（id 11/12/13）从<b>演示会员池</b>（member.source='demo'，
 *       V13 建立）补齐 booking 行，不碰真实会员的任何记录；</li>
 *   <li>闭合重算：对<b>所有</b>课程把 {@code booked_count} 重算为有效预约数——
 *       这是防御性收口，即使将来某条路径漏记，也不会再出现"账实不符"。</li>
 * </ul>
 */
@Component
public class DemoScheduleAligner {

    private static final Logger log = LoggerFactory.getLogger(DemoScheduleAligner.class);

    private final JdbcTemplate jdbc;

    public DemoScheduleAligner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            align();
        } catch (RuntimeException e) {
            // 演示数据的便利性不能拖垮启动：失败只记录，不影响系统可用
            log.warn("[demo] 演示排课对齐跳过：{}", e.getMessage());
        }
    }

    /** 对齐：课程时间 + 场地占用 + 报名名册（名册闭合每次都跑，代价是几条 SQL） */
    @Transactional
    public void align() {
        LocalDate monday = mondayOf(LocalDate.now());
        LocalDateTime from = monday.atStartOfDay();
        LocalDateTime to = monday.plusDays(7).atStartOfDay();

        boolean coursesMoved = alignCourses(from, to, monday);
        boolean venuesAdded = alignVenueBookings(from, to, monday);
        int fixed = alignRoster();
        if (coursesMoved || venuesAdded || fixed > 0) {
            log.info("[demo] 演示数据已对齐（{} ~ {}）：补报名 {} 条",
                    monday, monday.plusDays(6), fixed);
        }
    }

    private static final int MIN_FUTURE_COURSES = 3;

    /**
     * 本周还能报名的课不足 3 门 → 把 C001/C002/C003 挪到「今天及以后」的最近几天。
     *
     * <p>只认未来排课，是因为过期课程既选不进去、也演示不了签到，留着等于演示事故；
     * 阈值取 3 而不是 1，是为了让本周课表一眼看上去有内容，而不是孤零零一条。
     */
    private boolean alignCourses(LocalDateTime from, LocalDateTime to, LocalDate monday) {
        Integer n = jdbc.queryForObject(
                "select count(*) from course where start_time >= ? and start_time < ? and status <> 'cancelled'",
                Integer.class, LocalDateTime.now(), to);
        if (n != null && n >= MIN_FUTURE_COURSES) return false;

        List<LocalDate> days = upcomingDays(monday, 3);
        move(11, days.get(0), LocalTime.of(19, 0), LocalTime.of(20, 0));  // 动感单车
        move(12, days.get(1), LocalTime.of(18, 0), LocalTime.of(19, 0));  // 瑜伽（满员，用于演示容量校验）
        move(13, days.get(2), LocalTime.of(20, 0), LocalTime.of(21, 0));  // 搏击操
        return true;
    }

    /** 取本周内「今天及以后」的前 need 天；本周不够则从下周一开始补齐 */
    private List<LocalDate> upcomingDays(LocalDate monday, int need) {
        LocalDate today = LocalDate.now();
        List<LocalDate> days = new ArrayList<>();
        for (int i = 0; i < 7 && days.size() < need; i++) {
            LocalDate d = monday.plusDays(i);
            if (!d.isBefore(today)) days.add(d);
        }
        for (int i = 0; days.size() < need; i++) days.add(monday.plusDays(7 + i));
        return days;
    }

    private void move(long courseId, LocalDate day, LocalTime start, LocalTime end) {

        jdbc.update("update course set start_time = ?, end_time = ? where id = ?",
                LocalDateTime.of(day, start), LocalDateTime.of(day, end), courseId);
    }

    /** 本周无场地占用 → 补几条演示预约（私有场馆 2 条 + 公共区域 1 条，含一条门店代约） */
    private boolean alignVenueBookings(LocalDateTime from, LocalDateTime to, LocalDate monday) {
        Integer n = jdbc.queryForObject(
                "select count(*) from venue_booking where start_time >= ? and start_time < ? and status = 'booked'",
                Integer.class, from, to);
        if (n != null && n > 0) return false;

        List<LocalDate> days = upcomingDays(monday, 4);
        add(1L, 1L, days.get(0), 10, 11, null);               // 会员自助 · 私有场馆
        add(2L, 2L, days.get(1), 18, 19, null);               // 会员自助 · 私有场馆
        add(3L, 3L, days.get(2), 9, 10, 5L);                  // 门店代约 · 私有场馆
        add(5L, 4L, days.get(3), 16, 17, null);               // 会员自助 · 公共区域
        return true;
    }

    private void add(long venueId, long memberId, LocalDate day, int startHour, int endHour, Long createdBy) {
        jdbc.update("insert into venue_booking (venue_id, member_id, start_time, end_time, status, created_by) "
                        + "values (?,?,?,?, 'booked', ?)",
                venueId, memberId, LocalDateTime.of(day, LocalTime.of(startHour, 0)),
                LocalDateTime.of(day, LocalTime.of(endHour, 0)), createdBy);
    }

    /* ==================== 报名名册闭合 ==================== */

    /** 参与名册对齐的基础演示课程（与种子脚本一致：C001 / C002 / C003） */
    private static final long[] DEMO_COURSES = {11L, 12L, 13L};

    /** 有效预约状态：与余位口径一致——已约 + 已签到（已取消 / 爽约不计入余位） */
    private static final String ACTIVE_STATUS = "('booked','checked_in')";

    /**
     * 名册闭合：演示课程缺多少有效预约就从演示会员池补多少，然后全量重算 booked_count。
     *
     * @return 实际补建的 booking 行数
     */
    private int alignRoster() {
        int created = 0;
        for (long courseId : DEMO_COURSES) {
            Integer cap = jdbc.queryForObject(
                    "select capacity from course where id = ?", Integer.class, courseId);
            Integer target = jdbc.queryForObject(
                    "select least(booked_count, capacity) from course where id = ?",
                    Integer.class, courseId);
            if (cap == null || target == null || target <= 0) continue;

            Integer active = jdbc.queryForObject(
                    "select count(*) from booking where course_id = ? and status in "
                            + ACTIVE_STATUS, Integer.class, courseId);
            int need = target - (active == null ? 0 : active);
            if (need <= 0) continue;

            LocalDateTime start = jdbc.queryForObject(
                    "select start_time from course where id = ?", LocalDateTime.class, courseId);
            // 已开课的历史预约记为已签到，更贴近真实账目；未开课的记为已约
            boolean started = start != null && start.isBefore(LocalDateTime.now());
            String status = started ? "checked_in" : "booked";

            // 演示会员池：只用 source='demo' 的档案，绝不占用真实会员
            List<Long> pool = jdbc.queryForList(
                    "select id from member where source = 'demo' and deleted_at is null "
                            + "and id not in (select member_id from booking where course_id = ? "
                            + "and status in " + ACTIVE_STATUS + ") order by id limit ?",
                    Long.class, courseId, need);
            for (Long memberId : pool) {
                if (started) {
                    created += jdbc.update(
                            "insert into booking (member_id, course_id, status, booked_at,"
                                    + " checkin_at, checkin_channel) values (?, ?, ?, now(), ?, 'front_desk')",
                            memberId, courseId, status, start);
                } else {
                    created += jdbc.update(
                            "insert into booking (member_id, course_id, status, booked_at) "
                                    + "values (?, ?, ?, now())",
                            memberId, courseId, status);
                }
            }
        }

        // 全量闭合收口：任何课程的余位都以有效预约数为准
        jdbc.update("update course c set c.booked_count = "
                + "(select count(*) from booking b where b.course_id = c.id and b.status in "
                + ACTIVE_STATUS + ")");
        return created;
    }

    /** 本周周一（周一为一周起始，与课表口径一致） */
    private static LocalDate mondayOf(LocalDate d) {
        return d.minusDays(d.getDayOfWeek().getValue() - 1L);
    }
}
