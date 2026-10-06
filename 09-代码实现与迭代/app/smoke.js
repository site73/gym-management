'use strict';
/**
 * 端到端冒烟测试：启动真实 HTTP 服务，用真实请求跑通 S1 全流程。
 * 运行：node app/smoke.js
 */
const { start } = require('./server');

const PORT = Number(process.env.SMOKE_PORT || 3111);
const BASE = `http://127.0.0.1:${PORT}`;
let pass = 0, fail = 0;
const rows = [];

function check(name, cond, extra = '') {
  if (cond) { pass++; rows.push([name, 'PASS', extra]); console.log(`✅ ${name} ${extra}`); }
  else { fail++; rows.push([name, 'FAIL', extra]); console.log(`❌ ${name} ${extra}`); }
}
async function call(method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => ({}));
  return { status: res.status, data };
}

(async () => {
  const server = await start(PORT, true);
  console.log('====================================================');
  console.log(' S1 切片 · 端到端冒烟测试（真实 HTTP 请求）');
  console.log(` 服务地址：${BASE}`);
  console.log('====================================================\n');

  await call('POST', '/api/admin/reset');   // 保证确定性

  // 1 健康检查
  const h = await call('GET', '/api/health');
  check('服务健康检查', h.status === 200 && h.data.ok === true);

  // 2 课程列表
  const c = await call('GET', '/api/courses');
  check('查询课程列表', c.status === 200 && c.data.length >= 4, `共 ${c.data.length} 门`);

  // 3 约课成功（SYS-R1/R3 通过）
  const b1 = await call('POST', '/api/bookings', { memberId: 1, courseId: 11 });
  check('张三约"动感单车"成功', b1.status === 200 && b1.data.ok, `bookingId=${b1.data.bookingId}`);

  // 4 幂等
  const b1b = await call('POST', '/api/bookings', { memberId: 1, courseId: 11 });
  check('重复提交保持幂等', b1b.status === 200 && b1b.data.idempotent === true);

  // 5 会籍过期拒绝（SYS-R1）
  const b2 = await call('POST', '/api/bookings', { memberId: 2, courseId: 11 });
  check('会籍过期被拒绝（SYS-R1）', b2.status === 409 && b2.data.ruleCode === 'SYS-R1', b2.data.reason);

  // 6 课程满员拒绝（SYS-R3）
  const b3 = await call('POST', '/api/bookings', { memberId: 3, courseId: 12 });
  check('课程满员被拒绝（SYS-R3）', b3.status === 409 && b3.data.ruleCode === 'SYS-R3', b3.data.reason);

  // 7 时间冲突拒绝（SYS-R3）
  const b4 = await call('POST', '/api/bookings', { memberId: 1, courseId: 14 });
  check('同时段冲突被拒绝（SYS-R3）', b4.status === 409 && b4.data.ruleCode === 'SYS-R3', b4.data.reason);

  // 8 签到成功 + 课包核销（SYS-R5）
  const before = (await call('GET', '/api/members/1')).data.packageRemaining;
  const ci = await call('POST', `/api/bookings/${b1.data.bookingId}/checkin`, { channel: 'scan' });
  const after = (await call('GET', '/api/members/1')).data.packageRemaining;
  check('扫码签到成功', ci.status === 200 && ci.data.ok);
  check('签到核销课包一次（SYS-R5）', after === before - 1, `课包 ${before} → ${after}`);

  // 9 取消释放名额
  const b5 = await call('POST', '/api/bookings', { memberId: 4, courseId: 13 });
  const beforeSeats = (await call('GET', '/api/courses')).data.find(x => x.id === 13).remaining;
  await call('POST', `/api/bookings/${b5.data.bookingId}/cancel`, {});
  const afterSeats = (await call('GET', '/api/courses')).data.find(x => x.id === 13).remaining;
  check('取消预约释放名额（SYS-R3）', afterSeats === beforeSeats + 1, `剩余 ${beforeSeats} → ${afterSeats}`);

  // 10 爽约判定与惩罚（SYS-R4，张三已有 2 次）
  const b6 = await call('POST', '/api/bookings', { memberId: 1, courseId: 13 });
  const ns = await call('POST', `/api/bookings/${b6.data.bookingId}/no-show`, {});
  check('超时未签到判爽约（累计 3 次）', ns.status === 200 && ns.data.noShowCount === 3, `累计=${ns.data.noShowCount}`);
  check('爽约达 3 次触发限制预约 7 天（SYS-R4）', ns.data.penaltyApplied === true);
  const b7 = await call('POST', '/api/bookings', { memberId: 1, courseId: 12 });
  check('受限制会员约课被拒绝（SYS-R4）', b7.status === 409 && b7.data.ruleCode === 'SYS-R4', b7.data.reason);

  // 11 支付异常（REQ-B5-004）
  const o = await call('POST', '/api/orders', { memberId: 2, bizType: 'membership', amount: 3000 });
  const nt = await call('POST', `/api/orders/${o.data.no}/notify`, { paidAmount: 300 });
  check('支付金额不符标记异常并告警（REQ-B5-004）', nt.status === 409 && nt.data.order.status === 'abnormal', nt.data.reason);

  // 12 流失风险评分（SYS-R7/R9，创新点）
  const risk = await call('POST', '/api/jobs/risk-score');
  check('流失风险评分生成跟进任务（SYS-R7/R9）', risk.status === 200 && risk.data.length > 0, `任务 ${risk.data.length} 条`);

  // 13 爽约预测（SYS-R8）
  const pred = await call('GET', '/api/predictions?courseId=11');
  check('爽约预测返回概率与动作（SYS-R8）', pred.status === 200 && pred.data.ok === true, `阈值 T=${pred.data.threshold}`);

  // 14 提成（SYS-R10）
  const st = await call('GET', '/api/settlement');
  check('私教提成核算（SYS-R10）', st.status === 200 && st.data.length > 0, `教练 ${st.data.length} 人`);

  // 15 经营摘要
  const sum = await call('GET', '/api/reports/summary');
  check('经营摘要报表', sum.status === 200 && sum.data.members > 0, `爽约率 ${sum.data.noShowRate}`);

  console.log('\n----------------------------------------------------');
  console.log(` 结果：通过 ${pass} / 失败 ${fail} / 共 ${pass + fail}`);
  console.log('----------------------------------------------------');

  const fs = require('fs');
  const path = require('path');
  const md = ['# 端到端冒烟测试结果（真实 HTTP 请求）', '',
    `- 服务地址：${BASE}`,
    `- 结果：**通过 ${pass} / 失败 ${fail} / 共 ${pass + fail}**`, '',
    '| 用例 | 结果 | 说明 |', '|---|---|---|',
    ...rows.map(r => `| ${r[0]} | ${r[1] === 'PASS' ? '✅ 通过' : '❌ 失败'} | ${r[2] || ''} |`), ''].join('\n');
  fs.writeFileSync(path.join(__dirname, 'smoke-report.md'), md, 'utf8');
  console.log('已生成：app/smoke-report.md');

  server.close();
  process.exit(fail === 0 ? 0 : 1);
})();
