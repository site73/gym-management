'use strict';
/**
 * 功能测试（真实 HTTP）—— 覆盖约课系统全部已有功能，用于回归与交付验收。
 * 前置：后端已启动（MySQL 模式 8080），并已执行 verify/reset-demo-data.sql
 * 运行：node verify/functional-test.js
 */
const BASE = process.env.API_BASE || 'http://127.0.0.1:8080';
const fs = require('fs');
const path = require('path');

let pass = 0, fail = 0;
const rows = [];
let seq = 0;

function check(id, group, name, expected, actual, cond) {
  const r = cond ? 'PASS' : 'FAIL';
  if (cond) pass++; else fail++;
  rows.push([id, group, name, expected, actual, r]);
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${id}  ${name}  | 期望: ${expected} | 实际: ${actual}`);
}
async function call(method, p, body) {
  const res = await fetch(BASE + p, {
    method, headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  let data = null;
  try { data = await res.json(); } catch { /* 204 */ }
  return { status: res.status, data };
}
const nid = (p) => p + (++seq).toString().padStart(3, '0');

(async () => {
  console.log('========================================================');
  console.log(' 约课系统 功能测试（真实 HTTP / MySQL 后端）');
  console.log(` 服务地址：${BASE}`);
  console.log('========================================================\n');

  /* ================= A. 约课规则 ================= */
  const A = 'A 约课规则';
  let r = await call('GET', '/api/courses');
  const bikeBefore = r.data.find(c => c.id === 11).remaining;

  let b = await call('POST', '/api/bookings', { memberId: 1, courseId: 11 });
  check(nid('TC-A-'), A, '有效会籍可约课（SYS-R1）', '200 + booked', `${b.status} ${b.data && b.data.status}`, b.status === 200);

  let b2 = await call('POST', '/api/bookings', { memberId: 1, courseId: 11 });
  check(nid('TC-A-'), A, '重复提交幂等（不新增预约）', 'bookingId 不变', `${b2.status} id=${b2.data && b2.data.bookingId}/${b.data.bookingId}`,
        b2.status === 200 && b2.data.bookingId === b.data.bookingId);

  r = await call('GET', '/api/courses');
  check(nid('TC-A-'), A, '约课成功后余位 -1', `剩余 ${bikeBefore - 1}`, `剩余 ${r.data.find(c => c.id === 11).remaining}`,
        r.data.find(c => c.id === 11).remaining === bikeBefore - 1);

  let e = await call('POST', '/api/bookings', { memberId: 2, courseId: 11 });
  check(nid('TC-A-'), A, '会籍过期拒绝（SYS-R1）', '409 + SYS-R1', `${e.status} ${e.data && e.data.ruleCode}`, e.status === 409 && e.data.ruleCode === 'SYS-R1');

  let f = await call('POST', '/api/bookings', { memberId: 4, courseId: 11 });
  check(nid('TC-A-'), A, '会籍冻结拒绝（SYS-R2）', '409 + SYS-R2', `${f.status} ${f.data && f.data.ruleCode}`, f.status === 409 && f.data.ruleCode === 'SYS-R2');

  let g = await call('POST', '/api/bookings', { memberId: 3, courseId: 12 });
  check(nid('TC-A-'), A, '课程满员拒绝（SYS-R3）', '409 + SYS-R3', `${g.status} ${g.data && g.data.ruleCode}`, g.status === 409 && g.data.ruleCode === 'SYS-R3');

  let h = await call('POST', '/api/bookings', { memberId: 1, courseId: 12 });
  check(nid('TC-A-'), A, '满员课程即使会籍有效也拒绝', '409', `${h.status} ${h.data && h.data.ruleCode}`, h.status === 409);

  /* ================= B. 签到 ================= */
  const B = 'B 签到';
  const bid = b.data.bookingId;
  let ci = await call('POST', `/api/bookings/${bid}/checkin`, { channel: 'scan' });
  check(nid('TC-B-'), B, '扫码签到成功', '204', `${ci.status}`, ci.status === 204);

  let ci2 = await call('POST', `/api/bookings/${bid}/checkin`, { channel: 'scan' });
  check(nid('TC-B-'), B, '重复签到被拒绝（状态不允许）', '409', `${ci2.status} ${ci2.data && ci2.data.code}`, ci2.status === 409);

  let list = await call('GET', `/api/bookings?memberId=1`);
  const mine = list.data.find(x => x.id === bid);
  check(nid('TC-B-'), B, '预约状态更新为已签到', 'checked_in', mine && mine.status, mine && mine.status === 'checked_in');
  check(nid('TC-B-'), B, '记录签到渠道', 'scan', mine && mine.checkinChannel, mine && mine.checkinChannel === 'scan');

  /* ================= C. 取消 ================= */
  const C = 'C 取消预约';
  let cb = await call('POST', '/api/bookings', { memberId: 3, courseId: 13 });
  const beforeSeats = (await call('GET', '/api/courses')).data.find(x => x.id === 13).remaining;
  let cc = await call('POST', `/api/bookings/${cb.data.bookingId}/cancel`, {});
  check(nid('TC-C-'), C, '取消预约成功', '204', `${cc.status}`, cc.status === 204);

  const afterSeats = (await call('GET', '/api/courses')).data.find(x => x.id === 13).remaining;
  check(nid('TC-C-'), C, '取消后释放名额（SYS-R3）', `剩余 ${beforeSeats + 1}`, `剩余 ${afterSeats}`, afterSeats === beforeSeats + 1);

  let cc2 = await call('POST', `/api/bookings/${cb.data.bookingId}/cancel`, {});
  check(nid('TC-C-'), C, '重复取消被拒绝（状态不允许）', '409', `${cc2.status} ${cc2.data && cc2.data.code}`, cc2.status === 409);

  let reb = await call('POST', '/api/bookings', { memberId: 3, courseId: 13 });
  check(nid('TC-C-'), C, '取消后可再次预约同一课程', '200', `${reb.status}`, reb.status === 200 && reb.data.ok === true);

  let lb = await call('GET', `/api/bookings?memberId=3`);
  const cancelled = lb.data.find(x => x.id === cb.data.bookingId);
  check(nid('TC-C-'), C, '预约状态标记为已取消', 'cancelled', cancelled && cancelled.status, cancelled && cancelled.status === 'cancelled');

  /* ================= D. 爽约 ================= */
  const D = 'D 爽约判定';
  let ns = await call('POST', `/api/bookings/${reb.data.bookingId}/no-show`, {});
  check(nid('TC-D-'), D, '判爽约并返回累计次数', '200 + 次数≥1', `${ns.status} 累计=${ns.data}`, ns.status === 200 && ns.data >= 1);

  let ns2 = await call('POST', `/api/bookings/${reb.data.bookingId}/no-show`, {});
  check(nid('TC-D-'), D, '重复判爽约被拒绝', '409', `${ns2.status}`, ns2.status === 409);

  /* ================= E. 预约查询 ================= */
  const E = 'E 预约查询';
  let all = await call('GET', '/api/bookings');
  check(nid('TC-E-'), E, '预约列表可查询', '200 且非空', `${all.status} ${Array.isArray(all.data) ? all.data.length : 0} 条`,
        all.status === 200 && Array.isArray(all.data) && all.data.length > 0);
  let byMember = await call('GET', '/api/bookings?memberId=1');
  check(nid('TC-E-'), E, '按会员过滤预约', '仅返回该会员', `${byMember.data.length} 条`,
        byMember.data.every(x => x.memberId === 1));

  /* ================= F. 收费（S2） ================= */
  const F = 'F 收费';
  let o = await call('POST', '/api/orders', { memberId: 2, bizType: 'membership', amount: 3000, times: 0 });
  check(nid('TC-F-'), F, '创建订单', '200 + pending', `${o.status} ${o.data && o.data.status}`, o.status === 200 && o.data.status === 'pending');

  let pay = await call('POST', `/api/orders/${o.data.orderNo}/pay`);
  check(nid('TC-F-'), F, '支付成功', '200 + paid', `${pay.status} ${pay.data && pay.data.status}`, pay.status === 200 && pay.data.status === 'paid');

  let pay2 = await call('POST', `/api/pay/notify/${o.data.orderNo}`, { paidAmount: 3000, outTradeNo: 'DUP' });
  check(nid('TC-F-'), F, '重复回调幂等', '200 + paid', `${pay2.status} ${pay2.data && pay2.data.status}`, pay2.status === 200 && pay2.data.status === 'paid');

  let o2 = await call('POST', '/api/orders', { memberId: 2, bizType: 'membership', amount: 3000, times: 0 });
  let bad = await call('POST', `/api/pay/notify/${o2.data.orderNo}`, { paidAmount: 300, outTradeNo: 'BAD' });
  check(nid('TC-F-'), F, '金额不符标记异常', '409 + abnormal', `${bad.status} ${bad.data && bad.data.status}`, bad.status === 409 && bad.data.status === 'abnormal');

  let abn = await call('GET', '/api/orders/abnormal');
  check(nid('TC-F-'), F, '异常订单可查询', '≥1 笔', `${abn.data.length} 笔`, abn.data.length >= 1);

  /* ================= G. 对账与提醒 ================= */
  const G = 'G 对账与提醒';
  let st = await call('POST', '/api/settlements');
  check(nid('TC-G-'), G, '生成月度对账单', '200 + 实收≥3000', `${st.status} ¥${st.data && st.data.totalAmount}`, st.status === 200 && Number(st.data.totalAmount) >= 3000);

  let rr = await call('POST', '/api/jobs/renew-remind?days=7');
  check(nid('TC-G-'), G, '到期提醒扫描', '200', `${rr.status} 需提醒 ${rr.data && rr.data.count} 人`, rr.status === 200);

  /* ================= H. 页面 ================= */
  const H = 'H 管理页面';
  const page = await fetch(BASE + '/');
  const html = await page.text();
  check(nid('TC-H-'), H, '管理页面可访问', '200 + 标题', `HTTP ${page.status}`, page.status === 200 && html.includes('后端管理页'));

  console.log('\n--------------------------------------------------------');
  console.log(` 结果：通过 ${pass} / 失败 ${fail} / 共 ${pass + fail}`);
  console.log('--------------------------------------------------------');

  const md = ['# 功能测试报告（约课系统）', '',
    `- 服务地址：${BASE}（MySQL 8.0 模式）`,
    `- 执行时间：${new Date().toISOString()}`,
    `- 结果：**通过 ${pass} / 失败 ${fail} / 共 ${pass + fail}**`, '',
    '| 用例编号 | 分组 | 用例名称 | 期望 | 实际 | 结果 |', '|---|---|---|---|---|---|',
    ...rows.map(x => `| ${x[0]} | ${x[1]} | ${x[2]} | ${x[3]} | ${x[4]} | ${x[5] === 'PASS' ? '✅ 通过' : '❌ 失败'} |`),
    ''].join('\n');
  fs.writeFileSync(path.join(__dirname, 'functional-test-report.md'), md, 'utf8');
  console.log('已生成：verify/functional-test-report.md');

  process.exit(fail === 0 ? 0 : 1);
})();
