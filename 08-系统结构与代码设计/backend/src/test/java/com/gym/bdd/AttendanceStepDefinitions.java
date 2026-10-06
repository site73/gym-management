package com.gym.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static com.gym.bdd.S1World.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * attendance.feature 的步骤绑定（S1 · 签到与爽约 SYS-R4、REQ-B4-002/003/004/005）。
 */
public class AttendanceStepDefinitions {

    /* ---------- Given ---------- */
    @Given("^会员\"(.*?)\"已预约课程\"(.*?)\"$")
    public void memberBooked(String name, String courseName) {
        curMember = name; curCourse = courseName;
        assertThat(book(name, courseName).ok).as("前置条件：应能成功预约").isTrue();
    }

    @Given("^会员\"(.*?)\"已预约该课程$")
    public void memberBookedCurrent(String name) {
        curMember = name;
        assertThat(book(name, curCourse).ok).as("前置条件：应能成功预约").isTrue();
    }

    @Given("^会员\"(.*?)\"已预约课程且无法使用小程序$")
    public void memberBookedCannotUseApp(String name) {
        curMember = name; curCourse = "私教课";
        course("私教课").capacity = 5;
        assertThat(book(name, "私教课").ok).as("前置条件：应能成功预约").isTrue();
    }

    @Given("^当前时间在签到窗口内$")
    public void inCheckinWindow() { /* 状态步骤，无需动作 */ }

    @Given("^课程开始后已超过 (\\d+) 分钟$")
    public void overMinutes(int minutes) { /* 状态步骤，无需动作 */ }

    @Given("^该会员未签到且未取消$")
    public void notCheckedIn() { /* 状态步骤，无需动作 */ }

    @Given("^会员\"(.*?)\"已累计 (\\d+) 次爽约$")
    public void memberHasNoShowCount(String name, int n) { curMember = name; member(name).noShowCount = n; }

    /* ---------- When ---------- */
    @When("^该会员扫码签到$")
    public void scanCheckIn() { last = checkIn(curMember, "scan", null); }

    @When("^前台为其代理签到$")
    public void frontDeskCheckIn() { last = checkIn(curMember, "front_desk", "前台"); }

    @When("^系统执行爽约判定$")
    public void judgeNoShow() { last = markNoShow(curMember); }

    @When("^该会员再次发生一次爽约$")
    public void noShowAgain() {
        if (lastBookingOf(curMember) == null) book(curMember, "补录课程");
        last = markNoShow(curMember);
    }

    @When("^该会员在开课前取消预约$")
    public void cancelBeforeStart() { last = cancel(curMember, curCourse); }

    /* ---------- Then ---------- */
    @Then("^该预约状态应更新为\"(.*?)\"$")
    public void expectBookingStatus(String status) {
        Booking b = lastBookingOf(curMember);
        assertThat(b).isNotNull();
        assertThat(b.status).isEqualTo(status);
    }

    @Then("^记录签到时间$")
    public void expectCheckinTime() { assertThat(lastBookingOf(curMember).checkinAt).isNotNull(); }

    @Then("^记录代办人信息$")
    public void expectOperator() { assertThat(lastBookingOf(curMember).operator).isNotNull(); }

    @Then("^该会员爽约次数累加 1$")
    public void expectNoShowPlusOne() { assertThat(member(curMember).noShowCount).isEqualTo(1); }

    @Then("^该会员爽约次数应更新为 (\\d+)$")
    public void expectNoShowCount(int n) { assertThat(member(curMember).noShowCount).isEqualTo(n); }

    @Then("^系统应在 (\\d+) 天内限制该会员的预约权限$")
    public void expectPenalty(int days) { assertThat(member(curMember).penaltyDaysRemaining).isEqualTo(days); }

    @Then("^该课程剩余名额应变为 (\\d+)$")
    public void expectRemaining(int n) { assertThat(course(curCourse).remaining()).isEqualTo(n); }
}
