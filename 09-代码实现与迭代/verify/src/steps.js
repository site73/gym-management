'use strict';
/**
 * 中文步骤解释器（S1 切片）
 * 每个步骤既是"动作"也可能是"断言"——断言失败即抛出错误，由运行器记为失败。
 */
const STATUS_MAP = { '有效': 'active', '过期': 'expired', '冻结': 'frozen' };

function expect(cond, msg) {
  if (!cond) throw new Error(msg);
}

const handlers = [
  /* ---------------- 前置状态 ---------------- */
  [/^会员"(.+?)"的会籍状态为"(.+?)"$/, (c, m) => {
    c.member = m[1];
    c.gym.member(m[1]).membershipStatus = STATUS_MAP[m[2]] || m[2];
  }],
  [/^课程"(.+?)"剩余名额大于 (\d+)$/, (c, m) => {
    c.course = m[1];
    const course = c.gym.course(m[1]);
    course.capacity = course.booked + Number(m[2]) + 1;
  }],
  [/^课程"(.+?)"的?剩余名额为 (\d+)$/, (c, m) => {
    c.course = m[1];
    const course = c.gym.course(m[1]);
    course.capacity = course.booked + Number(m[2]);
  }],
  [/^会员"(.+?)"已存在 (\d{1,2}:\d{2}) 的预约$/, (c, m) => {
    c.member = m[1];
    const g = c.gym;
    const holder = g.course('占位课 ' + m[2]);
    holder.time = m[2];
    holder.capacity = 10; holder.booked = 1;
    g.bookings.push({ id: ++g.seq, member: m[1], course: holder.name, status: '已约', checkinAt: null, operator: null });
  }],
  [/^会员"(.+?)"已成功预约课程"(.+?)"$/, (c, m) => {
    c.member = m[1]; c.course = m[2];
    const r = c.gym.submitBooking(m[1], m[2]);
    expect(r.ok, `前置条件失败：${m[1]} 无法预约 ${m[2]}`);
    c.capacityBefore = c.gym.course(m[2]).remaining;
  }],
  [/^会员"(.+?)"已预约该课程$/, (c, m) => {
    c.member = m[1];
    const r = c.gym.submitBooking(m[1], c.course);
    expect(r.ok, `前置条件失败：${m[1]} 无法预约 ${c.course}`);
  }],
  [/^会员"(.+?)"已预约课程"(.+?)"$/, (c, m) => {
    c.member = m[1]; c.course = m[2];
    const r = c.gym.submitBooking(m[1], m[2]);
    expect(r.ok, `前置条件失败：${m[1]} 无法预约 ${m[2]}`);
  }],
  [/^会员"(.+?)"已预约课程且无法使用小程序$/, (c, m) => {
    c.member = m[1]; c.course = '私教课';
    const g = c.gym;
    const course = g.course('私教课');
    course.capacity = 5;
    const r = g.submitBooking(m[1], '私教课');
    expect(r.ok, `前置条件失败：${m[1]} 无法预约私教课`);
  }],
  [/^教练"(.+?)"在 (\d{1,2}:\d{2}) 已有课程$/, (c, m) => {
    c.coach = m[1]; c.time = m[2];
    const g = c.gym;
    const course = g.course(`已有课 ${m[2]}`);
    course.coach = m[1]; course.time = m[2];
  }],
  [/^当前时间在签到窗口内$/, (c) => { c.inWindow = true; }],
  [/^课程开始后已超过 (\d+) 分钟$/, (c, m) => { c.overMinutes = Number(m[1]); }],
  [/^该会员未签到且未取消$/, () => {}],
  [/^会员"(.+?)"已累计 (\d+) 次爽约$/, (c, m) => { c.member = m[1]; c.gym.member(m[1]).noShowCount = Number(m[2]); }],
  [/^会员"(.+?)"存在待支付的(年卡|私教课包)订单$/, (c, m) => {
    c.member = m[1];
    const biz = m[2] === '年卡' ? 'membership' : 'pt_package';
    c.order = c.gym.createOrder(m[1], biz, 3000);
    c.packageBefore = c.gym.member(m[1]).packageRemaining;
    c.contractBefore = c.gym.member(m[1]).contract;
  }],
  [/^会员"(.+?)"的会籍距到期还有 (\d+) 天$/, (c, m) => { c.member = m[1]; c.gym.member(m[1]).daysToExpire = Number(m[2]); }],
  [/^会员"(.+?)"的会籍已超过到期日且未续费$/, (c, m) => { c.member = m[1]; c.gym.member(m[1]).daysToExpire = -1; }],
  [/^订单金额为 (\d+) 元$/, (c, m) => {
    c.member = '某会员';
    c.gym.member('某会员');
    c.order = c.gym.createOrder('某会员', 'membership', Number(m[1]));
    c.contractBefore = c.gym.member('某会员').contract;
    c.packageBefore = c.gym.member('某会员').packageRemaining;
  }],
  [/^会员"(.+?)"的私教课包剩余 (\d+) 次$/, (c, m) => { c.member = m[1]; c.gym.member(m[1]).packageRemaining = Number(m[2]); }],

  /* ---------------- 动作 ---------------- */
  [/^该会员提交该课程的预约$/, (c) => { c.lastResult = c.gym.submitBooking(c.member, c.course); }],
  [/^该会员提交课程"(.+?)"的预约$/, (c, m) => { c.course = m[1]; c.lastResult = c.gym.submitBooking(c.member, m[1]); }],
  [/^该会员提交任意课程的预约$/, (c) => { c.lastResult = c.gym.submitBooking(c.member, '任意课程'); }],
  [/^会员"(.+?)"提交该课程的预约$/, (c, m) => { c.member = m[1]; c.lastResult = c.gym.submitBooking(m[1], c.course); }],
  [/^该会员再提交 (\d{1,2}:\d{2}) 另一课程的预约$/, (c, m) => {
    const name = '另一课 ' + m[1];
    const course = c.gym.course(name); course.time = m[1]; course.capacity = 10;
    c.lastResult = c.gym.submitBooking(c.member, name);
  }],
  [/^该会员重复提交完全相同的预约请求$/, (c) => {
    const before = c.gym.bookings.length;
    c.lastResult = c.gym.submitBooking(c.member, c.course);
    c.countBefore = before;
  }],
  [/^店长提交该教练 (\d{1,2}:\d{2}) 的新排课$/, (c, m) => { c.scheduleResult = c.gym.schedule(c.coach, m[1]); }],
  [/^该会员扫码签到$/, (c) => { c.lastResult = c.gym.checkIn(c.member, 'scan'); }],
  [/^系统执行爽约判定$/, (c) => { c.lastResult = c.gym.markNoShow(c.member); }],
  [/^该会员再次发生一次爽约$/, (c) => {
    // 若无既有预约，则先补一条预约以模拟"再次爽约"场景
    if (!c.gym.lastBookingOf(c.member)) c.gym.submitBooking(c.member, '补录课程');
    c.lastResult = c.gym.markNoShow(c.member);
  }],
  [/^该会员在开课前取消预约$/, (c) => { c.lastResult = c.gym.cancel(c.member, c.course); }],
  [/^前台为其代理签到$/, (c) => { c.lastResult = c.gym.checkIn(c.member, 'front_desk', '前台'); }],
  [/^该订单支付成功$/, (c) => { c.lastResult = c.gym.payOrder(c.order); }],
  [/^系统执行每日提醒任务$/, (c) => { c.lastResult = c.gym.dailyRenewRemind(); }],
  [/^系统执行每日扫描$/, (c) => { c.lastResult = c.gym.dailyExpireScan(); }],
  [/^支付回调失败$/, (c) => { c.lastResult = c.gym.payCallbackFailed(c.order); }],
  [/^支付回调金额为 (\d+) 元$/, (c, m) => { c.lastResult = c.gym.payAmountMismatch(c.order, Number(m[1])); }],
  [/^其私教课程结课$/, (c) => { c.lastResult = c.gym.finishPtClass(c.member); }],

  /* ---------------- 断言 ---------------- */
  [/^系统应生成预约$/, (c) => expect(c.lastResult && c.lastResult.ok, '预期生成预约，但被拒绝')],
  [/^该预约状态应为"(.+?)"$/, (c, m) => {
    const b = c.gym.lastBookingOf(c.member);
    expect(b && b.status === m[1], `预期预约状态为"${m[1]}"，实际为"${b && b.status}"`);
  }],
  [/^系统应拒绝该预约$/, (c) => expect(c.lastResult && !c.lastResult.ok, '预期拒绝预约，但被接受')],
  [/^返回提示"(.+?)"$/, (c, m) => expect(c.lastResult && c.lastResult.reason === m[1],
    `预期提示"${m[1]}"，实际为"${c.lastResult && c.lastResult.reason}"`)],
  [/^提供"(.+?)"选项$/, (c) => expect(c.lastResult && c.lastResult.waitlistAvailable === true, '预期提供候补选项')],
  [/^系统不应生成新的预约$/, (c) => expect(c.lastResult && c.lastResult.idempotent === true, '预期幂等（不生成新预约）')],
  [/^课程剩余名额保持不变$/, (c) => {
    const after = c.gym.course(c.course).remaining;
    expect(after === c.capacityBefore, `预期剩余名额保持 ${c.capacityBefore}，实际 ${after}`);
  }],
  [/^系统应拒绝该排课$/, (c) => expect(c.scheduleResult && !c.scheduleResult.ok, '预期排课被拒绝')],
  [/^提示冲突的教练与时段$/, (c) => expect(/冲突|已有课程/.test(c.scheduleResult.reason || ''), '预期提示冲突信息')],
  [/^该预约状态应更新为"(.+?)"$/, (c, m) => {
    const b = c.gym.lastBookingOf(c.member);
    expect(b && b.status === m[1], `预期状态"${m[1]}"，实际"${b && b.status}"`);
  }],
  [/^记录签到时间$/, (c) => {
    const b = c.gym.lastBookingOf(c.member);
    expect(b && b.checkinAt != null, '预期记录签到时间');
  }],
  [/^该会员爽约次数累加 1$/, (c) => {
    const m = c.gym.member(c.member);
    expect(m.noShowCount === 1, `预期爽约次数为 1，实际 ${m.noShowCount}`);
  }],
  [/^该会员爽约次数应更新为 (\d+)$/, (c, m) => {
    const mem = c.gym.member(c.member);
    expect(mem.noShowCount === Number(m[1]), `预期爽约次数 ${m[1]}，实际 ${mem.noShowCount}`);
  }],
  [/^系统应在 (\d+) 天内限制该会员的预约权限$/, (c, m) => {
    const mem = c.gym.member(c.member);
    expect(mem.penaltyDaysRemaining === Number(m[1]),
      `预期限制 ${m[1]} 天，实际 ${mem.penaltyDaysRemaining}`);
  }],
  [/^该课程剩余名额应变为 (\d+)$/, (c, m) => {
    const after = c.gym.course(c.course).remaining;
    expect(after === Number(m[1]), `预期剩余名额 ${m[1]}，实际 ${after}`);
  }],
  [/^记录代办人信息$/, (c) => {
    const b = c.gym.lastBookingOf(c.member);
    expect(b && b.operator != null, '预期记录代办人');
  }],
  [/^系统应生成会籍合同$/, (c) => expect(c.gym.member(c.member).contract != null, '预期生成会籍合同')],
  [/^该会员会籍状态应为"(.+?)"$/, (c, m) => {
    const st = c.gym.member(c.member).membershipStatus;
    const w = STATUS_MAP[m[1]] || m[1];
    expect(st === w, `预期会籍状态"${m[1]}"，实际"${st}"`);
  }],
  [/^应向该会员发送续费提醒$/, (c) => {
    const hit = c.gym.notifications.some(n => n.member === c.member && n.template === 'renew_reminder');
    expect(hit, '预期发送续费提醒');
  }],
  [/^该会员状态应更新为"(.+?)"$/, (c, m) => {
    const st = c.gym.member(c.member).membershipStatus;
    const w = STATUS_MAP[m[1]] || m[1];
    expect(st === w, `预期状态"${m[1]}"，实际"${st}"`);
  }],
  [/^其约课请求应被拦截$/, (c) => {
    const r = c.gym.submitBooking(c.member, '事后验证课程');
    expect(!r.ok, '预期约课被拦截');
  }],
  [/^该订单应被标记为"(.+?)"$/, (c, m) => expect(c.order.status === m[1], `预期订单状态"${m[1]}"，实际"${c.order.status}"`)],
  [/^触发告警$/, (c) => expect(c.gym.alerts.length > 0, '预期触发告警')],
  [/^该会员的课包余额不应发生变化$/, (c) => {
    const now = c.gym.member(c.member).packageRemaining;
    expect(now === c.packageBefore, `预期课包余额不变（${c.packageBefore}），实际 ${now}`);
  }],
  [/^系统应标记订单异常$/, (c) => expect(c.order.status === '异常', `预期订单异常，实际"${c.order.status}"`)],
  [/^不得为会员延长会籍或增加课包次数$/, (c) => {
    const mem = c.gym.member(c.member);
    expect(mem.contract === c.contractBefore && mem.packageRemaining === c.packageBefore,
      '预期会籍与课包均未变更');
  }],
  [/^该课包余额应扣减为 (\d+)$/, (c, m) => {
    const v = c.gym.member(c.member).packageRemaining;
    expect(v === Number(m[1]), `预期课包余额 ${m[1]}，实际 ${v}`);
  }],
  [/^系统应禁止再为其排课$/, (c) => expect(!c.gym.canSchedulePt(c.member), '预期禁止再排课')],
];

function findHandler(text) {
  for (const [re, fn] of handlers) {
    const m = text.match(re);
    if (m) return { fn, m };
  }
  return null;
}

module.exports = { findHandler, expect };
