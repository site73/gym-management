package com.gym.bdd;

import java.util.*;

/**
 * S1 切片的行为驱动测试世界（内存参考实现）。
 *
 * <p>与 Java 规则实现（SYS-R1/R2/R3/R4/R5/R6）及 Node 参考执行器同语义，
 * 供 booking / attendance / membership 三个 feature 的步骤绑定共用。
 * 每个场景开始前由 {@link Hooks} 重置。
 */
public final class S1World {

    public static final int PENALTY_N = 3;          // SYS-R4
    public static final int PENALTY_DAYS = 7;       // SYS-R4
    public static final int RENEW_REMIND_D = 7;     // SYS-R6

    public static class Member {
        public String name;
        public String status = "active";
        public int noShowCount = 0;
        public int penaltyDaysRemaining = 0;
        public Integer packageRemaining = null;
        public int daysToExpire = 90;
        public Object contract = null;
        Member(String n) { this.name = n; }
    }

    public static class Course {
        public String name;
        public String time;
        public String coach = "待定";
        public int capacity = 20;
        public int booked = 0;
        Course(String n) {
            this.name = n;
            var m = java.util.regex.Pattern.compile("\\d{1,2}:\\d{2}").matcher(n);
            this.time = m.find() ? m.group() : "00:00";
        }
        public int remaining() { return capacity - booked; }
    }

    public static class Booking {
        public String member, course, status = "已约";
        public String checkinAt = null, channel = null, operator = null;
    }

    public static class Order {
        public String no, member, bizType, status = "pending";
        public int amount;
        public Integer times = 0;
    }

    public static class Result {
        public boolean ok;
        public String reason;
        public boolean waitlist;
        public boolean idempotent;
    }

    public static final Map<String, Member> members = new HashMap<>();
    public static final Map<String, Course> courses = new HashMap<>();
    public static final List<Booking> bookings = new ArrayList<>();
    public static final List<Order> orders = new ArrayList<>();
    public static final List<String> notifications = new ArrayList<>();
    public static final List<String> alerts = new ArrayList<>();

    public static String curMember, curCourse, curCoach;
    public static Result last;
    public static boolean scheduleRejected;
    public static String scheduleReason;
    public static int remainingBefore;
    public static Integer packageBefore;
    public static Object contractBefore;
    public static Order curOrder;

    private static int seq = 0;

    public static void reset() {
        members.clear(); courses.clear(); bookings.clear(); orders.clear();
        notifications.clear(); alerts.clear();
        curMember = curCourse = curCoach = null;
        last = null; scheduleRejected = false; scheduleReason = null;
        remainingBefore = 0; packageBefore = null; contractBefore = null; curOrder = null;
        seq = 0;
    }

    public static Member member(String n) { return members.computeIfAbsent(n, Member::new); }
    public static Course course(String n) { return courses.computeIfAbsent(n, Course::new); }

    public static List<Booking> bookingsOf(String m) {
        List<Booking> r = new ArrayList<>();
        for (Booking b : bookings) if (b.member.equals(m)) r.add(b);
        return r;
    }

    public static Booking lastBookingOf(String m) {
        List<Booking> list = bookingsOf(m);
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    /* ---------------- 约课（SYS-R1/R2/R3/R4） ---------------- */
    public static Result book(String memberName, String courseName) {
        Member m = member(memberName);
        Course c = course(courseName);
        Result r = new Result();
        if (!"active".equals(m.status)) {
            r.reason = "frozen".equals(m.status) ? "会籍冻结中，暂不可约课" : "会籍已过期，请先续费";
            return r;
        }
        if (m.penaltyDaysRemaining > 0) {
            r.reason = "已被限制预约，剩余 " + m.penaltyDaysRemaining + " 天";
            return r;
        }
        for (Booking b : bookings) {
            if (b.member.equals(memberName) && b.course.equals(courseName) && !b.status.equals("取消")) {
                r.ok = true; r.idempotent = true; return r;
            }
        }
        for (Booking b : bookings) {
            if (b.member.equals(memberName) && !b.status.equals("取消")
                    && course(b.course).time.equals(c.time)) {
                r.reason = "该时段已有预约，存在时间冲突"; return r;
            }
        }
        if (c.remaining() <= 0) { r.reason = "课程已满"; r.waitlist = true; return r; }
        c.booked++;
        Booking nb = new Booking();
        nb.member = memberName; nb.course = courseName; nb.status = "已约";
        bookings.add(nb);
        r.ok = true;
        return r;
    }

    /* ---------------- 签到 / 爽约 / 取消 ---------------- */
    public static Result checkIn(String memberName, String channel, String operator) {
        Result r = new Result();
        Booking b = lastBookingOf(memberName);
        if (b == null || !"已约".equals(b.status)) { r.reason = "无可签到的预约"; return r; }
        b.status = "已签到"; b.checkinAt = "now"; b.channel = channel; b.operator = operator;
        r.ok = true;
        return r;
    }

    public static Result markNoShow(String memberName) {
        Result r = new Result();
        Booking b = lastBookingOf(memberName);
        if (b == null) { r.reason = "无预约"; return r; }
        b.status = "爽约";
        Member m = member(memberName);
        m.noShowCount++;
        if (m.noShowCount >= PENALTY_N) m.penaltyDaysRemaining = PENALTY_DAYS;
        r.ok = true;
        return r;
    }

    public static Result cancel(String memberName, String courseName) {
        Result r = new Result();
        for (Booking b : bookings) {
            if (b.member.equals(memberName) && b.course.equals(courseName) && "已约".equals(b.status)) {
                b.status = "取消";
                Course c = course(courseName);
                c.booked = Math.max(0, c.booked - 1);
                r.ok = true;
                return r;
            }
        }
        r.reason = "无可取消的预约";
        return r;
    }

    /* ---------------- 排课冲突（SYS-R3） ---------------- */
    public static void schedule(String coach, String time) {
        scheduleRejected = false;
        for (Course c : courses.values()) {
            if (c.coach.equals(coach) && c.time.equals(time)) scheduleRejected = true;
        }
        scheduleReason = scheduleRejected ? "教练 " + coach + " 在 " + time + " 已有课程" : null;
    }

    /* ---------------- 支付（REQ-B5-001/004） ---------------- */
    public static Order createOrder(String memberName, String bizType, int amount) {
        Order o = new Order();
        o.no = "O" + (++seq); o.member = memberName; o.bizType = bizType; o.amount = amount;
        if ("pt_package".equals(bizType)) o.times = 10;
        orders.add(o);
        return o;
    }

    public static Result payOrder(Order o) {
        Result r = new Result();
        o.status = "paid";
        Member m = member(o.member);
        m.status = "active";
        m.contract = o.bizType;
        if ("pt_package".equals(o.bizType)) {
            m.packageRemaining = (m.packageRemaining == null ? 0 : m.packageRemaining) + o.times;
        }
        r.ok = true;
        return r;
    }

    public static Result payCallbackFailed(Order o) {
        Result r = new Result();
        o.status = "异常";
        alerts.add("PAY_CALLBACK_FAILED:" + o.no);
        r.ok = true;
        return r;
    }

    public static Result payAmountMismatch(Order o, int paidAmount) {
        Result r = new Result();
        if (paidAmount != o.amount) {
            o.status = "异常";
            alerts.add("PAY_AMOUNT_MISMATCH:" + o.no);
            r.reason = "金额与订单不一致，已标记异常并告警";
            return r;
        }
        return payOrder(o);
    }

    /* ---------------- 课包核销（SYS-R5） ---------------- */
    public static Result finishPtClass(String memberName) {
        Result r = new Result();
        Member m = member(memberName);
        if (m.packageRemaining == null) { r.reason = "无课包"; return r; }
        m.packageRemaining = Math.max(0, m.packageRemaining - 1);
        r.ok = true;
        return r;
    }

    public static boolean canSchedulePt(String memberName) {
        Member m = member(memberName);
        return !(m.packageRemaining != null && m.packageRemaining <= 0);
    }

    /* ---------------- 定时任务（SYS-R6 / 过期扫描） ---------------- */
    public static void dailyRenewRemind() {
        for (Member m : members.values()) {
            if ("active".equals(m.status) && m.daysToExpire <= RENEW_REMIND_D) {
                notifications.add(m.name + ":renew_reminder");
            }
        }
    }

    public static void dailyExpireScan() {
        for (Member m : members.values()) {
            if (m.daysToExpire < 0 && "active".equals(m.status)) m.status = "expired";
        }
    }

    public static boolean renewed(String memberName) {
        return notifications.stream().anyMatch(n -> n.startsWith(memberName + ":renew_reminder"));
    }
}
