'use strict';
/**
 * 业务服务层（S1 切片为主，附 S2/S3/S4 可演示能力）
 * 规则参数来自 rules.js（生产环境改为读 rule_config）；跨模块通过内部契约函数调用。
 */
const R = require('./rules');
const store = require('./store');

const now = () => new Date();
const daysBetween = (a, b) => Math.floor((a - b) / 86400000);

function db() { return store.load(); }
function sameId(a, b) { return String(a) === String(b); }
function findMember(id) { return db().members.find(m => sameId(m.id, id)); }
function findCourse(id) { return db().courses.find(c => sameId(c.id, id)); }
function findBooking(id) { return db().bookings.find(b => sameId(b.id, id)); }
function remaining(course) { return Math.max(0, course.capacity - course.booked); }

/* ============================== 查询 ============================== */
function listCourses() {
  return db().courses.map(c => ({ ...c, remaining: remaining(c) }));
}
function getMember(id) {
  const m = findMember(id);
  if (!m) return null;
  const penaltyDaysRemaining = m.penaltyUntil && new Date(m.penaltyUntil) > now()
    ? daysBetween(new Date(m.penaltyUntil), now()) + 1 : 0;
  return { ...m, penaltyDaysRemaining };
}
function listBookings(memberId) {
  return db().bookings
    .filter(b => !memberId || b.memberId === Number(memberId))
    .map(b => ({ ...b, courseName: (findCourse(b.courseId) || {}).name, time: (findCourse(b.courseId) || {}).time }));
}

/* ============================== S1 约课（SYS-R1/R2/R3/R4） ============================== */
function book(memberId, courseId) {
  const m = findMember(memberId), c = findCourse(courseId);
  if (!m || !c) return { ok: false, ruleCode: null, reason: '会员或课程不存在' };

  // SYS-R1 / SYS-R2 会籍有效性
  if (m.status !== R['SYS-R1'].requireStatus) {
    const ruleCode = m.status === 'frozen' ? 'SYS-R2' : 'SYS-R1';
    const reason = m.status === 'frozen' ? '会籍冻结中，暂不可约课' : '会籍已过期，请先续费';
    store.audit('booking.reject', 'booking', null, `${ruleCode} member=${m.id}`);
    return { ok: false, ruleCode, reason };
  }
  // SYS-R4 爽约惩罚
  if (m.penaltyUntil && new Date(m.penaltyUntil) > now()) {
    const left = daysBetween(new Date(m.penaltyUntil), now()) + 1;
    store.audit('booking.reject', 'booking', null, `SYS-R4 member=${m.id}`);
    return { ok: false, ruleCode: 'SYS-R4', reason: `已被限制预约，剩余 ${left} 天` };
  }
  // 幂等：同一会员同一课程不重复
  const exist = db().bookings.find(b => b.memberId === m.id && b.courseId === c.id
    && b.status !== 'cancelled');
  if (exist) return { ok: true, bookingId: exist.id, status: exist.status, idempotent: true };

  // SYS-R3 时间冲突
  if (R['SYS-R3'].timeConflictCheck) {
    const conflict = db().bookings.find(b => b.memberId === m.id
      && ['booked', 'checked_in'].includes(b.status)
      && (findCourse(b.courseId) || {}).time === c.time);
    if (conflict) {
      store.audit('booking.reject', 'booking', null, `SYS-R3 conflict member=${m.id}`);
      return { ok: false, ruleCode: 'SYS-R3', reason: '该时段已有预约，存在时间冲突' };
    }
  }
  // SYS-R3 容量
  if (R['SYS-R3'].capacityCheck && remaining(c) <= 0) {
    store.audit('booking.reject', 'booking', null, `SYS-R3 full course=${c.id}`);
    return { ok: false, ruleCode: 'SYS-R3', reason: '课程已满', waitlistAvailable: true };
  }

  c.booked += 1;
  const booking = {
    id: store.nextId('B'), memberId: m.id, courseId: c.id, status: 'booked',
    noShowProb: predictProb(m, c), checkinChannel: null, operatorId: null,
    coachId: c.coach, at: now().toISOString(),
  };
  db().bookings.push(booking);
  store.save();
  store.audit('booking.create', 'booking', booking.id, `member=${m.id} course=${c.id}`);
  return { ok: true, bookingId: booking.id, status: 'booked', ruleCode: null };
}

/* ============================== S1 签到 / 爽约 / 取消 ============================== */
function checkIn(bookingId, channel, operatorId) {
  const b = findBooking(bookingId);
  if (!b) return { ok: false, reason: '预约不存在' };
  if (b.status !== 'booked') return { ok: false, reason: `当前状态不可签到：${b.status}` };
  b.status = 'checked_in';
  b.checkinChannel = channel || 'scan';
  b.operatorId = operatorId || null;
  b.checkinAt = now().toISOString();
  // 课后核销课包（SYS-R5）并记录课消（供提成）
  const m = findMember(b.memberId);
  if (m && m.packageRemaining > 0) {
    m.packageRemaining = Math.max(0, m.packageRemaining - R['SYS-R5'].deductPerClass);
    db().consumptions.push({
      id: store.nextId('C'), bookingId: b.id, memberId: m.id, coachId: b.coachId,
      courseId: b.courseId, times: 1, amount: 200, at: now().toISOString(),
    });
  }
  store.save();
  store.audit('booking.checkin', 'booking', b.id, `channel=${b.checkinChannel}`);
  return { ok: true, booking: b };
}

function cancel(bookingId) {
  const b = findBooking(bookingId);
  if (!b) return { ok: false, reason: '预约不存在' };
  if (b.status !== 'booked') return { ok: false, reason: `当前状态不可取消：${b.status}` };
  b.status = 'cancelled';
  const c = findCourse(b.courseId);
  if (c) c.booked = Math.max(0, c.booked - 1);
  store.save();
  store.audit('booking.cancel', 'booking', b.id, null);
  return { ok: true, booking: b, remaining: c ? remaining(c) : null };
}

function markNoShow(bookingId) {
  const b = findBooking(bookingId);
  if (!b) return { ok: false, reason: '预约不存在' };
  if (b.status !== 'booked') return { ok: false, reason: `当前状态不可判爽约：${b.status}` };
  b.status = 'no_show';
  const m = findMember(b.memberId);
  m.noShowCount += 1;
  let penaltyApplied = false;
  if (m.noShowCount >= R['SYS-R4'].N) {
    m.penaltyUntil = new Date(Date.now() + R['SYS-R4'].restrictDays * 86400000).toISOString();
    penaltyApplied = true;
    store.audit('penalty.applied', 'member', m.id, `days=${R['SYS-R4'].restrictDays}`);
  }
  store.save();
  store.audit('booking.noShow', 'booking', b.id, `累计=${m.noShowCount}`);
  return { ok: true, noShowCount: m.noShowCount, penaltyApplied };
}

/* ============================== S2 收费（REQ-B5-001/004） ============================== */
function createOrder(memberId, bizType, amount, times) {
  const o = {
    no: store.nextId('O'), memberId: Number(memberId), bizType, amount: Number(amount),
    paidAmount: null, status: 'pending', times: times || 0, at: now().toISOString(),
  };
  db().orders.push(o);
  store.save();
  return o;
}
function payOrder(orderNo) {
  const o = db().orders.find(x => x.no === orderNo);
  if (!o) return { ok: false, reason: '订单不存在' };
  o.status = 'paid'; o.paidAmount = o.amount;
  const m = findMember(o.memberId);
  m.status = 'active';
  if (o.bizType === 'pt_package' && o.times) m.packageRemaining = (m.packageRemaining || 0) + o.times;
  if (o.bizType === 'membership') m.daysToExpire = 365;
  store.save();
  store.audit('order.paid', 'order', o.no, `amount=${o.amount}`);
  return { ok: true, order: o };
}
/** 支付回调（含金额核对与幂等） */
function payNotify(orderNo, paidAmount) {
  const o = db().orders.find(x => x.no === orderNo);
  if (!o) return { ok: false, reason: '订单不存在' };
  if (o.status === 'paid') return { ok: true, order: o, idempotent: true };
  if (Number(paidAmount) !== o.amount) {
    o.status = 'abnormal'; o.paidAmount = Number(paidAmount);
    db().alerts.push({ at: now().toISOString(), type: 'PAY_AMOUNT_MISMATCH', order: o.no });
    store.save();
    store.audit('order.abnormal', 'order', o.no, 'amountMismatch');
    return { ok: false, order: o, ruleCode: 'REQ-B5-004', reason: '金额与订单不一致，已标记异常并告警' };
  }
  return payOrder(o.no);
}

/* ============================== S4 预警（创新点：SYS-R7/R8/R9） ============================== */
function predictProb(member, course) {
  // 规则式起步（数据充足后可替换为模型）：历史爽约率 + 到店间隔 + 新会员权重
  let p = 0.1;
  const total = (member.visits30 || 0) + (member.noShowCount || 0);
  if (total > 0) p += (member.noShowCount / total) * 0.6;
  if ((member.lastVisitDays || 0) > 14) p += 0.2;
  if ((member.createdDaysAgo || 99) <= 30) p += 0.15;
  return Math.min(0.99, Number(p.toFixed(3)));
}

function riskScore() {
  const d = db();
  d.tasks = d.tasks.filter(t => t.status === 'done');
  const list = [];
  for (const m of d.members) {
    const hits = [];
    if ((m.lastVisitDays || 0) >= R['SYS-R7'].noVisitWeeks * 7) hits.push(`连续 ${R['SYS-R7'].noVisitWeeks} 周未到店`);
    const bookings = d.bookings.filter(b => b.memberId === m.id);
    const noShow = bookings.filter(b => b.status === 'no_show').length;
    if (bookings.length > 0 && noShow / bookings.length > R['SYS-R7'].noShowRate) {
      hits.push(`近 30 天爽约率 ${(noShow / bookings.length * 100).toFixed(0)}%`);
    }
    if (hits.length) {
      m.riskLevel = 'high';
      const t = { id: store.nextId('T'), memberId: m.id, memberName: m.name, taskType: 'churn_risk', ruleCode: 'SYS-R7', hit: hits.join('；'), status: 'pending', dueAt: new Date(Date.now() + 2 * 86400000).toISOString() };
      d.tasks.push(t); list.push(t);
    } else { m.riskLevel = 'low'; }

    // SYS-R9 新会员首月跟进
    if ((m.createdDaysAgo || 999) <= R['SYS-R9'].days && (m.visits30 || 0) < R['SYS-R9'].minVisits) {
      const t = { id: store.nextId('T'), memberId: m.id, memberName: m.name, taskType: 'new_member_30d', ruleCode: 'SYS-R9', hit: `办卡 ${m.createdDaysAgo} 天到店 ${m.visits30} 次`, status: 'pending', dueAt: new Date(Date.now() + 86400000).toISOString() };
      d.tasks.push(t); list.push(t);
    }
  }
  store.save();
  store.audit('job.riskScore', 'risk', null, `命中 ${list.length} 条`);
  return list;
}

/** SYS-R8 爽约预测：返回概率与推荐动作 */
function noShowPrediction(courseId) {
  const c = findCourse(courseId);
  if (!c) return { ok: false, reason: '课程不存在' };
  const rows = db().bookings
    .filter(b => b.courseId === c.id && b.status === 'booked')
    .map(b => {
      const m = findMember(b.memberId);
      const prob = predictProb(m || {}, c);
      const full = remaining(c) <= 0;
      const action = prob > R['SYS-R8'].T ? (full ? 'release' : 'remind') : 'none';
      return { bookingId: b.id, memberName: (m || {}).name, prob, action };
    });
  return { ok: true, course: c.name, threshold: R['SYS-R8'].T, rows };
}

/* ============================== S3 提成（SYS-R10） ============================== */
function settlement(period) {
  const d = db();
  const byCoach = {};
  for (const x of d.consumptions) {
    (byCoach[x.coachId] = byCoach[x.coachId] || { coachId: x.coachId, times: 0, amount: 0 });
    byCoach[x.coachId].times += x.times;
    byCoach[x.coachId].amount += x.amount;
  }
  const rate = R['SYS-R10'].rate;
  return Object.values(byCoach).map(v => ({
    ...v, period: period || '2026-10', rate,
    commission: R['SYS-R10'].calcType === 'by_times'
      ? Number((v.times * 40 * rate).toFixed(2))   // 以每节课 40 元课时费为基准
      : Number((v.amount * rate).toFixed(2)),
  }));
}

/* ============================== 报表（RP 摘要） ============================== */
function summary() {
  const d = db();
  const active = d.members.filter(m => m.status === 'active').length;
  const totalBookings = d.bookings.length;
  const noShow = d.bookings.filter(b => b.status === 'no_show').length;
  const checkedIn = d.bookings.filter(b => b.status === 'checked_in').length;
  return {
    members: d.members.length,
    activeMembers: active,
    courses: d.courses.length,
    bookings: totalBookings,
    checkedIn,
    noShow,
    noShowRate: totalBookings ? (noShow / totalBookings * 100).toFixed(1) + '%' : '0%',
    pendingTasks: d.tasks.filter(t => t.status === 'pending').length,
    abnormalOrders: d.orders.filter(o => o.status === 'abnormal').length,
    alerts: d.alerts.length,
  };
}

module.exports = {
  listCourses, getMember, listBookings,
  book, checkIn, cancel, markNoShow,
  createOrder, payOrder, payNotify,
  riskScore, noShowPrediction, settlement, summary,
  predictProb,
};
