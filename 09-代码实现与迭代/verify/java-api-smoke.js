'use strict';
/**
 * Spring Boot 后端端到端冒烟（真实 HTTP 请求，H2 本地库）
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
  try { data = await res.json(); } catch { /* 204 无响应体 */ }
  return { status: res.status, data };
}

(async () => {
  console.log('========================================================');
  console.log(' Spring Boot 后端端到端冒烟（H2 local profile）');
  console.log(` 服务地址：${BASE}`);
  console.log('========================================================\n');

  const h = await call('GET', '/actuator/health');
  check('健康检查 UP', h.status === 200 && h.data && h.data.status === 'UP');

  const c1 = await call('GET', '/api/courses');
  check('查询课程列表', c1.status === 200 && Array.isArray(c1.data) && c1.data.length === 3,
        `共 ${Array.isArray(c1.data) ? c1.data.length : 0} 门`);
  const bike = (c1.data || []).find(c => c.id === 11);
  check('课程字段与余位正确', bike && bike.remaining === 2, bike ? `动感单车剩余 ${bike.remaining}` : '');

  // 约课成功（SYS-R1/R3 通过）
  const b1 = await call('POST', '/api/bookings', { memberId: 1, courseId: 11 });
  check('张三约课成功', b1.status === 200 && b1.data && b1.data.ok === true, `bookingId=${b1.data && b1.data.bookingId}`);

  // 幂等
  const b2 = await call('POST', '/api/bookings', { memberId: 1, courseId: 11 });
  check('重复提交幂等', b2.status === 200 && b2.data && b2.data.ok === true);

  // 会籍过期（SYS-R1）
  const b3 = await call('POST', '/api/bookings', { memberId: 2, courseId: 11 });
  check('会籍过期被拒绝（SYS-R1）', b3.status === 409 && b3.data && b3.data.ruleCode === 'SYS-R1',
        b3.data ? b3.data.reason : '');

  // 课程满员（SYS-R3）
  const b4 = await call('POST', '/api/bookings', { memberId: 3, courseId: 12 });
  check('课程满员被拒绝（SYS-R3）', b4.status === 409 && b4.data && b4.data.ruleCode === 'SYS-R3',
        b4.data ? b4.data.reason : '');

  // 签到（REQ-B4-002）
  const ci = await call('POST', `/api/bookings/${b1.data.bookingId}/checkin`, { channel: 'scan' });
  check('扫码签到成功', ci.status === 204);

  // 取消释放名额（SYS-R3）
  const b5 = await call('POST', '/api/bookings', { memberId: 3, courseId: 13 });
  const before = (await call('GET', '/api/courses')).data.find(x => x.id === 13).remaining;
  await call('POST', `/api/bookings/${b5.data.bookingId}/cancel`, {});
  const after = (await call('GET', '/api/courses')).data.find(x => x.id === 13).remaining;
  check('取消预约释放名额（SYS-R3）', after === before + 1, `剩余 ${before} -> ${after}`);

  // 爽约判定（REQ-B4-003/SYS-R4）
  const b6 = await call('POST', '/api/bookings', { memberId: 3, courseId: 13 });
  const ns = await call('POST', `/api/bookings/${b6.data.bookingId}/no-show`, {});
  check('爽约判定返回累计次数（SYS-R4）', ns.status === 200 && ns.data >= 1, `累计=${ns.data}`);

  console.log('\n--------------------------------------------------------');
  console.log(` 结果：通过 ${pass} / 失败 ${fail} / 共 ${pass + fail}`);
  console.log('--------------------------------------------------------');

  const md = ['# Spring Boot 后端端到端冒烟结果（真实 HTTP）', '',
    `- 服务地址：${BASE}（profile=local，H2 内存库）`,
    `- 结果：**通过 ${pass} / 失败 ${fail} / 共 ${pass + fail}**`, '',
    '| 用例 | 结果 | 说明 |', '|---|---|---|',
    ...rows.map(r => `| ${r[0]} | ${r[1] === 'PASS' ? '✅ 通过' : '❌ 失败'} | ${r[2] || ''} |`), ''].join('\n');
  fs.writeFileSync(path.join(__dirname, 'java-api-smoke-report.md'), md, 'utf8');
  console.log('已生成：verify/java-api-smoke-report.md');

  process.exit(fail === 0 ? 0 : 1);
})();
