'use strict';
/**
 * Spring Boot 后端端到端冒烟（真实 HTTP 请求，H2 本地库）
 * 覆盖：S1 约课签到 + S2 收费对账 + 管理页面
 * 前置：后端已在 127.0.0.1:8080 启动（profile=local）
 * 运行：node verify/java-api-smoke.js
 */
const BASE = process.env.API_BASE || 'http://127.0.0.1:8080';
const fs = require('fs');
const path = require('path');

let pass = 0, fail = 0;
const rows = [];

function check(name, cond, detail = '') {
  if (cond) { pass++; rows.push([name, 'PASS', detail]); console.log(`PASS  ${name} ${detail}`); }
  else { fail++; rows.push([name, 'FAIL', detail]); console.log(`FAIL  ${name} ${detail}`); }
}
async function call(method, p, body) {
  const res = await fetch(BASE + p, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  let data = null;
  try { data = await res.json(); } catch { /* 204 / HTML */ }
  return { status: res.status, data };
}

(async () => {
  console.log('========================================================');
  console.log(' Spring Boot 后端端到端冒烟（S1 约课 + S2 收费 + 管理页）');
  console.log(` 服务地址：${BASE}`);
  console.log('========================================================\n');

  /* ---------- 基础设施 ---------- */
  const h = await call('GET', '/actuator/health');
  check('健康检查 UP', h.status === 200 && h.data && h.data.status === 'UP');

  const page = await fetch(BASE + '/');
  const html = await page.text();
  check('管理页面可访问', page.status === 200 && html.includes('后端管理页'), `HTTP ${page.status}`);

  /* ---------- S1 约课 ---------- */
  const c1 = await call('GET', '/api/courses');
  check('查询课程列表', c1.status === 200 && Array.isArray(c1.data) && c1.data.length === 3,
        `共 ${Array.isArray(c1.data) ? c1.data.length : 0} 门`);
  const bike = (c1.data || []).find(c => c.id === 11);
  check('课程字段与余位正确', bike && bike.remaining === 2, bike ? `动感单车剩余 ${bike.remaining}` : '');

  const mem = await call('GET', '/api/members');
  check('查询会员列表', mem.status === 200 && Array.isArray(mem.data) && mem.data.length === 3,
        `共 ${Array.isArray(mem.data) ? mem.data.length : 0} 人`);

  const b1 = await call('POST', '/api/bookings', { memberId: 1, courseId: 11 });
  check('张三约课成功', b1.status === 200 && b1.data && b1.data.ok === true, `bookingId=${b1.data && b1.data.bookingId}`);

  const b2 = await call('POST', '/api/bookings', { memberId: 1, courseId: 11 });
  check('重复提交幂等', b2.status === 200 && b2.data && b2.data.ok === true);

  const b3 = await call('POST', '/api/bookings', { memberId: 2, courseId: 11 });
  check('会籍过期被拒绝（SYS-R1）', b3.status === 409 && b3.data && b3.data.ruleCode === 'SYS-R1',
        b3.data ? b3.data.reason : '');

  const b4 = await call('POST', '/api/bookings', { memberId: 3, courseId: 12 });
  check('课程满员被拒绝（SYS-R3）', b4.status === 409 && b4.data && b4.data.ruleCode === 'SYS-R3',
        b4.data ? b4.data.reason : '');

  const ci = await call('POST', `/api/bookings/${b1.data.bookingId}/checkin`, { channel: 'scan' });
  check('扫码签到成功', ci.status === 204);

  const b5 = await call('POST', '/api/bookings', { memberId: 3, courseId: 13 });
  const before = (await call('GET', '/api/courses')).data.find(x => x.id === 13).remaining;
  await call('POST', `/api/bookings/${b5.data.bookingId}/cancel`, {});
  const after = (await call('GET', '/api/courses')).data.find(x => x.id === 13).remaining;
  check('取消预约释放名额（SYS-R3）', after === before + 1, `剩余 ${before} -> ${after}`);

  const b6 = await call('POST', '/api/bookings', { memberId: 3, courseId: 13 });
  const ns = await call('POST', `/api/bookings/${b6.data.bookingId}/no-show`, {});
  check('爽约判定返回累计次数（SYS-R4）', ns.status === 200 && ns.data >= 1, `累计=${ns.data}`);

  /* ---------- S2 收费与对账 ---------- */
  const o1 = await call('POST', '/api/orders', { memberId: 2, bizType: 'membership', amount: 3000, times: 0 });
  check('创建订单（REQ-B5-001）', o1.status === 200 && o1.data && o1.data.status === 'pending',
        o1.data ? o1.data.orderNo : '');

  const pay1 = await call('POST', `/api/orders/${o1.data.orderNo}/pay`);
  check('支付成功（订单转为 paid）', pay1.status === 200 && pay1.data && pay1.data.status === 'paid');

  const idem = await call('POST', `/api/pay/notify/${o1.data.orderNo}`, { paidAmount: 3000, outTradeNo: 'WX-DUP' });
  check('重复回调保持幂等（REQ-B5-004）', idem.status === 200 && idem.data && idem.data.status === 'paid');

  const o2 = await call('POST', '/api/orders', { memberId: 2, bizType: 'membership', amount: 3000, times: 0 });
  const bad = await call('POST', `/api/pay/notify/${o2.data.orderNo}`, { paidAmount: 300, outTradeNo: 'WX-BAD' });
  check('金额不符标记异常并拒绝（REQ-B5-004）',
        bad.status === 409 && bad.data && bad.data.status === 'abnormal',
        bad.data ? bad.data.abnormalReason : '');

  const abn = await call('GET', '/api/orders/abnormal');
  check('异常订单可查询（对账/告警）', abn.status === 200 && Array.isArray(abn.data) && abn.data.length >= 1,
        `${Array.isArray(abn.data) ? abn.data.length : 0} 笔`);

  const st = await call('POST', '/api/settlements');
  check('生成月度对账单（REQ-B5-003）', st.status === 200 && st.data && Number(st.data.totalAmount) >= 3000,
        st.data ? `${st.data.period} 实收 ¥${st.data.totalAmount}，异常 ${st.data.abnormalCount} 笔` : '');

  const stList = await call('GET', '/api/settlements');
  check('对账单可查询', stList.status === 200 && Array.isArray(stList.data) && stList.data.length >= 1);

  const rr = await call('POST', '/api/jobs/renew-remind?days=7');
  check('会籍到期提醒扫描（SYS-R6）', rr.status === 200 && rr.data && typeof rr.data.count === 'number',
        `需提醒 ${rr.data ? rr.data.count : '?'} 人`);

  console.log('\n--------------------------------------------------------');
  console.log(` 结果：通过 ${pass} / 失败 ${fail} / 共 ${pass + fail}`);
  console.log('--------------------------------------------------------');

  const md = ['# Spring Boot 后端端到端冒烟结果（真实 HTTP）', '',
    `- 服务地址：${BASE}（profile=local，H2 内存库）`,
    `- 覆盖：基础设施 / S1 约课签到 / S2 收费对账 / 管理页面`,
    `- 结果：**通过 ${pass} / 失败 ${fail} / 共 ${pass + fail}**`, '',
    '| 用例 | 结果 | 说明 |', '|---|---|---|',
    ...rows.map(r => `| ${r[0]} | ${r[1] === 'PASS' ? '✅ 通过' : '❌ 失败'} | ${r[2] || ''} |`), ''].join('\n');
  fs.writeFileSync(path.join(__dirname, 'java-api-smoke-report.md'), md, 'utf8');
  console.log('已生成：verify/java-api-smoke-report.md');

  process.exit(fail === 0 ? 0 : 1);
})();
