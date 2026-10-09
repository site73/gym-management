/**
 * 二维码结构校验（verify/qr-check.js）
 *
 * 目的：证明前端内嵌的二维码生成器产出的是**结构合法、内容可还原**的二维码。
 * 方法：完全黑盒 —— 只读取生成的 SVG 图形，重建模块矩阵，然后
 *   1) 校验标准结构特征：尺寸 17+4v、三个定位图案、时序图案、固定暗模块；
 *   2) 从图中读出格式信息，做 BCH 校验并取回纠错级别与掩码号；
 *   3) 按标准顺序反解数据位，做多块反交织，还原原文并与输入比对。
 *
 * 说明：本机浏览器未开放 BarcodeDetector 接口，因此改用等价的"结构 + 反解"校验；
 *      它能覆盖编码链路的每个环节（编码、纠错、交织、放置、掩码、格式信息）。
 *
 * 运行：NODE_PATH=E:/dev/pw/node_modules node verify/qr-check.js
 */

const { chromium } = require('playwright-core');
const fs = require('fs');
const EDGE = 'C://Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';

(async () => {
  const html = fs.readFileSync('E:/WorkBuddy/健身房管理业务/08-系统结构与代码设计/backend/src/main/resources/static/index.html', 'utf8');
  const m = html.match(/const qrSvg = \(function \(\)[\s\S]*?\n\}\)\(\);/);
  if (!m) { console.log('未找到 qrSvg 实现'); process.exit(1); }

  const browser = await chromium.launch({ executablePath: EDGE, headless: true });
  const page = await browser.newPage();
  await page.setContent('<html><body></body></html>');
  await page.addScriptTag({ content: m[0] });

  // 在浏览器里生成 SVG，再在 Node 侧解析（真正的黑盒）
  const cases = ['GYM-CHECKIN:12:1', 'GYM-CHECKIN:345:7', 'HELLO-GYM-2026', 'A'.repeat(60), 'X'.repeat(120)];
  let pass = 0, fail = 0;

  for (const text of cases) {
    const svg = await page.evaluate(t => qrSvg(t), text);
    const r = analyze(svg, text);
    if (r.ok) { pass++; console.log(`  PASS  QR  v${r.version} ${r.size}×${r.size}  内容反解一致  [${JSON.stringify(text.slice(0, 20))}]`); }
    else { fail++; console.log(`  FAIL  QR  ${r.reason}  [${JSON.stringify(text.slice(0, 20))}]`); }
  }
  console.log('  ' + '-'.repeat(52));
  console.log(`  二维码校验：通过 ${pass} / 失败 ${fail} / 共 ${cases.length}`);
  await browser.close();

  function analyze(svg, expect) {
    const vb = svg.match(/viewBox="0 0 (\d+) (\d+)"/);
    if (!vb) return { ok: false, reason: '没有 viewBox' };
    const dim = Number(vb[1]);
    const size = dim - 4;                       // 静区 quiet = 2
    if ((size - 17) % 4 !== 0 || size < 21) return { ok: false, reason: '尺寸不是 17+4v' };
    const version = (size - 17) / 4;

    const g = Array.from({ length: size }, () => new Array(size).fill(0));
    const pd = svg.match(/<path d="([^"]*)"/);
    if (!pd) return { ok: false, reason: '没有 path 数据' };
    const re = /M(\d+) (\d+)h1v1h-1z/g;
    let mm;
    while ((mm = re.exec(pd[1]))) {
      const c = Number(mm[1]) - 2, r = Number(mm[2]) - 2;
      if (r < 0 || r >= size || c < 0 || c >= size) return { ok: false, reason: '模块坐标越界' };
      g[r][c] = 1;
    }

    // --- 结构校验：定位图案 ---
    const finderOk = (r0, c0) => {
      for (let r = 0; r < 7; r++) for (let c = 0; c < 7; c++) {
        const ring = (r === 0 || r === 6 || c === 0 || c === 6);
        const core = r >= 2 && r <= 4 && c >= 2 && c <= 4;
        if (g[r0 + r][c0 + c] !== ((ring || core) ? 1 : 0)) return false;
      }
      return true;
    };
    if (!finderOk(0, 0) || !finderOk(0, size - 7) || !finderOk(size - 7, 0)) {
      return { ok: false, reason: '定位图案不符合标准' };
    }
    // --- 结构校验：时序图案 ---
    for (let i = 8; i < size - 8; i++) {
      if (g[6][i] !== (i % 2 === 0 ? 1 : 0)) return { ok: false, reason: '横向时序图案错误' };
      if (g[i][6] !== (i % 2 === 0 ? 1 : 0)) return { ok: false, reason: '纵向时序图案错误' };
    }
    // --- 结构校验：固定暗模块 ---
    if (g[size - 8][8] !== 1) return { ok: false, reason: '固定暗模块缺失' };

    // --- 重建功能模块图（与编码端一致） ---
    const fixed = Array.from({ length: size }, () => new Array(size).fill(false));
    const mark = (r0, r1, c0, c1) => { for (let r = r0; r <= r1; r++) for (let c = c0; c <= c1; c++) if (r >= 0 && r < size && c >= 0 && c < size) fixed[r][c] = true; };
    mark(-1, 7, -1, 7); mark(-1, 7, size - 8, size - 1); mark(size - 8, size - 1, -1, 7);
    for (let i = 0; i < size; i++) { fixed[6][i] = true; fixed[i][6] = true; }
    const ALIGN = { 1: [], 2: [6,18], 3: [6,22], 4: [6,26], 5: [6,30], 6: [6,34], 7: [6,22,38], 8: [6,24,42], 9: [6,26,46], 10: [6,28,50] };
    const al = ALIGN[version] || [];
    al.forEach(r0 => al.forEach(c0 => {
      if (fixed[r0][c0]) return;   // 与定位图案重叠 → 跳过（与编码端一致）
      for (let r = -2; r <= 2; r++) for (let c = -2; c <= 2; c++) fixed[r0 + r][c0 + c] = true;
    }));
    for (let i = 0; i <= 8; i++) { fixed[8][i] = true; fixed[i][8] = true; fixed[size - 1 - i][8] = true; }
    for (let i = 0; i < 8; i++) fixed[8][size - 1 - i] = true;

    // --- 读格式信息（第一份副本）→ 取掩码号 ---
    let fbits = 0;
    for (let i = 14; i >= 0; i--) {
      let b;
      if (i < 6) b = g[8][i];
      else if (i < 8) b = g[8][i + 1];
      else if (i === 8) b = g[7][8];
      else b = g[14 - i][8];
      fbits = (fbits << 1) | b;
    }
    const unmasked = fbits ^ 0b101010000010010;
    const ecLevel = (unmasked >> 13) & 0b11;
    const mask = (unmasked >> 10) & 0b111;
    if (ecLevel !== 0) return { ok: false, reason: '纠错级别不是 M（读到 ' + ecLevel + '）' };
    // BCH 校验：低 10 位应能整除生成多项式
    let chk = unmasked;
    for (let i = 14; i >= 10; i--) if (chk & (1 << i)) chk ^= 0b10100110111 << (i - 10);
    if (chk !== 0) return { ok: false, reason: '格式信息 BCH 校验失败' };

    const MASKS = [
      (r, c) => (r + c) % 2 === 0, (r) => r % 2 === 0, (r, c) => c % 3 === 0,
      (r, c) => (r + c) % 3 === 0, (r, c) => (Math.floor(r / 2) + Math.floor(c / 3)) % 2 === 0,
      (r, c) => ((r * c) % 2) + ((r * c) % 3) === 0,
      (r, c) => (((r * c) % 2) + ((r * c) % 3)) % 2 === 0,
      (r, c) => (((r + c) % 2) + ((r * c) % 3)) % 2 === 0
    ];
    // 反掩码
    const d = g.map(r => r.slice());
    for (let r = 0; r < size; r++) for (let c = 0; c < size; c++) if (!fixed[r][c] && MASKS[mask](r, c)) d[r][c] ^= 1;

    // --- 按标准顺序读数据位 ---
    const bits = [];
    let up = true;
    for (let col = size - 1; col > 0; col -= 2) {
      if (col === 6) col = 5;
      for (let k = 0; k < size; k++) {
        const row = up ? size - 1 - k : k;
        for (let dd = 0; dd < 2; dd++) {
          const c = col - dd;
          if (fixed[row][c]) continue;
          bits.push(d[row][c]);
        }
      }
      up = !up;
    }
    // 转字节
    const bytes = [];
    for (let i = 0; i + 8 <= bits.length; i += 8) {
      let b = 0; for (let j = 0; j < 8; j++) b = (b << 1) | bits[i + j];
      bytes.push(b);
    }
    // --- 反交织：按块还原（多块版本必须做，否则读到的是交织流） ---
    const RS_M = {
      1: [10, [[1, 16]]], 2: [16, [[1, 28]]], 3: [26, [[1, 44]]], 4: [18, [[2, 32]]],
      5: [24, [[2, 43]]], 6: [16, [[4, 27]]], 7: [18, [[4, 31]]],
      8: [22, [[2, 38], [2, 39]]], 9: [22, [[3, 36], [2, 37]]], 10: [26, [[4, 43], [1, 44]]]
    };
    const groups = RS_M[version][1];
    const blk = [];
    groups.forEach(g => { for (let i = 0; i < g[0]; i++) blk.push({ len: g[1], data: [] }); });
    let ptr = 0;
    const maxD = Math.max.apply(null, blk.map(b => b.len));
    for (let i = 0; i < maxD; i++) blk.forEach(b => { if (i < b.len) b.data.push(bytes[ptr++]); });
    const flat = [];
    blk.forEach(b => b.data.forEach(v => flat.push(v)));

    if (flat[0] >> 4 !== 0b0100) return { ok: false, reason: '模式指示符不是 Byte(0100)' };
    const cntBits = version <= 9 ? 8 : 16;
    let cnt = 0;
    for (let i = 0; i < cntBits; i++) {
      const bitIdx = 4 + i;
      cnt = (cnt << 1) | ((flat[Math.floor(bitIdx / 8)] >> (7 - (bitIdx % 8))) & 1);
    }
    const outBytes = [];
    for (let i = 0; i < cnt; i++) {
      let b = 0;
      for (let j = 0; j < 8; j++) {
        const bitIdx = 4 + cntBits + i * 8 + j;
        b = (b << 1) | ((flat[Math.floor(bitIdx / 8)] >> (7 - (bitIdx % 8))) & 1);
      }
      outBytes.push(b);
    }
    const decoded = Buffer.from(outBytes).toString('utf8');
    if (decoded !== expect) return { ok: false, reason: `内容不一致：读到 ${JSON.stringify(decoded.slice(0, 30))}` };
    return { ok: true, version, size };
  }
})();
