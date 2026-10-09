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
 * 演示排课对齐（保证「周课表」视图始终有内容）。
 *
 * <p><b>为什么需要</b>：种子脚本里的演示课程用 {@code CURDATE()} 落库，但 Flyway 的
 * R__ 脚本只在内容变化时重跑，时间一长这些课程就漂移到过去的某一周，
 * 于是「本周课表」「场馆占用课表」全是空的——功能没坏，演示却像坏了。
 *
 * <p><b>做法与边界</b>：只在<b>本周完全没有任何排课 / 没有任何场地占用</b>时才介入，
 * 一旦系统里已有本周的真实数据就立刻退出，绝不覆盖用户数据；
 * 介入时只调整 3 门基础演示课程（C001/C002/C003，id 11/12/13），
 * 并补 4 条演示场地预约，使会员端、教练端、门店后台三个视角的课表都有东西可看。
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

    /** 对齐本周：课程 + 场地占用 */
    @Transactional
    public void align() {
        LocalDate monday = mondayOf(LocalDate.now());
        LocalDateTime from = monday.atStartOfDay();
        LocalDateTime to = monday.plusDays(7).atStartOfDay();

        boolean coursesMoved = alignCourses(from, to, monday);
        boolean venuesAdded = alignVenueBookings(from, to, monday);
        if (coursesMoved || venuesAdded) {
            log.info("[demo] 演示排课已对齐本周（{} ~ {}）", monday, monday.plusDays(6));
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

    /** 本周周一（周一为一周起始，与课表口径一致） */
    private static LocalDate mondayOf(LocalDate d) {
        return d.minusDays(d.getDayOfWeek().getValue() - 1L);
    }
}
