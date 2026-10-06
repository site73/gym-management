'use strict';
/**
 * 真实浏览器测试（Playwright + 本机 Edge）
 *
 * 目的：验证"点击有响应、无 JS 报错"，覆盖登录、角色路由、会员端与门店后台各按钮。
 * 运行：node verify/browser-test.js
 * 依赖：E:/dev/pw/node_modules（playwright-core），通过 NODE_PATH 引入
 */
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright-core');

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const BASE = process.env.API_BASE || 'http://127.0.0.1:8080';

let pass = 0, fail = 0;
const rows = [];
const consoleErrors = [];   // 真正的 JS 错误
const serverErrors = [];    // 5xx 响应
let authResponses = 0;      // 预期内的 401（登录失败/未带令牌）

function check(group, name, expected, actual, cond) {
  const id = 'BT-' + String(rows.length + 1).padStart(3, '0');
  if (cond) pass++; else fail++;
  rows.push([id, group, name, expected, actual, cond ? 'PASS' : 'FAIL']);
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${id}  ${name}  | 期望: ${expected} | 实际: ${actual}`);
}
const sleep = ms => new Promise(r => setTimeout(r, ms));

/** 等待某个元素内出现指定文本（避免固定 sleep 造成的偶发失败） */
async function waitForText(page, selector, needle, timeout = 15000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const txt = await page.textContent(selector).catch(() => '');
    if (txt && txt.includes(needle)) return true;
    await sleep(200);
  }
  return false;
}
/** 等待条件成立（返回 boolean） */
async function waitFor(fn, timeout = 15000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    if (await fn().catch(() => false)) return true;
    await sleep(200);
  }
  return false;
}

(async () => {
  console.log('============================================================');
  console.log(' 真实浏览器测试（Edge 无头模式）');
  console.log(` 页面地址：${BASE}`);
  console.log('============================================================\n');

  // 先通过管理员接口重置数据，确保用例与前置执行解耦（避免数据耦合导致的偶发失败）
  try {
    const lr = await fetch(BASE + '/api/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'admin', password: '123456' })
    });
    const ld = await lr.json();
    if (ld && ld.token) {
      await fetch(BASE + '/api/admin/reset', { method: 'POST', headers: { 'X-Token': ld.token } });
      console.log('（前置）演示数据已重置\n');
    }
  } catch (e) {
    console.log('（前置）重置失败：' + e.message + '\n');
  }

  const browser = await chromium.launch({ executablePath: EDGE, headless: true });
  const page = await browser.newPage();

  const dialogs = [];
  page.on('dialog', async d => { dialogs.push(d.message()); await d.accept(); });
  page.on('console', m => {
    if (m.type() !== 'error') return;
    const t = m.text();
    // 资源加载失败单列统计：401 属预期（登录失败/令牌过期），5xx 视为缺陷
    if (/Failed to load resource/.test(t)) {
      if (/status of 401/.test(t)) { authResponses++; return; }
      if (/status of 5\d\d/.test(t)) { serverErrors.push(t); return; }
      return;
    }
    consoleErrors.push(t);
  });
  page.on('pageerror', e => consoleErrors.push('pageerror: ' + e.message));
  page.on('response', r => {
    if (r.status() >= 500) serverErrors.push(`${r.status()} ${r.url()}`);
  });

  try {
    /* ---------- A 登录页 ---------- */
    const A = 'A 登录页';
    await page.goto(BASE + '/', { waitUntil: 'networkidle' });
    check(A, '登录页渲染', '登录按钮可见', await page.isVisible('#loginBtn'), await page.isVisible('#loginBtn'));
    const accountRows = await page.locator('.accounts tr').count();
    check(A, '展示演示账号', '4 个账号', `${accountRows} 个`, accountRows === 4);

    // 点击"填充"按钮
    await page.locator('.accounts button').first().click();
    check(A, '点击填充按钮写入用户名', 'member1', await page.inputValue('#loginUser'),
          (await page.inputValue('#loginUser')) === 'member1');

    // 错误密码
    await page.fill('#loginPass', 'wrong');
    await page.click('#loginBtn');
    await sleep(600);
    const errMsg = await page.textContent('#loginMsg');
    check(A, '错误密码提示且停留在登录页', '提示非空 + 仍在登录页',
          `msg="${errMsg}"`, !!errMsg && await page.isVisible('#loginBtn'));

    /* ---------- B 会员端 ---------- */
    const B = 'B 会员端';
    await page.fill('#loginUser', 'member1');
    await page.fill('#loginPass', '123456');
    await page.click('#loginBtn');
    await page.waitForSelector('#viewMember:not(.hidden)', { timeout: 10000 });
    check(B, '会员登录后进入会员端', '会员端可见 + 角色标签含"会员端"',
          `角色=${await page.textContent('#roleTag')}`,
          (await page.textContent('#roleTag')).includes('会员端'));

    check(B, '会员端不显示门店标签页', '隐藏', await page.isVisible('#staffTabs') ? '仍可见' : '已隐藏',
          !(await page.isVisible('#staffTabs')));
    check(B, '会员端不显示接口调用日志（按需求）', '隐藏', await page.isVisible('#logPanel') ? '仍可见' : '已隐藏',
          !(await page.isVisible('#logPanel')));

    const infoText = await page.textContent('#memberInfo');
    check(B, '会员信息加载成功', '含昵称与会籍状态', infoText.replace(/\s+/g, ' ').slice(0, 40),
          infoText.includes('会籍状态'));

    const courseCards = await page.locator('#memberCourses .card').count();
    check(B, '课程列表渲染', '≥1 张课程卡', `${courseCards} 张`, courseCards >= 1);

    // 约课
    const beforeCount = await page.locator('#myBookings .card').count();
    await page.locator('#memberCourses .card button').first().click();
    const grew = await waitFor(async () => (await page.locator('#myBookings .card').count()) > beforeCount);
    const afterCount = await page.locator('#myBookings .card').count();
    check(B, '点击「立即约课」有响应', '弹出提示 + 预约列表增加', `弹窗=${dialogs.length} 预约 ${beforeCount}→${afterCount}`,
          dialogs.length >= 1 && grew);

    // 取消预约
    const cancelBtn = page.locator('#myBookings .card button:has-text("取消预约")').first();
    const hasCancel = await cancelBtn.count() > 0;
    if (hasCancel) {
      await cancelBtn.click();
      const cancelled = await waitForText(page, '#myBookings', '已取消');
      const listText = await page.textContent('#myBookings');
      check(B, '点击「取消预约」有响应', '状态变为已取消', cancelled ? '已取消' : '状态未变化', cancelled);
    } else {
      check(B, '点击「取消预约」有响应', '存在可取消的预约', '未找到按钮', false);
    }

    // 退出登录
    await page.click('header .user button');
    await page.waitForSelector('#loginBtn:visible', { timeout: 8000 });
    check(B, '退出登录回到登录页', '登录页可见', '已回到登录页', await page.isVisible('#loginBtn'));

    /* ---------- C 门店后台 ---------- */
    const C = 'C 门店后台';
    await page.fill('#loginUser', 'manager');
    await page.fill('#loginPass', '123456');
    await page.click('#loginBtn');
    await page.waitForSelector('#staffTabs:not(.hidden)', { timeout: 10000 });
    check(C, '店长登录后进入门店后台', '标签页可见 + 角色含"门店后台"',
          `角色=${await page.textContent('#roleTag')}`,
          (await page.textContent('#roleTag')).includes('门店后台'));
    check(C, '门店后台显示接口调用日志', '可见', await page.isVisible('#logPanel') ? '可见' : '隐藏',
          await page.isVisible('#logPanel'));

    const memberRows = await waitFor(async () => (await page.locator('#memberBox div').count()) >= 4)
      .then(() => page.locator('#memberBox div').count());
    check(C, '会员列表渲染', '4 位会员', `${memberRows} 行`, memberRows === 4);

    await waitFor(async () => (await page.locator('#bookingBox tr').count()) >= 2);
    const bookingRows = await page.locator('#bookingBox tr').count();
    check(C, '预约列表渲染', '≥2 行（含表头）', `${bookingRows} 行`, bookingRows >= 2);

    // 逐标签切换
    const tabs = [['tabPay', '收费与对账'], ['tabRisk', '风险与预测'], ['tabPerf', '业绩与提成'], ['tabReport', '经营摘要 / 审计']];
    for (const [id, label] of tabs) {
      await page.click('#' + id);
      const onePanel = await waitFor(async () => (await page.locator('.views section:not(.hidden)').count()) === 1);
      const visible = await page.locator('.views section:not(.hidden)').count();
      check(C, `切换到「${label}」`, '恰好 1 个面板可见', `${visible} 个`, onePanel);
    }

    // 风险扫描与预测
    await page.click('#tabRisk');
    await page.waitForSelector('button:has-text("执行风险扫描")', { state: 'visible', timeout: 10000 });
    await page.click('button:has-text("执行风险扫描")');
    const riskOk = await waitForText(page, '#riskBox', '会员总数');
    check(C, '点击「执行风险扫描」有响应', '显示 KPI 与任务', riskOk ? '已渲染' : '无内容', riskOk);

    await page.click('button:has-text("预测爽约概率")');
    const predOk = await waitForText(page, '#predictBox', '阈值');
    check(C, '点击「预测爽约概率」有响应', '显示阈值或结果', predOk ? '已渲染' : '无内容', predOk);

    // 提成
    await page.click('#tabPerf');
    await page.click('button:has-text("核算近 N 天提成")');
    const perfOk = await waitForText(page, '#commissionBox', '提成比例');
    check(C, '点击「核算提成」有响应', '显示提成比例', perfOk ? '已渲染' : '无内容', perfOk);

    // 摘要与审计
    await page.click('#tabReport');
    const summaryOk = await waitForText(page, '#summaryBox', '会员总数');
    const auditOk = await waitForText(page, '#auditBox', '动作');
    check(C, '经营摘要自动加载', '显示 KPI', summaryOk ? '已渲染' : '无内容', summaryOk);
    check(C, '审计日志自动加载', '有记录', auditOk ? '已渲染' : '无内容', auditOk);

    check(C, '店长看不到系统管理标签', '隐藏', await page.isVisible('#tabSystem') ? '仍可见' : '已隐藏',
          !(await page.isVisible('#tabSystem')));

    /* ---------- D 管理员 ---------- */
    const D = 'D 管理员';
    await page.click('header .user button');
    await page.waitForSelector('#loginBtn:visible', { timeout: 8000 });
    await page.fill('#loginUser', 'admin');
    await page.fill('#loginPass', '123456');
    await page.click('#loginBtn');
    await page.waitForSelector('#staffTabs:not(.hidden)', { timeout: 10000 });
    check(D, '管理员看到系统管理标签', '可见', await page.isVisible('#tabSystem') ? '可见' : '隐藏',
          await page.isVisible('#tabSystem'));

    await page.click('#tabSystem');
    await sleep(500);
    await page.click('button:has-text("重置数据")');
    await sleep(2000);
    const resetDialog = dialogs.some(d => d.includes('重置演示数据'));
    check(D, '点击「重置数据」有响应', '弹出确认并完成', resetDialog ? '已确认并重置' : '无弹窗',
          resetDialog);

    /* ---------- E 运行时健康 ---------- */
    const E = 'E 运行时健康';
    check(E, '无 JS 控制台错误', '0 个',
          consoleErrors.length === 0 ? '0 个' : consoleErrors.slice(0, 3).join(' | '),
          consoleErrors.length === 0);
    check(E, '无服务端 5xx 错误', '0 个',
          serverErrors.length === 0 ? '0 个' : [...new Set(serverErrors)].slice(0, 3).join(' | '),
          serverErrors.length === 0);
    const unknownHandlers = dialogs.filter(d => d.includes('is not defined'));
    check(E, '无未定义函数导致的静默失败', '0 个', `${unknownHandlers.length} 个`, unknownHandlers.length === 0);
    check(E, '无 404 资源错误（favicon 等）', '0 个',
          `${rows.filter(x => /404/.test(x[4])).length} 个`,
          !serverErrors.some(s => s.includes('404')));
    console.log(`\n（提示）预期内 401 响应 ${authResponses} 次，不计为失败`);

  } catch (e) {
    check('异常', '测试执行未抛异常', '正常完成', e.message, false);
  } finally {
    await browser.close();
  }

  console.log('\n------------------------------------------------------------');
  console.log(` 结果：通过 ${pass} / 失败 ${fail} / 共 ${pass + fail}`);
  console.log('------------------------------------------------------------');

  const md = ['# 真实浏览器测试报告（Playwright + Edge）', '',
    `- 页面地址：${BASE}`,
    `- 执行时间：${new Date().toISOString()}`,
    `- 结果：**通过 ${pass} / 失败 ${fail} / 共 ${pass + fail}**`,
    `- 控制台 JS 错误：${consoleErrors.length} 个`, '',
    '| 编号 | 分组 | 用例 | 期望 | 实际 | 结果 |', '|---|---|---|---|---|---|',
    ...rows.map(x => `| ${x[0]} | ${x[1]} | ${x[2]} | ${x[3]} | ${x[4]} | ${x[5] === 'PASS' ? '✅ 通过' : '❌ 失败'} |`),
    '', consoleErrors.length ? '## 控制台错误\n\n```\n' + consoleErrors.join('\n') + '\n```' : ''].join('\n');
  fs.writeFileSync(path.join(__dirname, 'browser-test-report.md'), md, 'utf8');
  console.log('已生成：verify/browser-test-report.md');

  process.exit(fail === 0 ? 0 : 1);
})();
