'use strict';
/**
 * S1 切片行为驱动验收执行器
 *
 * 作用：直接读取 05-测试用例与测试计划/features/*.feature（单一来源，不复制场景），
 * 在内存参考实现上执行 Given—When—Then，输出真实测试结果。
 *
 * 用法：node verify/run.js
 */
const fs = require('fs');
const path = require('path');
const { parse } = require('./src/gherkin');
const { findHandler } = require('./src/steps');
const { Gym } = require('./src/domain');

const FEATURE_DIR = path.resolve(__dirname, '..', '..', '05-测试用例与测试计划', 'features');
const TARGETS = ['booking.feature', 'attendance.feature', 'membership.feature']; // S1 切片
const S2S4 = ['warning.feature', 'commission.feature'];

const results = [];
let passed = 0, failed = 0;

function runScenario(featureName, scenario) {
  const ctx = { gym: new Gym() };
  for (const step of scenario.steps) {
    const h = findHandler(step.text);
    if (!h) {
      return { status: 'failed', reason: `未识别的步骤：${step.keyword} ${step.text}` };
    }
    try {
      h.fn(ctx, h.m);
    } catch (e) {
      return { status: 'failed', reason: `${step.keyword} ${step.text} → ${e.message}` };
    }
  }
  return { status: 'passed' };
}

console.log('========================================================');
console.log(' S1 切片 · 行为驱动验收测试（.feature 真实执行）');
console.log('========================================================');
console.log(`场景来源：${FEATURE_DIR}\n`);

for (const file of TARGETS) {
  const full = path.join(FEATURE_DIR, file);
  if (!fs.existsSync(full)) { console.log(`⚠️  未找到 ${file}`); continue; }
  const features = parse(fs.readFileSync(full, 'utf8'));
  console.log(`▸ ${file}`);
  for (const f of features) {
    for (const sc of f.scenarios) {
      const r = runScenario(f.name, sc);
      if (r.status === 'passed') { passed++; console.log(`   ✅ ${sc.name}`); }
      else { failed++; console.log(`   ❌ ${sc.name}\n        ${r.reason}`); }
      results.push({ file, feature: f.name, scenario: sc.name, ...r });
    }
  }
  console.log('');
}

console.log('--------------------------------------------------------');
console.log(` S1 结果：通过 ${passed} / 失败 ${failed} / 共 ${passed + failed}`);
console.log(` 待验证切片（S3/S4）：${S2S4.join(', ')} —— 随对应切片迭代再执行`);
console.log('--------------------------------------------------------');

// 输出报告
const summary = {
  slice: 'S1',
  executedAt: new Date().toISOString(),
  total: passed + failed,
  passed,
  failed,
  passRate: passed + failed ? (passed / (passed + failed) * 100).toFixed(1) + '%' : '0%',
  results,
  pendingSlices: S2S4,
};
fs.writeFileSync(path.join(__dirname, 'report.json'), JSON.stringify(summary, null, 2), 'utf8');

const md = [
  '# 行为驱动测试执行报告（S1 切片）',
  '',
  `- 执行时间：${summary.executedAt}`,
  `- 场景来源：\`05-测试用例与测试计划/features/\`（单一来源，未复制）`,
  `- 结果：**通过 ${passed} / 失败 ${failed} / 共 ${passed + failed}（通过率 ${summary.passRate}）**`,
  '',
  '| 场景 | 结果 |',
  '|---|---|',
  ...results.map(r => `| ${r.scenario} | ${r.status === 'passed' ? '✅ 通过' : '❌ 失败：' + r.reason} |`),
  '',
  `> 待验证切片（S3/S4）：${S2S4.join('、')} —— 随对应切片迭代再执行。`,
  '',
].join('\n');
fs.writeFileSync(path.join(__dirname, 'report.md'), md, 'utf8');

console.log('\n已生成：verify/report.json、verify/report.md');
process.exit(failed === 0 ? 0 : 1);
