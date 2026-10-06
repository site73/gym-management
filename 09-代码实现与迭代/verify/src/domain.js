'use strict';
/**
 * S1 切片参考实现（内存域模型）—— 与 Java 端规则实现同源。
 * 规则参数与 07-数据库与部署设计/db/seed/R__seed_base_data.sql 中 rule_config 默认值一致。
 */

const RULES = {
  'SYS-R1': { requireStatus: 'active' },
  'SYS-R2': { frozenBlocksBooking: true },
  'SYS-R3': { capacityCheck: true, timeConflictCheck: true },
  'SYS-R4': { N: 3, restrictDays: 7 },
  'SYS-R5': { deductPerClass: 1 },
  'SYS-R6': { D: 7 },
};

class BookingClass {
  constructor(name) {
    this.name = name;
    const m = name.match(/\d{1,2}:\d{2}/);
    this.time = m ? m[0] : '00:00';
    this.coach = '待定';
    this.capacity = 20;
    this.booked = 0;
    this.waitlist = [];
  }
  get remaining() { return this.capacity - this.booked; }
}

class Member {
  constructor(name) {
    this.name = name;
    this.membershipStatus = 'active';   // active / frozen / expired
    this.noShowCount = 0;
    this.packageRemaining = null;       // 私教课包余次
    this.penaltyDaysRemaining = 0;      // SYS-R4 限制剩余天数
    this.daysToExpire = 90;
    this.contract = null;
    this.visitsIn30d = 0;
    this.lastVisitDays = 0;
  }
}

class Gym {
  constructor() {
    this.members = new Map();
    this.courses = new Map();
    this.bookings = [];
    this.orders = [];
    this.notifications = [];
    this.alerts = [];
    this.audit = [];
    this.seq = 0;
  }

  // ---------- 基础查询 ----------
  member(name) {
    if (!this.members.has(name)) this.members.set(name, new Member(name));
    return this.members.get(name);
  }
  course(name) {
    if (!this.courses.has(name)) this.courses.set(name, new BookingClass(name));
    return this.courses.get(name);
  }
  lastBookingOf(memberName) {
    const list = this.bookings.filter(b => b.member === memberName);
    return list.length ? list[list.length - 1] : null;
  }
  log(action, detail) { this.audit.push({ action, detail, at: this.seq++ }); }

  // ---------- 约课（SYS-R1/R2/R3/R4 + 幂等） ----------
  submitBooking(memberName, courseName) {
    const m = this.member(memberName);
    const c = this.course(courseName);

    if (m.membershipStatus !== RULES['SYS-R1'].requireStatus) {
      const reason = m.membershipStatus === 'frozen' ? '会籍冻结中，暂不可约课' : '会籍已过期，请先续费';
      this.log('booking.reject', { memberName, courseName, rule: m.membershipStatus === 'frozen' ? 'SYS-R2' : 'SYS-R1' });
      return { ok: false, rule: m.membershipStatus === 'frozen' ? 'SYS-R2' : 'SYS-R1', reason };
    }
    if (m.penaltyDaysRemaining > 0) {
      this.log('booking.reject', { memberName, rule: 'SYS-R4' });
      return { ok: false, rule: 'SYS-R4', reason: `已被限制预约，剩余 ${m.penaltyDaysRemaining} 天` };
    }
    // 幂等：同一会员同一课程不重复生成
    const exist = this.bookings.find(b => b.member === memberName && b.course === courseName && b.status !== '取消');
    if (exist) {
      this.log('booking.idempotent', { memberName, courseName });
      return { ok: true, idempotent: true, booking: exist, rule: 'SYS-R3' };
    }
    // 时间冲突
    if (RULES['SYS-R3'].timeConflictCheck) {
      const conflict = this.bookings.find(b => b.member === memberName && b.status !== '取消' && b.status !== '爽约'
        && this.course(b.course).time === c.time);
      if (conflict) {
        this.log('booking.reject', { memberName, rule: 'SYS-R3', type: 'timeConflict' });
        return { ok: false, rule: 'SYS-R3', reason: '该时段已有预约，存在时间冲突' };
      }
    }
    // 容量
    if (RULES['SYS-R3'].capacityCheck && c.remaining <= 0) {
      this.log('booking.reject', { memberName, courseName, rule: 'SYS-R3', type: 'full' });
      return { ok: false, rule: 'SYS-R3', reason: '课程已满', waitlistAvailable: true };
    }

    c.booked += 1;
    const booking = { id: ++this.seq, member: memberName, course: courseName, status: '已约', checkinAt: null, operator: null };
    this.bookings.push(booking);
    this.log('booking.create', { memberName, courseName });
    return { ok: true, booking, rule: null };
  }

  // ---------- 签到（REQ-B4-002） ----------
  checkIn(memberName, channel = 'scan', operator = null) {
    const b = this.lastBookingOf(memberName);
    if (!b || b.status !== '已约') return { ok: false, reason: '无可签到的预约' };
    b.status = '已签到';
    b.checkinAt = 'now';
    b.operator = operator;
    this.log('booking.checkin', { memberName, channel, operator });
    return { ok: true, booking: b };
  }

  // ---------- 爽约判定（SYS-R4） ----------
  markNoShow(memberName) {
    const b = this.lastBookingOf(memberName);
    if (!b) return { ok: false, reason: '无预约' };
    b.status = '爽约';
    const m = this.member(memberName);
    m.noShowCount += 1;
    if (m.noShowCount >= RULES['SYS-R4'].N) {
      m.penaltyDaysRemaining = RULES['SYS-R4'].restrictDays;
      this.log('penalty.applied', { memberName, days: m.penaltyDaysRemaining, rule: 'SYS-R4' });
    }
    this.log('booking.noShow', { memberName, noShowCount: m.noShowCount });
    return { ok: true, booking: b, member: m };
  }

  // ---------- 取消（SYS-R3，释放名额） ----------
  cancel(memberName, courseName) {
    const b = this.bookings.find(x => x.member === memberName && x.course === courseName && x.status === '已约');
    if (!b) return { ok: false, reason: '无可取消的预约' };
    b.status = '取消';
    const c = this.course(courseName);
    c.booked = Math.max(0, c.booked - 1);
    this.log('booking.cancel', { memberName, courseName });
    return { ok: true, booking: b, course: c };
  }

  // ---------- 排课冲突（SYS-R3） ----------
  schedule(coach, time) {
    const conflict = [...this.courses.values()].some(c => c.coach === coach && c.time === time);
    if (conflict) {
      this.log('course.scheduleReject', { coach, time, rule: 'SYS-R3' });
      return { ok: false, rule: 'SYS-R3', reason: `教练 ${coach} 在 ${time} 已有课程` };
    }
    const c = new BookingClass(`新课 ${time}`);
    c.coach = coach; c.time = time;
    this.courses.set(c.name, c);
    return { ok: true, course: c };
  }

  // ---------- 支付（REQ-B5-001 / REQ-B5-004） ----------
  createOrder(memberName, bizType, amount) {
    const o = { no: 'O' + (++this.seq), member: memberName, bizType, amount, paidAmount: null, status: 'pending' };
    this.orders.push(o);
    return o;
  }
  payOrder(order) {
    order.status = 'paid';
    order.paidAmount = order.amount;
    const m = this.member(order.member);
    m.membershipStatus = 'active';
    m.contract = { type: order.bizType, status: 'active' };
    if (order.bizType === 'pt_package') m.packageRemaining = (m.packageRemaining || 0) + (order.times || 10);
    this.log('order.paid', { no: order.no });
    return { ok: true, order, member: m };
  }
  payCallbackFailed(order) {
    order.status = '异常';
    this.alerts.push({ type: 'PAY_CALLBACK_FAILED', order: order.no });
    this.log('order.abnormal', { no: order.no, reason: 'callbackFailed' });
    return { ok: true, order };
  }
  payAmountMismatch(order, paidAmount) {
    order.paidAmount = paidAmount;
    if (paidAmount !== order.amount) {
      order.status = '异常';
      this.alerts.push({ type: 'PAY_AMOUNT_MISMATCH', order: order.no });
      this.log('order.abnormal', { no: order.no, reason: 'amountMismatch' });
      return { ok: false, order };
    }
    return { ok: true, order };
  }

  // ---------- 课包核销（SYS-R5） ----------
  finishPtClass(memberName) {
    const m = this.member(memberName);
    if (m.packageRemaining == null) return { ok: false, reason: '无课包' };
    m.packageRemaining = Math.max(0, m.packageRemaining - RULES['SYS-R5'].deductPerClass);
    this.log('package.deduct', { memberName, remaining: m.packageRemaining });
    return { ok: true, member: m };
  }
  canSchedulePt(memberName) {
    const m = this.member(memberName);
    return !(m.packageRemaining != null && m.packageRemaining <= 0);
  }

  // ---------- 定时任务（SYS-R6 / R1 过期扫描） ----------
  dailyRenewRemind() {
    const sent = [];
    for (const m of this.members.values()) {
      if (m.membershipStatus === 'active' && m.daysToExpire <= RULES['SYS-R6'].D) {
        this.notifications.push({ member: m.name, template: 'renew_reminder' });
        sent.push(m.name);
      }
    }
    this.log('job.renewRemind', { sent });
    return sent;
  }
  dailyExpireScan() {
    const expired = [];
    for (const m of this.members.values()) {
      if (m.daysToExpire < 0 && m.membershipStatus === 'active') {
        m.membershipStatus = 'expired';
        expired.push(m.name);
      }
    }
    this.log('job.expireScan', { expired });
    return expired;
  }
}

module.exports = { Gym, RULES };
