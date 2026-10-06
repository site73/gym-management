'use strict';
/**
 * 前端静态检查（防止"点击无响应"类缺陷）
 *
 * 检查项：
 *  1. 内联脚本语法是否合法
 *  2. onclick 中的参数是否加了引号（未加引号会把字符串当变量 → ReferenceError 静默失败）
 *  3. onclick 调用的函数是否都已定义
 *  4. $('id') 引用的元素是否存在（不存在会静默报错）
 *  5. 前端调用的接口路径在后端是否存在（防止"调了个不存在的接口"）
 *
 * 运行：node verify/frontend-check.js
 */
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const ROOT = path.resolve(__dirname, '..', '..');
const PAGE = path.join(ROOT, '08-系统结构与代码设计', 'backend', 'src', 'main', 'resources', 'static', 'index.html');
const JAVA_DIR = path.join(ROOT, '08-系统结构与代码设计', 'backend', 'src', 'main', 'java', 'com', 'gym');

let pass = 0, fail = 0;
const rows = [];
function check(id, name, detail, cond) {
  if (cond) pass++; else fail++;
  rows.push({ id, name, detail, ok: cond });
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${id}  ${name}  ${detail}`);
}

const html = fs.readFileSync(PAGE, 'utf8');
const scriptMatch = html.match(/<script>([\s\S]*?)<\/script>/);
if (!scriptMatch) { console.log('FAIL  未找到内联脚本'); process.exit(1); }
const js = scriptMatch[1];

/* ---------- 1. 语法（用 vm 编译，避免子进程与文件锁） ---------- */
let syntaxOk = true, syntaxErr = '';
try {
  new (require('vm').Script)(js);
} catch (e) {
  syntaxOk = false;
  syntaxErr = e.message;
}
check('FE-001', '内联脚本语法合法', syntaxOk ? '' : syntaxErr, syntaxOk);

/* ---------- 2/3. onclick 引号与函数定义 ---------- */
const defined = new Set();
for (const m of js.matchAll(/(?:async\s+)?function\s+([A-Za-z_$][\w$]*)\s*\(/g)) defined.add(m[1]);
for (const m of js.matchAll(/(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*(?:async\s*)?\(/g)) defined.add(m[1]);

const handlers = [...html.matchAll(/on(?:click|change|keydown)\s*=\s*"([^"]*)"/g)].map(m => m[1]);
const undefinedFns = [];
for (const h of handlers) {
  for (const call of h.matchAll(/([A-Za-z_$][\w$]*)\s*\(/g)) {
    const fn = call[1];
    if (['if', 'for', 'while', 'Number', 'String', 'JSON', 'alert', 'confirm', 'return', 'typeof'].includes(fn)) continue;
    if (!defined.has(fn)) undefinedFns.push(`${fn}() in "${h}"`);
  }
}
// 规则：内联处理器中的每个插值 ${...} 都必须位于引号内（字符串字面量），否则会被当作变量解析
const quoteIssues = handlers.filter(h => {
  let inQuote = false, quoteChar = '';
  for (let i = 0; i < h.length; i++) {
    const c = h[i];
    if (!inQuote && (c === "'" || c === '"')) { inQuote = true; quoteChar = c; continue; }
    if (inQuote && c === quoteChar) { inQuote = false; continue; }
    if (!inQuote && c === '$' && h[i + 1] === '{') return true;
  }
  return false;
});
check('FE-002', 'onclick 参数均已加引号', quoteIssues.length ? '未加引号: ' + quoteIssues.join(' | ') : `${handlers.length} 个处理器`, quoteIssues.length === 0);
check('FE-003', 'onclick 调用的函数均已定义', undefinedFns.length ? undefinedFns.join(' | ') : `${defined.size} 个函数`, undefinedFns.length === 0);

/* ---------- 4. $('id') 元素存在性 ---------- */
const ids = new Set([...html.matchAll(/\bid\s*=\s*"([^"]+)"/g)].map(m => m[1]));
const missingIds = [];
for (const m of js.matchAll(/\$\(\s*'([^']+)'\s*\)/g)) {
  if (!ids.has(m[1])) missingIds.push(m[1]);
}
const uniqMissing = [...new Set(missingIds)];
check('FE-004', '$(\'id\') 引用的元素都存在', uniqMissing.length ? '缺失: ' + uniqMissing.join(', ') : `${ids.size} 个元素`, uniqMissing.length === 0);

/* ---------- 5. 接口路径与后端一致性 ---------- */
function walk(dir, out = []) {
  for (const f of fs.readdirSync(dir)) {
    const p = path.join(dir, f);
    const st = fs.statSync(p);
    if (st.isDirectory()) walk(p, out);
    else if (f.endsWith('Controller.java')) out.push(p);
  }
  return out;
}
const routes = [];
for (const file of walk(JAVA_DIR)) {
  const src = fs.readFileSync(file, 'utf8');
  const base = (src.match(/@RequestMapping\(\s*"([^"]*)"\s*\)/) || [, ''])[1];
  // 兼顾 @GetMapping / @GetMapping() / @GetMapping("/x") / @GetMapping(value="/x") 四种写法
  for (const m of src.matchAll(/@(Get|Post|Put|Delete)Mapping\s*(?:\(\s*(?:value\s*=\s*)?"([^"]*)"[^)]*\))?/g)) {
    const p = (base + (m[2] || '')).replace(/\/+$/, '');
    routes.push({ method: m[1].toUpperCase(), path: p || '/' });
  }
}
function toRegex(p) {
  // 先按 {param} 切分，再对字面量部分转义，最后用 [^/]+ 拼回 —— 避免转义与占位符互相干扰
  const parts = p.split(/\{[^}]+\}/).map(s => s.replace(/[.*+?^$()|[\]\\]/g, '\\$&'));
  return new RegExp('^' + parts.join('[^/]+') + '$');
}
const routeMatchers = routes.map(r => ({ ...r, re: toRegex(r.path) }));

const frontendCalls = [];
for (const m of js.matchAll(/api\(\s*'(GET|POST|PUT|DELETE)'\s*,\s*(`[^`]*`|'[^']*')/g)) {
  let p = m[2].slice(1, -1)
    .replace(/\$\{[^}]*\}/g, '1')          // 模板变量 → 1
    .split('?')[0]                          // 去掉查询串
    .replace(/\/\d+/g, '/1');
  frontendCalls.push({ method: m[1], raw: p });
}
// 同时检查原生 fetch() 调用的路径（如登录接口）
for (const m of js.matchAll(/fetch\(\s*(`[^`]*`|'[^']*')/g)) {
  const raw = m[1].slice(1, -1);
  if (!raw.startsWith('/api/')) continue;
  const p = raw.replace(/\$\{[^}]*\}/g, '1').split('?')[0].replace(/\/\d+/g, '/1');
  frontendCalls.push({ method: 'POST-OR-GET', raw: p });
}
const unmatched = frontendCalls.filter(c => {
  if (c.method === 'POST-OR-GET') {
    return !routeMatchers.some(r => r.re.test(c.raw));
  }
  return !routeMatchers.some(r => r.method === c.method && r.re.test(c.raw));
});
check('FE-005', '前端调用的接口后端均存在',
  unmatched.length ? unmatched.map(u => `${u.method} ${u.raw}`).join(' | ')
                   : `${frontendCalls.length} 个调用 / 后端 ${routes.length} 个路由`,
  unmatched.length === 0);

/* ---------- 输出 ---------- */
console.log('\n----------------------------------------------------');
console.log(` 前端静态检查：通过 ${pass} / 失败 ${fail} / 共 ${pass + fail}`);
console.log('----------------------------------------------------');
const md = ['# 前端静态检查报告', '',
  `- 检查文件：\`${path.relative(ROOT, PAGE)}\``,
  `- 后端路由：${routes.length} 个（自动解析 Controller 注解）`,
  `- 结果：**通过 ${pass} / 失败 ${fail} / 共 ${pass + fail}**`, '',
  '| 编号 | 检查项 | 明细 | 结果 |', '|---|---|---|---|',
  ...rows.map(r => `| ${r.id} | ${r.name} | ${r.detail || '—'} | ${r.ok ? '✅ 通过' : '❌ 失败'} |`), ''].join('\n');
fs.writeFileSync(path.join(__dirname, 'frontend-check-report.md'), md, 'utf8');
console.log('已生成：verify/frontend-check-report.md');
process.exit(fail === 0 ? 0 : 1);
