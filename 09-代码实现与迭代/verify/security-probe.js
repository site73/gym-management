const BASE = "http://127.0.0.1:8080";
async function call(m, p, body, token) {
  const h = { "Content-Type": "application/json" };
  if (token) h["X-Token"] = token;
  let r, d = null;
  try {
    r = await fetch(BASE + p, { method: m, headers: h, body: body === undefined ? undefined : JSON.stringify(body) });
    try { d = await r.json(); } catch (e) { d = null; }
  } catch (e) { return { status: 0, data: {} }; }
  return { status: r.status, data: d };
}
const login = async (u, p) => call("POST", "/api/auth/login", { username: u, password: p });

(async () => {
  const mgr = (await login("manager", "123456")).data.token;
  const sup = (await login("superadmin", "123456")).data.token;

  // 1. XSS：注册姓名带脚本的会员
  const u = "x" + Date.now().toString().slice(-6);
  const payload = "<img src=x onerror=alert(1)>";
  const reg = await call("POST", "/api/auth/register", { username: u, password: "123456", name: payload, phone: "13800001111" });
  console.log("[1] 注册脚本名会员:", reg.status, "返回 name =", reg.data && (reg.data.name || reg.data.reason || reg.data.message));

  // 2. 查会员库是否原样存储
  const ms = (await call("GET", "/api/members", undefined, mgr)).data;
  const hit = ms.find(m => m.name && m.name.includes("onerror"));
  console.log("[2] 会员库原样存储脚本名:", hit ? "是（未过滤）" : "否（已过滤）");

  // 3. 若原样存储，检查门店端名单/课表等前端渲染是否转义（读取源码判断）
  const fs = require("fs");
  const html = fs.readFileSync("E:/WorkBuddy/健身房管理业务/08-系统结构与代码设计/backend/src/main/resources/static/index.html", "utf8");
  // showCourseRoster 里的渲染片段
  const rosterFrag = html.match(/async function showCourseRoster[\s\S]*?openModal/)[0];
  const rosterUsesSafe = rosterFrag.includes("safe(b.memberName)") || rosterFrag.includes("safe(x.memberName)");
  console.log("[3] 名单弹窗 memberName 是否转义:", rosterUsesSafe ? "是" : "否（XSS 风险）");

  // 4. 分页越界
  const p1 = await call("GET", "/api/members?page=-1", undefined, mgr);
  const p2 = await call("GET", "/api/members?page=99999", undefined, mgr);
  console.log("[4] 分页越界 page=-1 =>", p1.status, "| page=99999 =>", p2.status, Array.isArray(p2.data) ? ("返回 " + p2.data.length + " 条") : JSON.stringify(p2.data).slice(0, 60));

  // 5. 重复支付通知幂等
  const ord = await call("POST", "/api/orders", { memberId: 1, bizType: "year", amount: 1, times: 0 }, mgr);
  const ono = ord.data && ord.data.orderNo;
  if (ono) {
    const n1 = await call("POST", `/api/pay/notify/${ono}`, { paidAmount: 1, outTradeNo: "T-" + Date.now() });
    const n2 = await call("POST", `/api/pay/notify/${ono}`, { paidAmount: 1, outTradeNo: "T2-" + Date.now() });
    console.log("[5] 支付通知幂等:", "第1次 status=" + (n1.data && n1.data.status), "| 第2次 status=" + (n2.data && n2.data.status));
  }

  // 6. 会员查看他人会员详情越权
  const m1 = (await login("member1", "123456")).data.token;
  const r6 = await call("GET", "/api/members/3", undefined, m1);
  console.log("[6] 会员查看他人档案:", r6.status, "（403=安全）");

  // 7. 教练伪造 coachId 查看他人课表
  const coach = (await login("coach1", "123456")).data.token;
  const r7 = await call("GET", "/api/timetable/courses?scope=all&coachId=102", undefined, coach);
  console.log("[7] 教练伪造coachId=102看课表:", r7.status, "scope=" + (r7.data && r7.data.scope), "（mine=安全）");

  // 8. 超管能否直接读明文密码（应不存在该字段）
  const r8 = (await call("GET", "/api/admin/accounts", undefined, sup)).data;
  const leak = r8 && r8.some(a => a.password || a.passwordHash);
  console.log("[8] 账号接口是否泄露明文/哈希密码:", leak ? "泄露!" : "无（安全）");

  console.log("\n=== 探测完成 ===");
})();
