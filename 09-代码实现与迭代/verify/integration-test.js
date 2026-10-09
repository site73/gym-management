'use strict';
/**
 * 集成测试（真实 HTTP）：登录 → 角色路由与权限 → 会员端流程 → 门店后台流程 → 回调放行
 *
 * 前置：后端已启动（MySQL 模式 8080），并已执行 verify/reset-demo-data.sql
 * 运行：node verify/integration-test.js
 */
const BASE = process.env.API_BASE || 'http://127.0.0.1:8080';
const fs = require('fs');
const path = require('path');

let pass = 0, fail = 0, seq = 0;
const rows = [];

function check(group, name, expected, actual, cond) {
  const id = 'IT-' + String(++seq).padStart(3, '0');
  const r = cond ? 'PASS' : 'FAIL';
  if (cond) pass++; else fail++;
  rows.push([id, group, name, expected, actual, r]);
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${id}  ${name}  | 期望: ${expected} | 实际: ${actual}`);
}
async function call(method, p, body, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['X-Token'] = token;
  let res, data = null;
  try {
    res = await fetch(BASE + p, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    try { data = await res.json(); } catch (e) { data = null; }
  } catch (e) {
    return { status: 0, data: { reason: '网络错误 ' + e.message } };
  }
  return { status: res.status, data };
}
const login = async (u, p) => call('POST', '/api/auth/login', { username: u, password: p });

(async () => {
  console.log('============================================================');
  console.log(' 健身房运营管理系统 · 集成测试（登录 / 权限 / 全流程）');
  console.log(` 服务地址：${BASE}`);
  console.log('============================================================\n');

  /* ==================== A 认证 ==================== */
  const A = 'A 登录认证';
  let r = await call('GET', '/api/courses');
  check(A, '未登录访问业务接口', '401', `${r.status}`, r.status === 401);

  r = await login('member1', 'wrong-password');
  check(A, '密码错误被拒绝', '401', `${r.status} ${r.data && r.data.reason}`, r.status === 401);

  r = await login('not-exist', '123456');
  check(A, '账号不存在提示统一', '401', `${r.status}`, r.status === 401);

  const m1 = await login('member1', '123456');
  check(A, '会员登录成功', '200 + role=MEMBER + memberId=1',
        `${m1.status} role=${m1.data && m1.data.user.role} memberId=${m1.data && m1.data.user.memberId}`,
        m1.status === 200 && m1.data.user.role === 'MEMBER' && m1.data.user.memberId === 1);

  const mgr = await login('manager', '123456');
  check(A, '店长登录成功且为 STAFF', '200 + role=STAFF + admin=false',
        `${mgr.status} role=${mgr.data && mgr.data.user.role} admin=${mgr.data && mgr.data.user.admin}`,
        mgr.status === 200 && mgr.data.user.role === 'STAFF' && mgr.data.user.admin === false);

  const adm = await login('admin', '123456');
  check(A, '管理员登录成功且 admin=true', '200 + admin=true',
        `${adm.status} admin=${adm.data && adm.data.user.admin}`,
        adm.status === 200 && adm.data.user.admin === true);

  const T_M1 = m1.data.token, T_MGR = mgr.data.token, T_ADM = adm.data.token;

  r = await call('GET', '/api/auth/me', undefined, T_M1);
  check(A, '凭令牌获取当前用户', '200 + member1', `${r.status} ${r.data && r.data.username}`,
        r.status === 200 && r.data.username === 'member1');

  r = await call('GET', '/api/auth/me', undefined, 'invalid-token');
  check(A, '无效令牌被拒绝', '401', `${r.status}`, r.status === 401);

  /* ---------- A2 会员自助注册 ---------- */
  const uniq = 'u' + String(Date.now()).slice(-7);
  const reg = await call('POST', '/api/auth/register', {
    username: uniq, password: 'pass123456', name: '注册测试会员', phone: '13900001111'
  });
  check(A, '会员自助注册', '200 + role=MEMBER + 绑定会员 ID',
        `${reg.status} role=${reg.data && reg.data.user.role} memberId=${reg.data && reg.data.user.memberId}`,
        reg.status === 200 && reg.data.user.role === 'MEMBER' && !!reg.data.user.memberId);

  const regToken = reg.data && reg.data.token;
  const regMe = await call('GET', '/api/auth/me', undefined, regToken);
  check(A, '注册即登录（令牌可用）', '200', `${regMe.status}`, regMe.status === 200);

  const regDup = await call('POST', '/api/auth/register', {
    username: uniq, password: 'pass123456', name: '重名会员'
  });
  check(A, '用户名重复无法注册', '400 + 含已被占用',
        `${regDup.status} ${regDup.data && regDup.data.reason}`,
        regDup.status === 400 && /已被占用/.test(regDup.data.reason || ''));

  const regShort = await call('POST', '/api/auth/register', {
    username: uniq + 'x', password: '123', name: '短密码'
  });
  check(A, '密码过短无法注册', '400 + 含密码至少',
        `${regShort.status} ${regShort.data && regShort.data.reason}`,
        regShort.status === 400 && /密码至少/.test(regShort.data.reason || ''));

  const regNoName = await call('POST', '/api/auth/register', {
    username: uniq + 'y', password: 'pass123456', name: ''
  });
  check(A, '姓名为空无法注册', '400', `${regNoName.status}`, regNoName.status === 400);

  const regInfo = await call('GET', `/api/members/${reg.data.user.memberId}`, undefined, regToken);
  check(A, '新会员可查看本人档案', '200 + 状态为潜在会员',
        `${regInfo.status} status=${regInfo.data && regInfo.data.status}`,
        regInfo.status === 200 && regInfo.data.status === 'potential');

  /* ==================== B 角色权限 ==================== */
  const B = 'B 角色权限';
  r = await call('GET', '/api/members', undefined, T_M1);
  check(B, '会员访问会员列表', '403', `${r.status}`, r.status === 403);

  r = await call('GET', '/api/members/1', undefined, T_M1);
  check(B, '会员查看本人档案', '200', `${r.status}`, r.status === 200);

  r = await call('GET', '/api/members/2', undefined, T_M1);
  check(B, '会员查看他人档案', '403', `${r.status}`, r.status === 403);

  r = await call('GET', '/api/orders', undefined, T_M1);
  check(B, '会员访问收费模块', '403', `${r.status}`, r.status === 403);

  r = await call('GET', '/api/reports/summary', undefined, T_M1);
  check(B, '会员访问经营摘要', '403', `${r.status}`, r.status === 403);

  r = await call('POST', '/api/admin/reset', {}, T_M1);
  check(B, '会员执行数据重置', '403', `${r.status}`, r.status === 403);

  r = await call('POST', '/api/admin/reset', {}, T_MGR);
  check(B, '店长执行数据重置（非管理员）', '403', `${r.status}`, r.status === 403);

  r = await call('GET', '/api/bookings?memberId=2', undefined, T_M1);
  const onlySelf = Array.isArray(r.data) && r.data.every(b => b.memberId === 1);
  check(B, '会员查询他人预约被强制为本人', '仅返回本人数据', `${r.status} memberIds=${[...new Set((r.data || []).map(b => b.memberId))]}`,
        r.status === 200 && onlySelf);
  r = await call('GET', '/api/members', undefined, T_MGR);
  check(B, '店长查看会员列表', '200 且 ≥4 人（注册用例会新增会员）',
        `${r.status} ${Array.isArray(r.data) ? r.data.length : 0} 人`,
        r.status === 200 && r.data.length >= 4);

  /* ==================== C 会员端流程 ==================== */
  const C = 'C 会员端流程';
  r = await call('GET', '/api/courses', undefined, T_M1);
  const before = (r.data || []).find(c => c.id === 11)?.remaining;
  check(C, '会员查看课程列表', '200', `${r.status} 动感单车剩余 ${before}`, r.status === 200);

  r = await call('POST', '/api/bookings', { courseId: 11 }, T_M1);
  const bid = r.data && r.data.bookingId;
  check(C, '会员约课成功', '200 + booked', `${r.status} bookingId=${bid}`, r.status === 200 && r.data.ok === true);

  r = await call('POST', '/api/bookings', { courseId: 11 }, T_M1);
  check(C, '会员重复约课幂等', '同一预约号', `${r.status} bookingId=${r.data && r.data.bookingId}`,
        r.status === 200 && r.data.bookingId === bid);

  r = await call('POST', '/api/bookings', { memberId: 2, courseId: 13 }, T_M1);
  const forcedSelf = r.status === 200;
  check(C, '会员伪造他人 memberId 约课被强制为本人', '按本人下单', `${r.status}`, forcedSelf);

  r = await call('GET', '/api/bookings', undefined, T_M1);
  const allSelf = Array.isArray(r.data) && r.data.length >= 1 && r.data.every(b => b.memberId === 1);
  check(C, '会员查看我的预约', '仅本人且非空', `${r.status} ${r.data.length} 条 状态=${[...new Set(r.data.map(b => b.status))]}`,
        r.status === 200 && allSelf);

  r = await call('POST', `/api/bookings/${bid}/checkin`, {}, T_M1);
  check(C, '会员扫码签到', '204', `${r.status}`, r.status === 204);

  r = await call('POST', `/api/bookings/${bid}/checkin`, {}, T_M1);
  check(C, '重复签到被拒绝', '409', `${r.status} ${r.data && r.data.code}`, r.status === 409);

  r = await call('POST', '/api/bookings/' + bid + '/no-show', {}, T_M1);
  check(C, '会员尝试判爽约', '403（仅门店）', `${r.status}`, r.status === 403);

  // 换一门课验证取消与名额释放
  r = await call('POST', '/api/bookings', { courseId: 13 }, T_M1);
  const bid2 = r.data.bookingId;
  const seatsBefore = (await call('GET', '/api/courses', undefined, T_M1)).data.find(c => c.id === 13).remaining;
  r = await call('POST', `/api/bookings/${bid2}/cancel`, {}, T_M1);
  const seatsAfter = (await call('GET', '/api/courses', undefined, T_M1)).data.find(c => c.id === 13).remaining;
  check(C, '会员取消预约并释放名额', `剩余 ${seatsBefore + 1}`, `${r.status} 剩余 ${seatsAfter}`,
        r.status === 200 && seatsAfter === seatsBefore + 1);

  r = await call('POST', `/api/bookings/${bid2}/cancel`, {}, T_M1);
  check(C, '重复取消被拒绝', '409', `${r.status}`, r.status === 409);

  // 会籍过期的会员2
  const m2 = await login('member2', '123456');
  r = await call('POST', '/api/bookings', { courseId: 11 }, m2.data.token);
  check(C, '会籍过期会员约课被拒（SYS-R1）', '409 + SYS-R1', `${r.status} ${r.data && r.data.ruleCode}`,
        r.status === 409 && r.data.ruleCode === 'SYS-R1');

  /* ==================== D 门店后台流程 ==================== */
  const D = 'D 门店后台流程';
  r = await call('GET', '/api/bookings', undefined, T_MGR);
  check(D, '店长查看全部预约', '200', `${r.status} ${Array.isArray(r.data) ? r.data.length : 0} 条`, r.status === 200);

  const target = (r.data || []).find(b => b.status === 'booked');
  if (target) {
    const ns = await call('POST', `/api/bookings/${target.id}/no-show`, {}, T_MGR);
    check(D, '店长判爽约并返回累计次数', '200', `${ns.status} 累计=${ns.data && ns.data.noShowCount}`,
          ns.status === 200 && ns.data.noShowCount >= 1);
  }

  r = await call('POST', '/api/orders', { memberId: 2, bizType: 'membership', amount: 3000, times: 0 }, T_MGR);
  const orderNo = r.data && r.data.orderNo;
  check(D, '店长创建订单', '200 + pending', `${r.status} ${orderNo}`, r.status === 200 && r.data.status === 'pending');

  r = await call('POST', `/api/orders/${orderNo}/pay`, {}, T_MGR);
  check(D, '店长受理支付', '200 + paid', `${r.status} ${r.data && r.data.status}`, r.status === 200 && r.data.status === 'paid');

  r = await call('POST', '/api/orders', { memberId: 2, bizType: 'membership', amount: 3000, times: 0 }, T_MGR);
  const orderNo2 = r.data.orderNo;
  r = await call('POST', `/api/pay/notify/${orderNo2}`, { paidAmount: 300, outTradeNo: 'BAD' });
  check(D, '支付回调免令牌放行且金额不符标异常', '409 + abnormal',
        `${r.status} ${r.data && r.data.status}`, r.status === 409 && r.data.status === 'abnormal');

  r = await call('POST', `/api/pay/notify/${orderNo}`, { paidAmount: 3000, outTradeNo: 'DUP' });
  check(D, '重复回调幂等', '200 + paid', `${r.status} ${r.data && r.data.status}`, r.status === 200 && r.data.status === 'paid');

  r = await call('POST', '/api/settlements', {}, T_MGR);
  check(D, '生成对账单', '200', `${r.status} ¥${r.data && r.data.totalAmount}`, r.status === 200);

  r = await call('POST', '/api/jobs/renew-remind?days=7', {}, T_MGR);
  check(D, '到期提醒扫描（SYS-R6）', '200', `${r.status} 需提醒 ${r.data && r.data.count} 人`, r.status === 200);

  r = await call('POST', '/api/jobs/risk-score', {}, T_MGR);
  check(D, '风险扫描（SYS-R7 / SYS-R9）', '200 且有任务', `${r.status} 任务 ${r.data && r.data.tasks.length} 条`,
        r.status === 200 && r.data.tasks.length >= 1);

  r = await call('GET', '/api/risks', undefined, T_MGR);
  const riskTasks = r.data && Array.isArray(r.data.tasks) ? r.data.tasks : [];
  check(D, '预警任务与统计', '200 + 含 tasks 与 totalMembers',
        `${r.status} tasks=${riskTasks.length} highRisk=${r.data && r.data.highRisk}`,
        r.status === 200 && Array.isArray(r.data.tasks) && typeof r.data.totalMembers === 'number');

  // 先制造一笔待核验预约，保证预测有数据可算（否则 rows 恒为 0，测不出算法）
  await call('POST', '/api/bookings', { memberId: 2, courseId: 11 }, T_MGR);
  r = await call('GET', '/api/predictions?courseId=11', undefined, T_MGR);
  const row = (r.data && r.data.rows[0]) || null;
  check(D, '爽约预测（SYS-R8）给出概率与建议动作', '200 + 至少 1 行 + prob∈(0,1]',
        `${r.status} T=${r.data && r.data.threshold} rows=${r.data && r.data.rows.length} prob=${row && row.prob} action=${row && row.action}`,
        r.status === 200 && r.data.rows.length >= 1 && row.prob > 0 && row.prob <= 1 && ['remind', 'release', 'none'].includes(row.action));

  r = await call('GET', '/api/commissions?days=30', undefined, T_MGR);
  check(D, '提成核算（SYS-R10）', '200 + 含比例', `${r.status} rate=${r.data && r.data.rate}`, r.status === 200 && r.data.rate > 0);

  r = await call('GET', '/api/reports/summary', undefined, T_MGR);
  check(D, '经营摘要', '200 + 会员数与列表一致',
        `${r.status} 会员 ${r.data && r.data.members} 爽约率 ${r.data && r.data.noShowRate}`,
        r.status === 200 && r.data.members >= 4);

  r = await call('GET', '/api/audit', undefined, T_MGR);
  check(D, '审计日志', '200 且有记录', `${r.status} ${Array.isArray(r.data) ? r.data.length : 0} 条`,
        r.status === 200 && r.data.length >= 1);

  r = await call('POST', '/api/admin/reset', {}, T_ADM);
  check(D, '管理员重置演示数据', '200', `${r.status}`, r.status === 200);

  /* ==================== E 排课与课程维护（管理员 / 店长） ==================== */
  const E = 'E 排课与课程维护';
  const future = new Date(Date.now() + 10 * 86400000);
  const iso = d => new Date(d).toISOString().slice(0, 16);
  const s0 = iso(future), e0 = iso(future.getTime() + 3600000);
  // 每次执行生成唯一编号 / 教练 / 场地，避免与历史数据冲突，保证用例可反复重跑
  const tag = String(Date.now()).slice(-7);
  const cA = 'C' + tag + 'A';
  const cB = 'C' + tag + 'B';
  const cC = 'C' + tag + 'C';
  const cD = 'C' + tag + 'D';
  const coach = 900000 + Number(tag.slice(-4));
  const room = 'ZZ' + tag;

  const created = await call('POST', '/api/courses', {
    code: cA, name: '搏击操', type: 'group',
    coachId: coach, room: room, startTime: s0, endTime: e0, capacity: 18
  }, T_ADM);
  check(E, '管理员排课（新建课程）', '200 + 返回 id',
        `${created.status} id=${created.data && created.data.id}`,
        created.status === 200 && !!created.data.id);
  const cid = created.data && created.data.id;

  const byManager = await call('POST', '/api/courses', {
    code: cB, name: '店长排的课', type: 'group',
    coachId: coach + 1, room: room + 'B',
    startTime: iso(new Date(future.getTime() + 5 * 3600000)),
    endTime: iso(new Date(future.getTime() + 6 * 3600000)), capacity: 15
  }, T_MGR);
  check(E, '店长排课（本次新放开的权限）', '200 + 返回 id',
        `${byManager.status} id=${byManager.data && byManager.data.id}`,
        byManager.status === 200 && !!byManager.data.id);

  const byMember = await call('POST', '/api/courses', {
    code: cC, name: '会员排的课', type: 'group',
    coachId: coach, room: room, startTime: s0, endTime: e0, capacity: 10
  }, T_M1);
  check(E, '会员排课被拒绝', '403', `${byMember.status}`, byMember.status === 403);

  const dup = await call('POST', '/api/courses', {
    code: cA, name: '重号课', type: 'group',
    coachId: coach, room: room, startTime: s0, endTime: e0, capacity: 18
  }, T_ADM);
  check(E, '课程编号重复被拒绝', '400 + 含编号',
        `${dup.status} ${dup.data && dup.data.reason}`,
        dup.status === 400 && /编号/.test(dup.data.reason || ''));

  const badRange = await call('POST', '/api/courses', {
    code: cC + '1', name: '时间倒置', type: 'group',
    coachId: coach, room: room, startTime: e0, endTime: s0, capacity: 10
  }, T_ADM);
  check(E, '结束时间早于开始时间被拒绝', '400', `${badRange.status}`, badRange.status === 400);

  const badCap = await call('POST', '/api/courses', {
    code: cC + '2', name: '容量为零', type: 'group',
    coachId: coach, room: room, startTime: s0, endTime: e0, capacity: 0
  }, T_ADM);
  check(E, '容量小于 1 被拒绝', '400', `${badCap.status}`, badCap.status === 400);

  const past = new Date(Date.now() - 2 * 86400000);
  const badPast = await call('POST', '/api/courses', {
    code: cC + '3', name: '过去的课', type: 'group',
    coachId: coach, room: room,
    startTime: iso(past), endTime: iso(past.getTime() + 3600000), capacity: 10
  }, T_ADM);
  check(E, '开始时间早于当前时间被拒绝', '400', `${badPast.status}`, badPast.status === 400);

  // 与刚创建的 cA 同一教练、同一场地、同一时段 → 必须被 SYS-R3 拦下
  const conflict = await call('POST', '/api/courses', {
    code: cD, name: '撞课', type: 'group',
    coachId: coach, room: room, startTime: s0, endTime: e0, capacity: 20
  }, T_ADM);
  check(E, '教练/场地排课冲突被拒绝（SYS-R3）', '400 + 含 SYS-R3',
        `${conflict.status} ${conflict.data && conflict.data.reason}`,
        conflict.status === 400 && /SYS-R3/.test(conflict.data.reason || ''));

  const upd = await call('PUT', `/api/courses/${cid}`, {
    code: cA, name: '搏击操进阶', type: 'group',
    coachId: coach, room: room, startTime: s0, endTime: e0, capacity: 22
  }, T_MGR);
  check(E, '店长修改课程', '200 + 名称/容量生效',
        `${upd.status} name=${upd.data && upd.data.name} cap=${upd.data && upd.data.capacity}`,
        upd.status === 200 && upd.data.name === '搏击操进阶' && upd.data.capacity === 22);

  const off = await call('DELETE', `/api/courses/${cid}`, {}, T_MGR);
  const after = await call('GET', `/api/courses/${cid}`, undefined, T_ADM);
  check(E, '店长下架课程', '200 + 状态变 cancelled',
        off.status + ' status=' + (after.data && after.data.status),
        off.status === 200 && after.data.status === 'cancelled');

  const rep = await call('POST', `/api/courses/${cid}/publish`, {}, T_MGR);
  check(E, '店长重新上架课程', '200 + published',
        `${rep.status} status=${rep.data && rep.data.status}`,
        rep.status === 200 && rep.data.status === 'published');

  /* ---------- E2 会员自愿选课 / 退课（所有登录用户可用） ---------- */
  const enrollCourse = cid;   // 用上面新排的课，避免影响演示数据
  const en = await call('POST', '/api/enrollments', { courseId: enrollCourse }, T_M1);
  check(E, '会员自愿选课', '200 + 返回预约号',
        `${en.status} bookingId=${en.data && en.data.bookingId}`,
        en.status === 200 && !!en.data.bookingId);
  const enId = en.data && en.data.bookingId;

  const enAgain = await call('POST', '/api/enrollments', { courseId: enrollCourse }, T_M1);
  check(E, '重复选课保持幂等', '同一预约号',
        `${enAgain.status} bookingId=${enAgain.data && enAgain.data.bookingId}`,
        enAgain.status === 200 && String(enAgain.data.bookingId) === String(enId));

  const forged = await call('POST', '/api/enrollments',
      { memberId: 2, courseId: enrollCourse }, T_M1);
  check(E, '会员伪造他人会员号选课被强制为本人', '按本人处理',
        `${forged.status}`, forged.status === 200);

  const sched = await call('GET', '/api/enrollments', undefined, T_M1);
  const mine = Array.isArray(sched.data) && sched.data.length >= 1;
  check(E, '我的课表', '200 + 至少一条',
        `${sched.status} ${Array.isArray(sched.data) ? sched.data.length : 0} 条`, mine);

  const wd = await call('POST', `/api/enrollments/${enId}/withdraw`, {}, T_M1);
  check(E, '会员自愿退课', '200', `${wd.status}`, wd.status === 200);

  const wdAgain = await call('POST', `/api/enrollments/${enId}/withdraw`, {}, T_M1);
  check(E, '重复退课被拒绝', '409 + INVALID_STATE',
        `${wdAgain.status} ${wdAgain.data && wdAgain.data.code}`,
        wdAgain.status === 409);

  /* ==================== F 会话 ==================== */
  const F = 'F 会话管理';
  r = await call('POST', '/api/auth/logout', {}, T_M1);
  check(E, '退出登录', '200', `${r.status}`, r.status === 200);

  r = await call('GET', '/api/auth/me', undefined, T_M1);
  check(E, '退出后令牌失效', '401', `${r.status}`, r.status === 401);

  const page = await fetch(BASE + '/');
  const html = await page.text();
  check(E, '统一页面可访问', '200 + 登录入口 + 身份选择',
        `HTTP ${page.status} 身份选择=${html.includes('idMember') && html.includes('idManager') && html.includes('idAdmin')}`,
        page.status === 200 && html.includes('选择登录身份')
        && html.includes('idMember') && html.includes('idManager') && html.includes('idAdmin')
        && html.includes('id="sidebar"'));

  console.log('\n------------------------------------------------------------');
  console.log(` 结果：通过 ${pass} / 失败 ${fail} / 共 ${pass + fail}`);
  console.log('------------------------------------------------------------');

  const md = ['# 集成测试报告（登录 / 权限 / 全流程）', '',
    `- 服务地址：${BASE}（MySQL 8.0 模式）`,
    `- 执行时间：${new Date().toISOString()}`,
    `- 结果：**通过 ${pass} / 失败 ${fail} / 共 ${pass + fail}**`, '',
    '| 编号 | 分组 | 用例 | 期望 | 实际 | 结果 |', '|---|---|---|---|---|---|',
    ...rows.map(x => `| ${x[0]} | ${x[1]} | ${x[2]} | ${x[3]} | ${x[4]} | ${x[5] === 'PASS' ? '✅ 通过' : '❌ 失败'} |`),
    ''].join('\n');
  fs.writeFileSync(path.join(__dirname, 'integration-test-report.md'), md, 'utf8');
  console.log('已生成：verify/integration-test-report.md');

  process.exit(fail === 0 ? 0 : 1);
})();
