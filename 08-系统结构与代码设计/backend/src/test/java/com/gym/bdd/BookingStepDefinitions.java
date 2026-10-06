package com.gym.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static com.gym.bdd.S1World.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * booking.feature 的步骤绑定（S1 · 约课规则 SYS-R1/R2/R3）。
 *
 * <p>步骤注解统一以 <code>^...$</code> 锚定，交由 Cucumber 按**正则**解析
 * （不加锚点会被当作 Cucumber 表达式，无法识别 \d 等正则语法）。
 */
public class BookingStepDefinitions {

    private static String toStatus(String cn) {
        return switch (cn) { case "有效" -> "active"; case "过期" -> "expired"; case "冻结" -> "frozen"; default -> cn; };
    }

    /* ---------- Given ---------- */
    @Given("^会员\"(.*?)\"的会籍状态为\"(.*?)\"$")
    public void memberStatus(String name, String status) { curMember = name; member(name).status = toStatus(status); }

    @Given("^课程\"(.*?)\"剩余名额大于 (\\d+)$")
    public void courseRemainingMoreThan(String name, int n) {
        curCourse = name; Course c = course(name); c.capacity = c.booked + n + 1;
    }

    /** 同时覆盖"剩余名额为 N"与"的剩余名额为 N"两种写法 */
    @Given("^课程\"(.*?)\"的?剩余名额为 (\\d+)$")
    public void courseRemainingExactly(String name, int n) {
        curCourse = name; Course c = course(name); c.capacity = c.booked + n;
    }

    @Given("^会员\"(.*?)\"已存在 (\\d{1,2}:\\d{2}) 的预约$")
    public void memberHasBookingAt(String name, String time) {
        curMember = name;
        Course holder = course("占位课 " + time);
        holder.time = time; holder.capacity = 10; holder.booked = 1;
        Booking b = new Booking();
        b.member = name; b.course = holder.name; b.status = "已约";
        bookings.add(b);
    }

    @Given("^会员\"(.*?)\"已成功预约课程\"(.*?)\"$")
    public void memberBookedSuccessfully(String name, String courseName) {
        curMember = name; curCourse = courseName;
        assertThat(book(name, courseName).ok).as("前置条件：应能成功预约").isTrue();
        remainingBefore = course(courseName).remaining();
    }

    @Given("^教练\"(.*?)\"在 (\\d{1,2}:\\d{2}) 已有课程$")
    public void coachHasCourse(String coach, String time) {
        curCoach = coach;
        Course c = course("已有课 " + time);
        c.coach = coach; c.time = time;
    }

    /* ---------- When ---------- */
    @When("^该会员提交该课程的预约$")
    public void submitCurrent() { last = book(curMember, curCourse); }

    @When("^该会员提交课程\"(.*?)\"的预约$")
    public void submitNamed(String courseName) { curCourse = courseName; last = book(curMember, courseName); }

    @When("^会员\"(.*?)\"提交该课程的预约$")
    public void submitByMember(String name) { curMember = name; last = book(name, curCourse); }

    @When("^该会员提交任意课程的预约$")
    public void submitAny() { last = book(curMember, "任意课程"); }

    @When("^该会员再提交 (\\d{1,2}:\\d{2}) 另一课程的预约$")
    public void submitAnotherAt(String time) {
        String n = "另一课 " + time;
        Course c = course(n); c.time = time; c.capacity = 10;
        last = book(curMember, n);
    }

    @When("^该会员重复提交完全相同的预约请求$")
    public void submitAgain() { last = book(curMember, curCourse); }

    @When("^店长提交该教练 (\\d{1,2}:\\d{2}) 的新排课$")
    public void schedule(String time) { S1World.schedule(curCoach, time); }

    /* ---------- Then ---------- */
    @Then("^系统应生成预约$")
    public void expectCreated() { assertThat(last.ok).isTrue(); }

    @Then("^系统应拒绝该预约$")
    public void expectRejected() { assertThat(last.ok).isFalse(); }

    @Then("^该预约状态应为\"(.*?)\"$")
    public void expectStatus(String status) {
        Booking b = lastBookingOf(curMember);
        assertThat(b).isNotNull();
        assertThat(b.status).isEqualTo(status);
    }

    @Then("^返回提示\"(.*?)\"$")
    public void expectReason(String msg) { assertThat(last.reason).isEqualTo(msg); }

    @Then("^提供\"(.*?)\"选项$")
    public void expectWaitlist(String opt) { assertThat(last.waitlist).isTrue(); }

    @Then("^系统不应生成新的预约$")
    public void expectIdempotent() { assertThat(last.idempotent).isTrue(); }

    @Then("^课程剩余名额保持不变$")
    public void expectRemainingUnchanged() { assertThat(course(curCourse).remaining()).isEqualTo(remainingBefore); }

    @Then("^系统应拒绝该排课$")
    public void expectScheduleRejected() { assertThat(scheduleRejected).isTrue(); }

    @Then("^提示冲突的教练与时段$")
    public void expectConflictHint() { assertThat(scheduleReason).contains(curCoach); }
}
