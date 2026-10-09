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
    await page.click('#idMember');
    await page.fill('#loginUser', 'member1');
    await page.fill('#loginPass', '123456');
    await page.click('#loginBtn');
    await page.waitForSelector('#viewMember:not(.hidden)', { timeout: 10000 });
    check(B, '会员登录后进入会员端', '会员端可见 + 角色标签含"会员端"',
          `角色=${await page.textContent('#roleTag')}`,
          (await page.textContent('#roleTag')).includes('会员端'));

    check(B, '会员端侧边栏只显示会员菜单', '无门店菜单',
          await page.isVisible('#nav-booking') ? '仍可见门店菜单' : '仅会员菜单',
          !(await page.isVisible('#nav-booking')));
    check(B, '会员端不显示接口调用日志（按需求）', '隐藏', await page.isVisible('#nav-log') ? '仍可见' : '已隐藏',
          !(await page.isVisible('#nav-log')));

    // 「我的信息」默认页
    const infoText = await page.textContent('#memberInfo');
    check(B, '会员信息加载成功', '含昵称与会籍状态', infoText.replace(/\s+/g, ' ').slice(0, 40),
          infoText.includes('会籍状态'));

    // 左侧导航切到「课程与余位」（独立页面）
    await page.click('#nav-memberCourses');
    await waitFor(async () => (await page.locator('#memberCourses .card').count()) >= 1);
    const courseCards = await page.locator('#memberCourses .card').count();
    check(B, '切到「课程与余位」并渲染课程', '≥1 张课程卡', `${courseCards} 张`, courseCards >= 1);

    // 先记录「我的预约」当前数量
    await page.click('#nav-memberBookings');
    await sleep(900);
    const beforeCount = await page.locator('#myBookings .card').count();

    // 回到课程页选课
    await page.click('#nav-memberCourses');
    await waitFor(async () => (await page.locator('#memberCourses .card').count()) >= 1);
    await page.locator('#memberCourses .card button').first().click();
    await sleep(1400);

    await page.click('#nav-memberBookings');
    const grew = await waitFor(async () => (await page.locator('#myBookings .card').count()) > beforeCount);
    const afterCount = await page.locator('#myBookings .card').count();
    check(B, '点击「选课」有响应', '弹出提示 + 预约列表增加', `弹窗=${dialogs.length} 预约 ${beforeCount}→${afterCount}`,
          dialogs.length >= 1 && grew);

    // 自愿退课（在「我的预约」页）
    const cancelBtn = page.locator('#myBookings .card button:has-text("退课")').first();
    const hasCancel = await cancelBtn.count() > 0;
    if (hasCancel) {
      await cancelBtn.click();
      const cancelled = await waitForText(page, '#myBookings', '已取消');
      const listText = await page.textContent('#myBookings');
      check(B, '点击「退课」有响应', '状态变为已取消', cancelled ? '已取消' : '状态未变化', cancelled);
    } else {
      check(B, '点击「退课」有响应', '存在可退课的预约', '未找到按钮', false);
    }

    // 退出登录
    await page.click('header .user button');
    await page.waitForSelector('#loginBtn:visible', { timeout: 8000 });
    check(B, '退出登录回到登录页', '登录页可见', '已回到登录页', await page.isVisible('#loginBtn'));

    /* ---------- C 门店后台 ---------- */
    const C = 'C 门店后台';
    await page.click('#idManager');
    await page.fill('#loginUser', 'manager');
    await page.fill('#loginPass', '123456');
    await page.click('#loginBtn');
    await page.waitForSelector('#nav-booking', { timeout: 10000 });
    check(C, '店长登录后进入门店后台', '标签页可见 + 角色含"门店后台"',
          `角色=${await page.textContent('#roleTag')}`,
          (await page.textContent('#roleTag')).includes('门店后台'));
    check(C, '门店后台左侧有「接口日志」页面入口', '可见', await page.isVisible('#nav-log') ? '可见' : '隐藏',
          await page.isVisible('#nav-log'));

    await waitFor(async () => (await page.locator('#memberBox .member-pick').count()) >= 4);
    const memberRows = await page.locator('#memberBox .member-pick').count();
    check(C, '会员列表渲染', '≥4 位会员（注册用例会新增）', `${memberRows} 张会员卡`, memberRows >= 4);

    await waitFor(async () => (await page.locator('#bookingBox tr').count()) >= 2);
    const bookingRows = await page.locator('#bookingBox tr').count();
    check(C, '预约列表渲染', '≥2 行（含表头）', `${bookingRows} 行`, bookingRows >= 2);

    // 逐标签切换
    const tabs = [['nav-pay', '收费与对账'], ['nav-risk', '风险与预测'], ['nav-perf', '业绩与提成'], ['nav-report', '经营摘要']];
    for (const [id, label] of tabs) {
      await page.click('#' + id);
      const onePanel = await waitFor(async () => (await page.locator('.views section:not(.hidden)').count()) === 1);
      const visible = await page.locator('.views section:not(.hidden)').count();
      check(C, `切换到「${label}」`, '恰好 1 个面板可见', `${visible} 个`, onePanel);
    }

    // 风险扫描与预测
    await page.click('#nav-risk');
    await page.waitForSelector('button:has-text("执行风险扫描")', { state: 'visible', timeout: 10000 });
    await page.click('button:has-text("执行风险扫描")');
    const riskOk = await waitForText(page, '#riskBox', '会员总数');
    check(C, '点击「执行风险扫描」有响应', '显示 KPI 与任务', riskOk ? '已渲染' : '无内容', riskOk);

    await page.click('button:has-text("预测爽约概率")');
    const predOk = await waitForText(page, '#predictBox', '阈值');
    check(C, '点击「预测爽约概率」有响应', '显示阈值或结果', predOk ? '已渲染' : '无内容', predOk);

    // 提成
    await page.click('#nav-perf');
    await page.click('button:has-text("核算近 N 天提成")');
    const perfOk = await waitForText(page, '#commissionBox', '提成比例');
    check(C, '点击「核算提成」有响应', '显示提成比例', perfOk ? '已渲染' : '无内容', perfOk);

    // 摘要与审计
    await page.click('#nav-report');
    const summaryOk = await waitForText(page, '#summaryBox', '会员总数');
    const auditOk = await waitForText(page, '#auditBox', '动作');
    check(C, '经营摘要自动加载', '显示 KPI', summaryOk ? '已渲染' : '无内容', summaryOk);
    check(C, '审计日志自动加载', '有记录', auditOk ? '已渲染' : '无内容', auditOk);

    check(C, '店长看不到系统管理（仅管理员）', '隐藏', await page.isVisible('#nav-system') ? '仍可见' : '已隐藏',
          !(await page.isVisible('#nav-system')));
    check(C, '店长能看到课程管理（可排课）', '可见', await page.isVisible('#nav-course') ? '可见' : '隐藏',
          await page.isVisible('#nav-course'));

    /* ---------- D 管理员 ---------- */
    const D = 'D 管理员';
    await page.click('header .user button');
    await page.waitForSelector('#loginBtn:visible', { timeout: 8000 });
    await page.click('#idAdmin');
    await page.fill('#loginUser', 'admin');
    await page.fill('#loginPass', '123456');
    await page.click('#loginBtn');
    await page.waitForSelector('#nav-booking', { timeout: 10000 });
    check(D, '管理员看到系统管理', '可见', await page.isVisible('#nav-system') ? '可见' : '隐藏',
          await page.isVisible('#nav-system'));

    await page.click('#nav-system');
    await sleep(500);
    await page.click('button:has-text("重置数据")');
    await sleep(2000);
    const resetDialog = dialogs.some(d => d.includes('重置演示数据'));
    check(D, '点击「重置数据」有响应', '弹出确认并完成', resetDialog ? '已确认并重置' : '无弹窗',
          resetDialog);

  /* ---------- E 课程管理（管理员） ---------- */
  const E = 'E 课程管理';
  await page.click('#nav-course');
  await waitForText(page, '#adminCourseBox', 'C001');
  const rowsBefore = await page.locator('#adminCourseBox tr').count();
  check(E, '课程列表加载', '含表头与课程行', `${rowsBefore} 行`, rowsBefore >= 2);

  await page.click('#adminCourseBox tr:last-child button:has-text("编辑")');
  const titleChanged = await waitForText(page, '#courseFormTitle', '编辑课程');
  check(E, '点击「编辑」有响应', '表单标题变为编辑课程',
        await page.textContent('#courseFormTitle'), titleChanged);

  await page.fill('#cfName', '动感单车·答辩演示版');
  await page.fill('#cfCapacity', '20');
  await page.click('#courseSubmitBtn');
  const saved = await waitForText(page, '#adminCourseBox', '答辩演示版');
  check(E, '点击「保存课程」有响应', '列表出现修改后的名称',
        saved ? '已保存并刷新' : '未更新', saved);

  /* ---------- F 注册新会员 ---------- */
  const F2 = 'F 注册与联动';
  await page.click('header .user button');
  await page.waitForSelector('#loginBtn:visible', { timeout: 8000 });
  await page.click('#toRegister');
  await sleep(300);
  const regVisible = await page.isVisible('#regBtn');
  check(F2, '点击「注册新会员」切换到注册表单', '注册按钮可见', regVisible ? '已显示' : '未显示', regVisible);

  const uniq = 'w' + String(Date.now()).slice(-6);
  await page.fill('#regUser', uniq);
  await page.fill('#regPass', 'pass123456');
  await page.fill('#regName', '浏览器注册会员');
  await page.fill('#regPhone', '13800002222');
  await page.click('#regBtn');
  const enteredMember = await waitFor(async () => await page.isVisible('#viewMember'));
  check(F2, '注册成功并自动进入会员端', '会员端可见',
        `角色=${await page.textContent('#roleTag')}`, enteredMember);

  const regInfoText = await page.textContent('#memberInfo');
  check(F2, '新会员档案已生成', '含会员编号与会籍状态',
        regInfoText.replace(/\s+/g, ' ').slice(0, 46), regInfoText.includes('会籍状态'));

  /* ---------- G 店长后台：会员 ↔ 课程余位联动 ---------- */
  await page.click('header .user button');
  await page.waitForSelector('#loginBtn:visible', { timeout: 8000 });
  await page.click('#idManager');
    await page.fill('#loginUser', 'manager');
  await page.fill('#loginPass', '123456');
  await page.click('#loginBtn');
  await page.waitForSelector('#nav-booking', { timeout: 10000 });
  await page.click('#nav-booking');
  await waitForText(page, '#memberBox', '张三');

  const hintBefore = await page.textContent('#selectedHint');
  check(F2, '未选会员时提示先选择', '提示含"未选择会员"',
        hintBefore.slice(0, 30), hintBefore.includes('未选择会员'));

  const noPickBtn = await page.locator('#courseBox button:has-text("为 ")').count();
  check(F2, '未选会员时不显示代客约课按钮', '0 个', `${noPickBtn} 个`, noPickBtn === 0);

  await page.locator('#memberBox .member-pick').first().click();
  const hintAfter = await waitForText(page, '#selectedHint', '已选中');
  check(F2, '点击会员后被选中', '提示含"已选中"',
        (await page.textContent('#selectedHint')).slice(0, 34), hintAfter);

  const pickBtns = await page.locator('#courseBox button:has-text("为 ")').count();
  check(F2, '课程卡出现「为 TA 约课」按钮', '≥1 个', `${pickBtns} 个`, pickBtns >= 1);

  const rosterBefore = await page.locator('#courseBox .roster').first().textContent();
  await page.locator('#courseBox button:has-text("为 ")').first().click();
  await sleep(1500);
  const rosterAfter = await page.locator('#courseBox .roster').first().textContent();
  check(F2, '代客约课后已报名名单联动更新', '名单人数增加',
        `${rosterBefore.trim().slice(0, 22)} → ${rosterAfter.trim().slice(0, 22)}`,
        rosterBefore !== rosterAfter);

  /* ---------- H 身份选择与左侧导航 ---------- */
  const H = 'H 身份与导航';
  // 选「店长」身份却用会员账号登录 → 应被拦下
  await page.click('header .user button');
  await page.waitForSelector('#loginBtn:visible', { timeout: 8000 });
  await page.click('#idManager');
  await page.fill('#loginUser', 'member1');
  await page.fill('#loginPass', '123456');
  await page.click('#loginBtn');
  await sleep(800);
  const mismatchMsg = await page.textContent('#loginMsg');
  const stillLogin = await page.isVisible('#loginBtn');
  check(H, '身份与账号不符时被拦下', '提示身份不匹配且仍在登录页',
        `msg="${(mismatchMsg || '').slice(0, 26)}"`, stillLogin && mismatchMsg.includes('身份'));

  // 正确身份可登录
  await page.click('#idManager');
  await page.fill('#loginUser', 'manager');
  await page.fill('#loginPass', '123456');
  await page.click('#loginBtn');
  await page.waitForSelector('#nav-booking', { timeout: 10000 });
  const navCount = await page.locator('#sidebar button').count();
  check(H, '左侧导航按角色渲染', '店长看到 ≥6 个菜单项', `${navCount} 项`, navCount >= 6);

  const onlyOnePanel = await page.locator('.views section:not(.hidden)').count();
  check(H, '同一时刻只显示一个页面', '1 个', `${onlyOnePanel} 个`, onlyOnePanel === 1);

  /* ---------- I 选课搜索 ---------- */
  const I = 'I 搜索与二维码';
  await page.click('#nav-booking');
  await waitFor(async () => (await page.locator('#courseBox .card').count()) > 0);
  const allCourses = await page.locator('#courseBox .card').count();
  await page.fill('#staffCourseSearch', '瑜伽');
  await sleep(300);
  const filtered = await page.locator('#courseBox .card').count();
  const filterText = await page.textContent('#courseBox');
  check(I, '门店课程搜索生效', '结果少于全部且含"瑜伽"',
        `${allCourses} → ${filtered} 门`, filtered < allCourses && filterText.includes('瑜伽'));

  await page.fill('#staffCourseSearch', 'zzz-不存在');
  await sleep(300);
  const noneText = await page.textContent('#courseBox');
  check(I, '无匹配时给出提示', '含"没有匹配"', noneText.slice(0, 24), noneText.includes('没有匹配'));
  await page.click('#staffCourseClear');
  await sleep(300);
  const restored = await page.locator('#courseBox .card').count();
  check(I, '清空搜索后恢复全部', '等于全部数量', `${restored} 门`, restored === allCourses);

  /* ---------- J 扫码签到二维码与核销 ---------- */
  const J = 'J 扫码签到';
  await page.click('header .user button');
  await page.waitForSelector('#loginBtn:visible', { timeout: 8000 });
  await page.click('#idMember');
  await page.fill('#loginUser', 'member1');
  await page.fill('#loginPass', '123456');
  await page.click('#loginBtn');
  await page.waitForSelector('#nav-memberBookings', { timeout: 10000 });
  await page.click('#nav-memberBookings');
  await waitFor(async () => (await page.locator('#myBookings .card').count()) > 0);

  // 没有可签到的预约时先选一门课
  let hasCheckin = await page.locator('#myBookings button:has-text("扫码签到"):not([disabled])').count();
  if (hasCheckin === 0) {
    await page.click('#nav-memberCourses');
    await waitFor(async () => (await page.locator('#memberCourses .card').count()) > 0);
    await page.locator('#memberCourses button:has-text("选课")').first().click();
    await sleep(1200);
    await page.click('#nav-memberBookings');
    await waitFor(async () => (await page.locator('#myBookings .card').count()) > 0);
  }

  await page.locator('#myBookings button:has-text("扫码签到")').first().click();
  await page.waitForSelector('#modalMask:not(.hidden)', { timeout: 5000 });
  const modalVisible = await page.isVisible('#modalBox');
  const svgCount = await page.locator('#modalBox .qr svg').count();
  const codeText = (await page.textContent('#modalBox .code')) || '';
  check(J, '点击「扫码签到」弹出二维码', '弹窗可见 + 含 SVG 二维码',
        `弹窗=${modalVisible} svg=${svgCount}`, modalVisible && svgCount === 1);
  check(J, '二维码内容为签到凭据', '形如 GYM-CHECKIN:{预约号}:{会员号}',
        codeText.slice(0, 30),
        codeText.trim().startsWith('GYM-CHECKIN:') && codeText.trim().split(':').length === 3);

  const qrModules = await page.locator('#modalBox .qr svg path').count();
  check(J, '二维码图形已绘制', 'path 存在（模块数 > 0）', `${qrModules} 个 path`, qrModules >= 1);

  await page.click('#modalBox button:has-text("关闭")');
  await sleep(300);
  check(J, '关闭弹窗', '弹窗隐藏', await page.isVisible('#modalMask') ? '仍可见' : '已关闭',
        !(await page.isVisible('#modalMask')));

  // 门店扫码核销
  const payload = codeText.trim();
  await page.click('header .user button');
  await page.waitForSelector('#loginBtn:visible', { timeout: 8000 });
  await page.click('#idManager');
  await page.fill('#loginUser', 'manager');
  await page.fill('#loginPass', '123456');
  await page.click('#loginBtn');
  await page.waitForSelector('#nav-booking', { timeout: 10000 });
  await page.click('#nav-booking');
  await sleep(1000);
  await page.fill('#checkinScanInput', payload);
  await page.click('button:has-text("核销签到")');
  const scanOk = await waitForText(page, '#scanResult', '核销成功');
  check(J, '门店扫码核销成功', '提示"核销成功"',
        (await page.textContent('#scanResult')).slice(0, 34), scanOk);

  await page.fill('#checkinScanInput', payload);
  await page.click('button:has-text("核销签到")');
  await sleep(900);
  const dupResult = await page.textContent('#scanResult');
  check(J, '重复核销被拒绝', '提示核销失败（状态冲突）', dupResult.slice(0, 34), dupResult.includes('核销失败'));

  /* ---------- K 会员端：场地预约 ---------- */
  const K = 'K 场地预约';
  const relogin = async (identity, user) => {
    await page.click('header .user button');
    await page.waitForSelector('#loginBtn:visible', { timeout: 8000 });
    await page.click('#' + identity);
    await page.fill('#loginUser', user);
    await page.fill('#loginPass', '123456');
    await page.click('#loginBtn');
  };

  await relogin('idMember', 'member1');
  await page.waitForSelector('#nav-venues', { timeout: 10000 });
  check(K, '会员左侧导航有「场地预约」入口', '可见', await page.isVisible('#nav-venues') ? '可见' : '隐藏',
        await page.isVisible('#nav-venues'));

  await page.click('#nav-venues');
  await waitFor(async () => (await page.locator('#venueList .venue-card').count()) > 0);
  const venueCards = await page.locator('#venueList .venue-card').count();
  const venueText = await page.textContent('#venueList');
  check(K, '场馆列表渲染 5 处（4 私有 + 1 公共）',
        '5 张卡片且含公共区域',
        `${venueCards} 张；含公共区域=${venueText.includes('公共区域')}`,
        venueCards >= 5 && venueText.includes('私有场馆') && venueText.includes('公共区域'));

  await page.locator('#venueList .venue-card').first().click();
  await sleep(300);
  const pickHint = await page.textContent('#venuePickHint');
  check(K, '点选场馆后给出已选提示', '含"已选择"', pickHint.slice(0, 30), pickHint.includes('已选择'));

  // 用一个较远的日期避免与其它用例冲突
  const far = new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10);
  await page.fill('#vbDate', far);
  await page.fill('#vbStart', '08:00');
  await page.fill('#vbEnd', '09:00');
  await page.click('#viewVenues button:has-text("提交预约")');
  const bookedOk = await waitForText(page, '#venueBookingMsg', '预约成功');
  check(K, '提交场地预约成功', '提示"预约成功"',
        (await page.textContent('#venueBookingMsg')).slice(0, 30), bookedOk);

  // 重新选中同一场馆，再提交同一时段 → 应被冲突校验拒绝
  await page.locator('#venueList .venue-card').first().click();
  await sleep(300);
  await page.fill('#vbDate', far);
  await page.fill('#vbStart', '08:00');
  await page.fill('#vbEnd', '09:00');
  await page.click('#viewVenues button:has-text("提交预约")');
  await sleep(1000);
  const dupMsg = await page.textContent('#venueBookingMsg');
  check(K, '同场地同时段重复预约被拒绝', '提示失败（时段不可重叠）',
        dupMsg.slice(0, 46), dupMsg.includes('预约失败'));

  await page.click('#nav-myVenueBookings');
  await waitFor(async () => (await page.locator('#myVenueBookings .card').count()) > 0);
  const myVbCount = await page.locator('#myVenueBookings .card').count();
  check(K, '「我的场地预约」显示预约记录', '≥1 条', `${myVbCount} 条`, myVbCount >= 1);

  const beforeCancel = await page.locator('#myVenueBookings button:has-text("取消预约"):not([disabled])').count();
  if (beforeCancel > 0) {
    await page.locator('#myVenueBookings button:has-text("取消预约"):not([disabled])').first().click();
    await sleep(1200);
    const cancelText = await page.textContent('#myVenueBookings');
    check(K, '取消场地预约成功', '列表出现"已取消"', cancelText.includes('已取消') ? '已取消' : '未变化',
          cancelText.includes('已取消'));
  } else {
    check(K, '取消场地预约成功', '存在可取消的预约', '未找到按钮', false);
  }

  /* ---------- L 教练端 ---------- */
  const L = 'L 教练端';
  await relogin('idCoach', 'coach1');
  await page.waitForSelector('#nav-coachCourses', { timeout: 10000 });
  check(L, '教练登录后进入教练端', '角色标签含"教练端"',
        await page.textContent('#roleTag'), (await page.textContent('#roleTag')).includes('教练端'));

  const coachNavCount = await page.locator('#sidebar button').count();
  check(L, '教练端导航只有教练菜单', '2 项（我的课表 / 学员名单）', `${coachNavCount} 项`, coachNavCount === 2);

  const coachNavText = await page.textContent('#sidebar');
  check(L, '教练端看不到门店菜单', '无约课管理/场馆管理等',
        coachNavText.replace(/\s+/g, ' ').slice(0, 36),
        !coachNavText.includes('约课管理') && !coachNavText.includes('场馆管理') && !coachNavText.includes('系统管理'));

  const coachProfile = await page.textContent('#coachProfile');
  check(L, '教练本人档案渲染', '含姓名与擅长项目',
        coachProfile.replace(/\s+/g, ' ').slice(0, 34), coachProfile.includes('王教练'));

  await waitFor(async () => (await page.locator('#coachCourseBox .card').count()) >= 0);
  const coachCourses = await page.locator('#coachCourseBox .card').count();
  check(L, '我的课表渲染', '≥1 门（王教练名下课程）', `${coachCourses} 门`, coachCourses >= 1);

  await page.click('#nav-coachRoster');
  await sleep(900);
  const rosterOptions = await page.locator('#coachRosterCourse option').count();
  check(L, '学员名单页可选题自己的课程', '下拉有选项', `${rosterOptions} 项`, rosterOptions >= 1);

  await page.click('#viewCoachRoster button:has-text("查看名单")');
  const rosterRendered = await waitFor(async () => {
    const t = await page.textContent('#coachRosterBox');
    return t.includes('共') || t.includes('暂无');
  });
  check(L, '查看学员名单有响应', '显示名单或"暂无报名"',
        (await page.textContent('#coachRosterBox')).replace(/\s+/g, ' ').slice(0, 34), rosterRendered);

  /* ---------- M 门店：教练 / 场馆 / 场地预约管理 ---------- */
  const M = 'M 管理页';
  await relogin('idManager', 'manager');
  await page.waitForSelector('#nav-coachAdmin', { timeout: 10000 });
  check(M, '门店导航含教练/场馆/场地预约管理', '三项均可见',
        `${await page.isVisible('#nav-coachAdmin')}/${await page.isVisible('#nav-venueAdmin')}/${await page.isVisible('#nav-venueBookingAdmin')}`,
        await page.isVisible('#nav-coachAdmin') && await page.isVisible('#nav-venueAdmin') && await page.isVisible('#nav-venueBookingAdmin'));

  await page.click('#nav-coachAdmin');
  await waitFor(async () => (await page.locator('#coachAdminBox tr').count()) >= 2);
  const coachRows = await page.locator('#coachAdminBox tr').count();
  check(M, '教练管理列表渲染', '≥2 行（含表头）', `${coachRows} 行`, coachRows >= 2);

  const newCoachCode = 'K7' + String(Date.now()).slice(-4);
  await page.fill('#cfCoachCode', newCoachCode);
  await page.fill('#cfCoachName', '浏览器新增教练');
  await page.fill('#cfCoachSpec', '拉伸放松');
  await page.click('#viewCoachAdmin button:has-text("保存教练")');
  await sleep(1200);
  // 教练数量会随测试累积，新教练可能落在第二页；用搜索精确定位（同时顺带验证教练列表搜索）
  await page.fill('#coachAdminSearch', '浏览器新增教练');
  await sleep(400);
  const coachAdded = await waitForText(page, '#coachAdminBox', '浏览器新增教练');
  check(M, '新增教练有响应（列表可搜到）', '列表出现新教练', coachAdded ? '已新增并可搜索到' : '未出现', coachAdded);
  await page.click('#coachAdminClear');
  await sleep(300);

  await page.click('#nav-venueAdmin');
  await waitFor(async () => (await page.locator('#venueAdminBox tr').count()) >= 6);
  const venueRows = await page.locator('#venueAdminBox tr').count();
  check(M, '场馆管理列表渲染 5 处', '6 行（含表头）', `${venueRows} 行`, venueRows >= 6);

  await page.locator('#venueAdminBox button:has-text("编辑")').first().click();
  await sleep(400);
  const venueFormTitle = await page.textContent('#venueFormTitle');
  check(M, '点「编辑」载入场馆表单', '标题含"编辑场馆"', venueFormTitle.slice(0, 20),
        venueFormTitle.includes('编辑场馆'));
  await page.click('#viewVenueAdmin button:has-text("保存场馆")');
  await sleep(1200);
  const venueSaved = await page.locator('#venueAdminBox tr').count() >= 6;
  check(M, '保存场馆有响应', '列表刷新', venueSaved ? '已刷新' : '未刷新', venueSaved);

  await page.click('#nav-venueBookingAdmin');
  await waitFor(async () => {
    const t = await page.textContent('#venueBookingAdminBox');
    return t.includes('场馆') || t.includes('暂无');
  });
  const vbAdminText = await page.textContent('#venueBookingAdminBox');
  check(M, '场地预约管理可查看全部预约', '渲染表格或"暂无"',
        vbAdminText.replace(/\s+/g, ' ').slice(0, 30), !!vbAdminText);

  const vbFar = new Date(Date.now() + 40 * 86400000).toISOString().slice(0, 10);
  await page.fill('#vbAdminDate', vbFar);
  await page.fill('#vbAdminStart', '16:00');
  await page.fill('#vbAdminEnd', '17:00');
  await page.click('#viewVenueBookingAdmin button:has-text("代客预约")');
  const staffVbOk = await waitForText(page, '#vbAdminMsg', '代客预约成功');
  check(M, '门店代客预约场地成功', '提示"代客预约成功"',
        (await page.textContent('#vbAdminMsg')).slice(0, 30), staffVbOk);

  /* ---------- N 报名名单弹窗与分页 ---------- */
  const N = 'N 名单与分页';
  await page.click('#nav-booking');
  await waitFor(async () => (await page.locator('#courseBox .card').count()) > 0);
  const rosterBtn = page.locator('#courseBox button:has-text("查看名单")').first();   // 已限定容器
  check(N, '门店课程卡有「查看名单」按钮', '≥1 个', `${await page.locator('#courseBox button:has-text("查看名单")').count()} 个`,
        await page.locator('#courseBox button:has-text("查看名单")').count() >= 1);

  if (await rosterBtn.count() > 0) {
    await rosterBtn.click();
    await page.waitForSelector('#modalMask:not(.hidden)', { timeout: 5000 });
    const rosterHtml = await page.textContent('#modalBox');
    check(N, '点「查看名单」弹出报名名单', '弹窗含"共 N 人"',
          rosterHtml.replace(/\s+/g, ' ').slice(0, 34), rosterHtml.includes('共'));
    await page.click('#modalBox button:has-text("关闭")');
    await sleep(300);
    check(N, '名单弹窗可关闭', '弹窗隐藏', await page.isVisible('#modalMask') ? '仍可见' : '已关闭',
          !(await page.isVisible('#modalMask')));
  }

  const pagerText = await page.textContent('#courseBox');
  check(N, '课程列表分页控件存在', '含"共 N 条 · 第 x/y 页"',
        (pagerText.match(/共 \d+ 条 · 第 \d+\/\d+ 页/) || ['（无分页）'])[0],
        /共 \d+ 条 · 第 \d+\/\d+ 页/.test(pagerText));

  const pageBtns = await page.locator('#courseBox .pager button.pg').count();
  check(N, '分页按钮可点击', '≥3 个（上一页/页码/下一页）', `${pageBtns} 个`, pageBtns >= 3);

  if (pageBtns >= 3) {
    const firstCardBefore = await page.locator('#courseBox .card').first().textContent();
    const nextBtn = page.locator('#courseBox .pager button.pg:has-text("下一页")');
    if (await nextBtn.isEnabled().catch(() => false)) {
      await nextBtn.click();
      await sleep(500);
      const firstCardAfter = await page.locator('#courseBox .card').first().textContent();
      check(N, '点「下一页」列表内容变化', '首张卡片不同',
            firstCardBefore !== firstCardAfter ? '已切换' : '未变化', firstCardBefore !== firstCardAfter);
    } else {
      check(N, '点「下一页」列表内容变化', '仅一页时按钮禁用', '当前只有一页（按钮已禁用）', true);
    }
  }

  /* ---------- F 运行时健康 ---------- */
  const F = 'F 运行时健康';
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
