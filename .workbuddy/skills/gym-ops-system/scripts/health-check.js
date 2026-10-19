#!/usr/bin/env node
/**
 * 健康检查：服务存活 / 角色登录 / 名单-余位闭合 / 课表有内容
 *
 * 用法：node .workbuddy/skills/gym-ops-system/scripts/health-check.js
 * 退出码：0 全部通过；1 有失败
 */
const BASE = process.env.API_BASE || "http://127.0.0.1:8080";

async function call(method, path, body, token) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers["X-Token"] = token;
  const r = await fetch(BASE + path, {
    method, headers, body: body === undefined ? undefined : JSON.stringify(body),
  });
  let data = null;
  try { data = await r.json(); } catch (e) { /* 非 JSON */ }
  return { status: r.status, data };
}

const login = (u, p = "123456") => call("POST", "/api/auth/login", { username: u, password: p });

let pass = 0, fail = 0;
function check(name, cond, actual) {
  console.log(`${cond ? "PASS" : "FAIL"}  ${name}  | 实际: ${actual}`);
  cond ? pass++ : fail++;
}

(async () => {
  console.log(`健康检查 ${BASE}\n`);

  // 1. 服务存活
  try {
    const page = await fetch(BASE + "/");
    check("页面可访问", page.status === 200, `HTTP ${page.status}`);
  } catch (e) {
    check("页面可访问", false, e.message);
    console.log("\n服务未启动，后续检查跳过");
    process.exit(1);
  }

  // 2. 五类角色登录
  const accounts = [
    ["member1", "MEMBER"], ["coach1", "COACH"], ["manager", "STAFF"],
    ["admin", "STAFF"], ["superadmin", "STAFF"],
  ];
  const tokens = {};
  for (const [u, role] of accounts) {
    const r = await login(u);
    const ok = r.status === 200 && r.data && r.data.user && r.data.user.role === role;
    check(`登录 ${u}`, ok, `${r.status} role=${r.data && r.data.user && r.data.user.role}`);
    if (ok) tokens[u] = r.data.token;
  }
  if (!tokens.superadmin) { console.log("\n管理员登录失败，后续检查跳过"); process.exit(1); }

  // 3. 账号管理仅超管可用
  const accSup = await call("GET", "/api/admin/accounts", undefined, tokens.superadmin);
  check("超管可查账号", accSup.status === 200 && Array.isArray(accSup.data), `${accSup.status}`);
  const accMgr = await call("GET", "/api/admin/accounts", undefined, tokens.manager);
  check("店长不可查账号", accMgr.status === 403, `${accMgr.status}`);

  // 4. 名单 ⇄ 余位闭合（全量）
  if (tokens.manager) {
    const cs = await call("GET", "/api/courses", undefined, tokens.manager);
    const courses = Array.isArray(cs.data) ? cs.data : [];
    let bad = 0;
    for (const c of courses) {
      const r = await call("GET", `/api/courses/${c.id}/bookings`, undefined, tokens.manager);
      const n = Array.isArray(r.data) ? r.data.length : -1;
      if (n !== c.bookedCount) { bad++; console.log(`  ! ${c.code} 余位 ${c.bookedCount} 名单 ${n}`); }
    }
    check(`名单与余位闭合（${courses.length} 门）`, courses.length > 0 && bad === 0,
      `不一致 ${bad} 门`);

    // 5. 课表有内容
    const tt = await call("GET", "/api/timetable/overview", undefined, tokens.manager);
    const cnt = tt.data && tt.data.courses
      ? tt.data.courses.days.reduce((n, d) => n + d.items.length, 0) : -1;
    check("本周课程表有内容", tt.status === 200 && cnt > 0, `${cnt} 段`);
    const vcnt = tt.data && tt.data.venues
      ? tt.data.venues.days.reduce((n, d) => n + d.items.length, 0) : -1;
    check("场馆占用课表有内容", tt.status === 200 && vcnt > 0, `${vcnt} 段`);
  }

  console.log(`\n结果：通过 ${pass} / 失败 ${fail}`);
  process.exit(fail === 0 ? 0 : 1);
})();
