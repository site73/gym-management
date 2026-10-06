package com.gym.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * booking.feature 的步骤绑定（S1 切片）。
 *
 * <p>本类用内存模型承载场景状态，聚焦"业务规则是否可执行"；
 * 其余 S1 场景（attendance / membership）按同一方式扩展绑定。
 * 端到端的真实执行结果见 {@code 09-代码实现与迭代/verify/report.md}。
 */
public class BookingStepDefinitions {

    /* ---------- 内存模型（与 Java 规则实现同语义） ---------- */
    static class Member {
        String name; String status = "active"; int noShow = 0;
        Member(String n) { this.name = n; }
    }
    static class Course {
        String name; String time; String coach = "待定"; int capacity = 20; int booked = 0;
        Course(String n) { this.name = n; var m = java.util.regex.Pattern.compile("\\d{1,2}:\\d{2}").matcher(n); this.time = m.find() ? m.group() : "00:00"; }
        int remaining() { return capacity - booked; }
    }
    static class Result { boolean ok; String reason; boolean waitlist; boolean idempotent; }

    private final Map<String, Member> members = new HashMap<>();
    private final Map<String, Course> courses = new HashMap<>();
    private final List<String[]> bookings = new ArrayList<>(); // [member, course, status]
    private String curMember, curCourse, curCoach;
    private Result last;
    private boolean scheduleRejected;
    private String scheduleReason;
    private int remainingBefore;

    private static String toStatus(String cn) {
        return switch (cn) { case "有效" -> "active"; case "过期" -> "expired"; case "冻结" -> "frozen"; default -> cn; };
    }
    private Member member(String n) { return members.computeIfAbsent(n, Member::new); }
    private Course course(String n) { return courses.computeIfAbsent(n, Course::new); }

    private Result book(String memberName, String courseName) {
        var m = member(memberName); var c = course(courseName);
        var r = new Result();
        if (!"active".equals(m.status)) {
            r.reason = "frozen".equals(m.status) ? "会籍冻结中，暂不可约课" : "会籍已过期，请先续费";
            return r;
        }
        boolean exist = bookings.stream().anyMatch(b -> b[0].equals(memberName) && b[1].equals(courseName) && !b[2].equals("取消"));
        if (exist) { r.ok = true; r.idempotent = true; return r; }
        boolean conflict = bookings.stream().anyMatch(b -> b[0].equals(memberName)
                && !b[2].equals("取消") && course(b[1]).time.equals(c.time));
        if (conflict) { r.reason = "该时段已有预约，存在时间冲突"; return r; }
        if (c.remaining() <= 0) { r.reason = "课程已满"; r.waitlist = true; return r; }
        c.booked++; bookings.add(new String[]{memberName, courseName, "已约"});
        r.ok = true; return r;
    }

    /* ---------- Given ---------- */
    @Given("会员\"(.*?)\"的会籍状态为\"(.*?)\"")
    public void memberStatus(String name, String status) { curMember = name; member(name).status = toStatus(status); }

    @Given("课程\"(.*?)\"的?剩余名额(?:大于|为) (\\d+)")
    public void courseRemaining(String name, int n) {
        curCourse = name; var c = course(name); c.capacity = c.booked + n + 1;
    }

    @Given("会员\"(.*?)\"已存在 (\\d{1,2}:\\d{2}) 的预约")
    public void memberHasBookingAt(String name, String time) {
        curMember = name;
        var holder = course("占位课 " + time); holder.time = time;
        bookings.add(new String[]{name, holder.name, "已约"});
    }

    @Given("会员\"(.*?)\"已成功预约课程\"(.*?)\"")
    public void memberBookedSuccessfully(String name, String courseName) {
        curMember = name; curCourse = courseName;
        var r = book(name, courseName);
        assertThat(r.ok).as("前置条件：应能成功预约").isTrue();
        remainingBefore = course(courseName).remaining();
    }

    @Given("教练\"(.*?)\"在 (\\d{1,2}:\\d{2}) 已有课程")
    public void coachHasCourse(String coach, String time) {
        curCoach = coach; var c = course("已有课 " + time); c.coach = coach; c.time = time;
    }

    /* ---------- When ---------- */
    @When("该会员提交该课程的预约")
    public void submitCurrent() { last = book(curMember, curCourse); }

    @When("该会员提交课程\"(.*?)\"的预约")
    public void submitNamed(String courseName) { curCourse = courseName; last = book(curMember, courseName); }

    @When("会员\"(.*?)\"提交该课程的预约")
    public void submitByMember(String name) { curMember = name; last = book(name, curCourse); }

    @When("该会员再提交 (\\d{1,2}:\\d{2}) 另一课程的预约")
    public void submitAnotherAt(String time) {
        var n = "另一课 " + time; var c = course(n); c.time = time; c.capacity = 10;
        last = book(curMember, n);
    }

    @When("该会员重复提交完全相同的预约请求")
    public void submitAgain() { last = book(curMember, curCourse); }

    @When("店长提交该教练 (\\d{1,2}:\\d{2}) 的新排课")
    public void schedule(String time) {
        scheduleRejected = courses.values().stream().anyMatch(c -> c.coach.equals(curCoach) && c.time.equals(time));
        scheduleReason = scheduleRejected ? "教练 " + curCoach + " 在 " + time + " 已有课程" : null;
    }

    /* ---------- Then ---------- */
    @Then("系统应生成预约")
    public void expectCreated() { assertThat(last.ok).isTrue(); }

    @Then("系统应拒绝该预约")
    public void expectRejected() { assertThat(last.ok).isFalse(); }

    @Then("该预约状态应为\"(.*?)\"")
    public void expectStatus(String status) {
        var b = bookings.stream().filter(x -> x[0].equals(curMember) && x[1].equals(curCourse)).reduce((a, c) -> c);
        assertThat(b).isPresent();
        assertThat(b.get()[2]).isEqualTo(status);
    }

    @Then("返回提示\"(.*?)\"")
    public void expectReason(String msg) { assertThat(last.reason).isEqualTo(msg); }

    @Then("提供\"(.*?)\"选项")
    public void expectWaitlist(String opt) { assertThat(last.waitlist).isTrue(); }

    @Then("系统不应生成新的预约")
    public void expectIdempotent() { assertThat(last.idempotent).isTrue(); }

    @Then("课程剩余名额保持不变")
    public void expectRemainingUnchanged() { assertThat(course(curCourse).remaining()).isEqualTo(remainingBefore); }

    @Then("系统应拒绝该排课")
    public void expectScheduleRejected() { assertThat(scheduleRejected).isTrue(); }

    @Then("提示冲突的教练与时段")
    public void expectConflictHint() { assertThat(scheduleReason).contains(curCoach); }
}
