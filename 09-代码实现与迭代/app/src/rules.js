'use strict';
/**
 * 规则参数（等价于数据库 rule_config 的默认值，见 07-数据库与部署设计/db/seed/R__seed_base_data.sql）
 * 改阈值只改这里（生产环境改为读数据库），不改业务代码。
 */
module.exports = {
  'SYS-R1': { requireStatus: 'active' },
  'SYS-R2': { frozenBlocksBooking: true },
  'SYS-R3': { capacityCheck: true, timeConflictCheck: true },
  'SYS-R4': { N: 3, restrictDays: 7 },
  'SYS-R5': { deductPerClass: 1 },
  'SYS-R6': { D: 7 },
  'SYS-R7': { noVisitWeeks: 4, noShowRate: 0.30, windowDays: 30 },
  'SYS-R8': { T: 0.60, action: 'remind_or_release' },
  'SYS-R9': { days: 30, minVisits: 2 },
  'SYS-R10': { calcType: 'by_times', rate: 0.20 },
};
