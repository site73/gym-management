'use strict';
const fs = require('fs');
const path = require('path');

const DATA_FILE = path.join(__dirname, '..', 'data', 'db.json');
let db = null;

function seed() {
  const now = Date.now();
  return {
    seq: 100,
    members: [
      { id: 1, name: '张三', phone: '138****0001', status: 'active', noShowCount: 2, lastVisitDays: 3, packageRemaining: 1, daysToExpire: 30, visits30: 8, createdDaysAgo: 200 },
      { id: 2, name: '李四', phone: '139****0002', status: 'expired', noShowCount: 0, lastVisitDays: 40, packageRemaining: 0, daysToExpire: -5, visits30: 0, createdDaysAgo: 400 },
      { id: 3, name: '王五', phone: '137****0003', status: 'active', noShowCount: 0, lastVisitDays: 35, packageRemaining: 5, daysToExpire: 60, visits30: 1, createdDaysAgo: 25 },
      { id: 4, name: '赵六', phone: '136****0004', status: 'active', noShowCount: 1, lastVisitDays: 6, packageRemaining: 3, daysToExpire: 7, visits30: 6, createdDaysAgo: 20 },
      { id: 5, name: '钱七', phone: '135****0005', status: 'active', noShowCount: 0, lastVisitDays: 2, packageRemaining: 0, daysToExpire: 90, visits30: 10, createdDaysAgo: 22 },
    ],
    courses: [
      { id: 11, name: '动感单车', time: '19:00', coach: '陈教练', capacity: 20, booked: 18, status: 'published' },
      { id: 12, name: '瑜伽',     time: '18:00', coach: '林教练', capacity: 15, booked: 15, status: 'full' },
      { id: 13, name: '搏击操',   time: '20:00', coach: '陈教练', capacity: 12, booked: 5,  status: 'published' },
      { id: 14, name: '拳击基础', time: '19:00', coach: '吴教练', capacity: 10, booked: 0,  status: 'published' },
    ],
    bookings: [],   // {id, memberId, courseId, status, noShowProb, checkinChannel, operatorId, payTimes, coachId, at}
    orders: [],     // {no, memberId, bizType, amount, paidAmount, status, times}
    consumptions: [], // {id, bookingId, memberId, coachId, courseId, times, amount, at}
    tasks: [],      // 跟进/预警任务
    notifications: [],
    alerts: [],
    audit: [],
    meta: { createdAt: new Date(now).toISOString(), version: '0.1.0' },
  };
}

function load() {
  if (db) return db;
  try {
    if (fs.existsSync(DATA_FILE)) db = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
    else { db = seed(); save(); }
  } catch (e) {
    console.error('[store] 读取数据失败，已重置为种子数据：', e.message);
    db = seed(); save();
  }
  return db;
}

function save() {
  fs.mkdirSync(path.dirname(DATA_FILE), { recursive: true });
  fs.writeFileSync(DATA_FILE, JSON.stringify(db, null, 2), 'utf8');
}

function reset() { db = seed(); save(); return db; }

function nextId(prefix) { return (prefix || '') + (++load().seq); }

function audit(action, targetType, targetId, detail) {
  const d = load();
  d.audit.push({ at: new Date().toISOString(), action, targetType, targetId, detail: detail || null });
  save();
}

module.exports = { load, save, reset, seed, nextId, audit, DATA_FILE };
