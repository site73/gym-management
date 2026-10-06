'use strict';
/**
 * 一键启动器：MySQL → 后端 → 打开浏览器
 *
 * 用法：双击 E:\dev\start-system.cmd（或 node E:\dev\start-system.js）
 *
 * 说明：
 *  · 本文件为 UTF-8（Node 原生支持中文路径）；外层 .cmd 仅含 ASCII，避免编码问题。
 *  · MySQL 以「后台常驻进程」方式拉起（不占用额外窗口），日志写入 E:\dev\mysql\logs\。
 *  · MySQL 起不来时自动回退到免数据库的 local 模式（H2 内存库）。
 */
const { spawn, spawnSync } = require('child_process');
const net = require('net');
const fs = require('fs');
const path = require('path');

const DEV = 'E:\\dev';
const PROJECT = 'E:\\WorkBuddy\\健身房管理业务';
const BACKEND_DIR = path.join(PROJECT, '08-系统结构与代码设计', 'backend');
const JAR = 'target\\gym-backend-1.0.0.jar';
const JAVA = path.join(DEV, 'jdk-17', 'bin', 'java.exe');
const MYSQLD = path.join(DEV, 'mysql', 'bin', 'mysqld.exe');
const MY_INI = path.join(DEV, 'mysql', 'my.ini');
const MYSQL_LOG_DIR = path.join(DEV, 'mysql', 'logs');
const MYSQL_LOG = path.join(MYSQL_LOG_DIR, 'mysqld-launcher.log');
const BACKEND_LOG = path.join(BACKEND_DIR, 'logs', 'gym-backend.log');
const BASE = 'http://127.0.0.1:8080';

const sleep = ms => new Promise(r => setTimeout(r, ms));
const line = () => console.log('------------------------------------------------------------');

function tcpAlive(port, host = '127.0.0.1', timeout = 800) {
  return new Promise(resolve => {
    const s = net.connect({ port, host });
    const done = ok => { s.destroy(); resolve(ok); };
    s.setTimeout(timeout);
    s.once('connect', () => done(true));
    s.once('timeout', () => done(false));
    s.once('error', () => done(false));
  });
}

async function backendUp() {
  if (!(await tcpAlive(8080))) return false;
  try {
    const r = await fetch(BASE + '/actuator/health', { signal: AbortSignal.timeout(2500) });
    return r.ok;
  } catch { return false; }
}

async function waitFor(fn, seconds, label) {
  process.stdout.write(`     等待${label}`);
  for (let i = 0; i < seconds; i++) {
    if (await fn()) { console.log(' 就绪'); return true; }
    process.stdout.write('.');
    await sleep(1000);
  }
  console.log(' 超时');
  return false;
}

function tail(file, n, title) {
  try {
    if (!fs.existsSync(file)) return console.log(`（未找到 ${title}：${file}）`);
    const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/).filter(Boolean);
    console.log(`\n${title} 末尾：`);
    line();
    console.log(lines.slice(-n).join('\n'));
    line();
  } catch (e) {
    console.log(`（读取 ${title} 失败：${e.message}）`);
  }
}

/** 后台拉起 mysqld（不占用窗口，随启动器退出后仍继续运行） */
function startMysqlDetached() {
  fs.mkdirSync(MYSQL_LOG_DIR, { recursive: true });
  const out = fs.openSync(MYSQL_LOG, 'a');
  const child = spawn(MYSQLD, [`--defaults-file=${MY_INI}`], {
    detached: true,                       // 脱离父进程，启动器退出后继续运行
    stdio: ['ignore', out, out],
    windowsHide: true,
  });
  child.unref();
  return child.pid;
}

(async () => {
  console.log('============================================================');
  console.log(' 健身房运营管理系统 · 一键启动');
  console.log('============================================================\n');

  /* ---------- 1. MySQL ---------- */
  let useMysql = true;
  if (await tcpAlive(3307)) {
    console.log('[1/3] MySQL 已在运行（端口 3307）');
  } else {
    console.log('[1/3] 正在启动 MySQL（端口 3307，后台常驻）...');
    let pid = null;
    try { pid = startMysqlDetached(); } catch (e) { console.log('     启动失败：' + e.message); }

    const ready = await waitFor(() => tcpAlive(3307), 40, 'MySQL 就绪');
    if (!ready) {
      useMysql = false;
      tail(MYSQL_LOG, 20, 'MySQL 启动日志');
      console.log('[!] MySQL 未能启动 → 自动改用免数据库的 local 模式（H2 内存库）');
      console.log('    数据为本次启动的演示数据，重启即重建；修好 MySQL 后可重跑本脚本切回。');
      console.log('    常见原因：上次的 mysqld 未退出占用数据目录 → 执行 taskkill /IM mysqld.exe /F\n');
    } else {
      console.log(`[1/3] MySQL 就绪（PID ${pid}）\n`);
    }
  }

  /* ---------- 2. 后端 ---------- */
  if (await backendUp()) {
    console.log('[2/3] 后端已在运行（端口 8080）');
  } else {
    const profile = useMysql ? 'mysql' : 'local';
    console.log(`[2/3] 正在启动后端（profile=${profile}），日志在新窗口显示 ...`);
    const cmdline = `start "Gym Backend (port 8080)" "${JAVA}" -jar "${JAR}" `
                  + `--spring.profiles.active=${profile} --server.port=8080`;
    const child = spawn(cmdline, { shell: true, cwd: BACKEND_DIR, stdio: 'ignore', windowsHide: false });
    child.on('error', e => console.log('     启动失败：' + e.message));

    const ok = await waitFor(backendUp, 150, '后端启动');
    if (!ok) {
      console.log('\n[ERROR] 后端未能在 150 秒内就绪。');
      tail(BACKEND_LOG, 25, '后端日志');
      console.log('常见原因：');
      console.log('  · MySQL 未就绪      → taskkill /IM mysqld.exe /F 后重跑本脚本');
      console.log('  · 8080 被占用       → taskkill /IM java.exe /F 后重试');
      console.log('  · JAR 未构建        → 在 backend 目录执行 mvn package');
      process.exit(1);
    }
    console.log('[2/3] 后端就绪\n');
  }

  /* ---------- 3. 浏览器 ---------- */
  console.log('[3/3] 正在打开浏览器 ...');
  spawn(`start "" "${BASE}/"`, { shell: true, stdio: 'ignore' });
  await sleep(1500);

  line();
  console.log(' 启动完成！请在浏览器中登录：');
  console.log('   门店后台：manager / 123456     （演示主要用这个）');
  console.log('   管理员　：admin   / 123456     （可重置数据）');
  console.log('   会员端　：member1 / 123456');
  line();
  console.log(` 数据库：MySQL 已在后台运行（端口 3307）`);
  console.log(` 日志　：${MYSQL_LOG}`);
  line();
  console.log(' 停止：taskkill /IM java.exe /F  与  taskkill /IM mysqld.exe /F');
  line();
})();
