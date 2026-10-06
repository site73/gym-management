package com.gym.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static com.gym.bdd.S1World.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * membership.feature 的步骤绑定（S1 · 会籍与课包 SYS-R1/R5/R6、REQ-B5-001/004）。
 */
public class MembershipStepDefinitions {

    /* ---------- Given ---------- */
    @Given("^会员\"(.*?)\"存在待支付的(年卡|私教课包)订单$")
    public void pendingOrder(String name, String kind) {
        curMember = name;
        String biz = "年卡".equals(kind) ? "membership" : "pt_package";
        curOrder = createOrder(name, biz, 3000);
        packageBefore = member(name).packageRemaining;
        contractBefore = member(name).contract;
    }

    @Given("^会员\"(.*?)\"的会籍距到期还有 (\\d+) 天$")
    public void daysToExpire(String name, int days) { curMember = name; member(name).daysToExpire = days; }

    @Given("^会员\"(.*?)\"的会籍已超过到期日且未续费$")
    public void alreadyExpired(String name) { curMember = name; member(name).daysToExpire = -1; }

    @Given("^订单金额为 (\\d+) 元$")
    public void orderAmount(int amount) {
        curMember = "某会员";
        curOrder = createOrder("某会员", "membership", amount);
        packageBefore = member("某会员").packageRemaining;
        contractBefore = member("某会员").contract;
    }

    @Given("^会员\"(.*?)\"的私教课包剩余 (\\d+) 次$")
    public void packageRemaining(String name, int times) { curMember = name; member(name).packageRemaining = times; }

    /* ---------- When ---------- */
    @When("^该订单支付成功$")
    public void paySucceeded() { last = payOrder(curOrder); }

    @When("^支付回调失败$")
    public void payCallbackFailedStep() { last = S1World.payCallbackFailed(curOrder); }

    @When("^支付回调金额为 (\\d+) 元$")
    public void payCallbackAmount(int amount) { last = payAmountMismatch(curOrder, amount); }

    @When("^系统执行每日提醒任务$")
    public void runDailyRemind() { dailyRenewRemind(); }

    @When("^系统执行每日扫描$")
    public void runDailyScan() { dailyExpireScan(); }

    @When("^其私教课程结课$")
    public void ptClassFinished() { last = finishPtClass(curMember); }

    /* ---------- Then ---------- */
    @Then("^系统应生成会籍合同$")
    public void expectContract() { assertThat(member(curMember).contract).isNotNull(); }

    @Then("^该会员会籍状态应为\"(.*?)\"$")
    public void expectMemberStatus(String status) {
        String expect = switch (status) { case "有效" -> "active"; case "过期" -> "expired"; case "冻结" -> "frozen"; default -> status; };
        assertThat(member(curMember).status).isEqualTo(expect);
    }

    @Then("^应向该会员发送续费提醒$")
    public void expectRenewNotification() { assertThat(renewed(curMember)).isTrue(); }

    @Then("^该会员状态应更新为\"(.*?)\"$")
    public void expectStatusUpdated(String status) {
        String expect = switch (status) { case "有效" -> "active"; case "过期" -> "expired"; case "冻结" -> "frozen"; default -> status; };
        assertThat(member(curMember).status).isEqualTo(expect);
    }

    @Then("^其约课请求应被拦截$")
    public void expectBookingBlocked() {
        assertThat(book(curMember, "事后验证课程").ok).as("会籍不可用时约课应被拦截").isFalse();
    }

    @Then("^该订单应被标记为\"(.*?)\"$")
    public void expectOrderStatus(String status) { assertThat(curOrder.status).isEqualTo(status); }

    @Then("^触发告警$")
    public void expectAlert() { assertThat(alerts).isNotEmpty(); }

    @Then("^该会员的课包余额不应发生变化$")
    public void expectPackageUnchanged() { assertThat(member(curMember).packageRemaining).isEqualTo(packageBefore); }

    @Then("^系统应标记订单异常$")
    public void expectOrderAbnormal() { assertThat(curOrder.status).isEqualTo("异常"); }

    @Then("^不得为会员延长会籍或增加课包次数$")
    public void expectNoBenefitGranted() {
        assertThat(member(curMember).contract).isEqualTo(contractBefore);
        assertThat(member(curMember).packageRemaining).isEqualTo(packageBefore);
    }

    @Then("^该课包余额应扣减为 (\\d+)$")
    public void expectPackageDeducted(int n) { assertThat(member(curMember).packageRemaining).isEqualTo(n); }

    @Then("^系统应禁止再为其排课$")
    public void expectScheduleBlocked() { assertThat(canSchedulePt(curMember)).isFalse(); }
}
