'use strict';
/**
 * 健身房运营管理系统 · S1 切片可运行服务（零依赖，仅用 Node 内置模块）
 *
 * 启动：node app/server.js        （默认 http://localhost:3000）
 * 页面：浏览器打开 http://localhost:3000
 * API ：见 contracts/openapi.yaml
 */
const http = require('http');
const fs = require('fs');
const path = require('path');
const svc = require('./src/service');
const store = require('./src/store');

const PORT = Number(process.env.PORT || 3111);
const HOST = process.env.HOST || '127.0.0.1';   // 仅监听本机回环，不对外暴露
const PUBLIC_DIR = path.join(__dirname, 'public');

function json(res, code, body) {
  const text = JSON.stringify(body, null, 2);
  res.writeHead(code, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type',
    'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
  });
  res.end(text);
}

function readBody(req) {
  return new Promise(resolve => {
    let raw = '';
    req.on('data', c => { raw += c; });
    req.on('end', () => {
      if (!raw) return resolve({});
      try { resolve(JSON.parse(raw)); } catch { resolve({}); }
    });
  });
}

function serveStatic(res, urlPath) {
  const rel = urlPath === '/' ? 'index.html' : urlPath.replace(/^\/+/, '');
  const file = path.join(PUBLIC_DIR, rel);
  if (!file.startsWith(PUBLIC_DIR) || !fs.existsSync(file)) { res.writeHead(404); res.end('Not Found'); return; }
  const ext = path.extname(file).toLowerCase();
  const type = ext === '.html' ? 'text/html; charset=utf-8'
    : ext === '.css' ? 'text/css; charset=utf-8'
    : ext === '.js' ? 'application/javascript; charset=utf-8'
    : 'application/octet-stream';
  res.writeHead(200, { 'Content-Type': type });
  res.end(fs.readFileSync(file));
}

async function handleApi(req, res, pathname, query) {
  const m = (re) => pathname.match(re);
  let hit;

  if (req.method === 'GET' && pathname === '/api/health') return json(res, 200, { ok: true, service: 'gym-backend(s1)', time: new Date().toISOString() });
  if (req.method === 'GET' && pathname === '/api/courses') return json(res, 200, svc.listCourses());
  if (req.method === 'GET' && pathname === '/api/reports/summary') return json(res, 200, svc.summary());

  if ((hit = m(/^\/api\/members\/(\d+)$/))) {
    const mem = svc.getMember(hit[1]);
    return mem ? json(res, 200, mem) : json(res, 404, { reason: '会员不存在' });
  }
  if ((hit = m(/^\/api\/members\/(\d+)\/bookings$/))) return json(res, 200, svc.listBookings(hit[1]));

  if (req.method === 'POST' && pathname === '/api/bookings') {
    const b = await readBody(req);
    const r = svc.book(b.memberId, b.courseId);
    return json(res, r.ok ? 200 : 409, r);
  }
  if ((hit = m(/^\/api\/bookings\/([^/]+)\/checkin$/)) && req.method === 'POST') {
    const b = await readBody(req);
    const r = svc.checkIn(hit[1], b.channel, b.operatorId);
    return json(res, r.ok ? 200 : 409, r);
  }
  if ((hit = m(/^\/api\/bookings\/([^/]+)\/cancel$/)) && req.method === 'POST') {
    const r = svc.cancel(hit[1]);
    return json(res, r.ok ? 200 : 409, r);
  }
  if ((hit = m(/^\/api\/bookings\/([^/]+)\/no-show$/)) && req.method === 'POST') {
    const r = svc.markNoShow(hit[1]);
    return json(res, r.ok ? 200 : 409, r);
  }

  if (req.method === 'POST' && pathname === '/api/orders') {
    const b = await readBody(req);
    return json(res, 200, svc.createOrder(b.memberId, b.bizType, b.amount, b.times));
  }
  if ((hit = m(/^\/api\/orders\/([^/]+)\/pay$/)) && req.method === 'POST') {
    const r = svc.payOrder(hit[1]);
    return json(res, r.ok ? 200 : 400, r);
  }
  if ((hit = m(/^\/api\/orders\/([^/]+)\/notify$/)) && req.method === 'POST') {
    const b = await readBody(req);
    const r = svc.payNotify(hit[1], b.paidAmount);
    return json(res, r.ok ? 200 : 409, r);
  }

  if (req.method === 'POST' && pathname === '/api/jobs/risk-score') return json(res, 200, svc.riskScore());
  if (req.method === 'GET' && pathname === '/api/risks') return json(res, 200, store.load().tasks);
  if (req.method === 'GET' && pathname === '/api/predictions') return json(res, 200, svc.noShowPrediction(query.get('courseId')));
  if (req.method === 'GET' && pathname === '/api/settlement') return json(res, 200, svc.settlement(query.get('period')));
  if (req.method === 'GET' && pathname === '/api/audit') return json(res, 200, store.load().audit.slice(-50));
  if (req.method === 'POST' && pathname === '/api/admin/reset') { store.reset(); return json(res, 200, { ok: true, message: '数据已重置为种子数据' }); }

  return json(res, 404, { reason: '接口不存在：' + pathname });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = url.pathname;
  if (req.method === 'OPTIONS') return json(res, 204, {});
  try {
    if (pathname.startsWith('/api/')) return await handleApi(req, res, pathname, url.searchParams);
    return serveStatic(res, pathname);
  } catch (e) {
    console.error('[server] 处理失败：', e);
    return json(res, 500, { reason: e.message });
  }
});

function start(port = PORT, silent = false) {
  return new Promise(resolve => {
    server.listen(port, HOST, () => {
      if (!silent) {
        console.log('====================================================');
        console.log(' 健身房运营管理系统 · S1 切片已启动');
        console.log(` 页面： http://${HOST}:${port}`);
        console.log(` 健康检查： http://${HOST}:${port}/api/health`);
        console.log(` 数据文件： ${store.DATA_FILE}`);
        console.log(' 按 Ctrl+C 停止');
        console.log('====================================================');
      }
      resolve(server);
    });
  });
}

if (require.main === module) start();

module.exports = { start, server };
