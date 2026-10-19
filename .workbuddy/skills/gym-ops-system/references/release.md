# 发布、版本管理与答辩演示

## 1. 仓库

- 地址：`https://github.com/site73/gym-management`（分支 `main`）
- 作者/提交者统一：`site73 <site73@users.noreply.github.com>`
  （提交前用 `git log --format='%an %ae %cn %ce' -3` 复核，不得出现真实姓名）

## 2. 提交规范

采用 `type(scope): 摘要` + 正文说明「问题 / 根因 / 修复 / 验证」。

| type | 用途 |
|---|---|
| feat | 新功能 |
| fix | 缺陷修复 |
| test | 测试用例或测试脚本 |
| docs | 需求/设计/测试文档、PPT、讲稿 |
| refactor | 不改变行为的重构 |
| chore | 构建、依赖、配置 |

一次迭代按"修复 → 测试 → 文档"拆成多个提交，便于回溯。

## 3. 版本标签

已打标签（附注标签，含验收口径）：

| 标签 | 内容 | 验收 |
|---|---|---|
| `v2.1-timetable` | 课表式管理 + 超级管理员账号治理 | 374 项 |
| `v2.2-closure` | 报名名单与课程余位严格闭合（BUG-017） | 388 项 |

新版本发布：

```bash
git tag -a v2.3-xxx -m "v2.3 · <主题>（<N> 项检查全绿）"
git -c http.proxy= -c https.proxy= push origin --tags
```

## 4. 推送

```bash
git -c http.proxy= -c https.proxy= push origin main
```

- 本机代理端口会变（2467 / 7237），且常返回 502；清空代理走直连。
- 直连 GitHub 也可能超时（`Failed to connect to github.com:443`）。这是网络环境问题：
  本地 commit 不受影响，网络恢复后补推即可；推送前用 `git status -sb` 确认 ahead 数量。
- 不要为了推送去改 `origin` 或强推；先确认本地提交完整。

## 5. 答辩演示

材料：`10-展示汇报/健身房运营管理系统答辩/`

- `健身房运营管理系统答辩.pptx`：29 页
- `展讲话稿.md` / `.docx`：逐页台词 + 演示步骤 + 删减方案
- `build/`：`main.py` 组装 PPT（含版面溢出自检）、`md2docx.py` 生成讲稿 Word

### 演示动线（15 分钟版）

1. 会员：`member1` → 我的课表 → 扫码签到出示二维码
2. 门店：`manager` → 周课表（全店 + 场馆占用）→ 课程管理切周课表 → 满员课程名单 15 人
3. 教练：`coach1` → 我的课表 + 学员名单
4. 超管：`superadmin` → 账号管理 → 重置密码返回新密码
5. 收尾：`node verify/browser-test.js` 现场跑一遍

### 时间控制

全量讲完约 24 分钟，超出 15–20 分钟区间。上台按讲稿第五节的分档删减方案走，
必讲页：04 / 06 / 08 / 13 / 14 / 22 / 23 / 25 / 26 / 28。不要靠加快语速。

### 演讲要点（容易被追问）

- **为什么看不到明文密码**：BCrypt 单向不可逆，改为"重置并返回新密码"，是安全取舍。
- **权限如何保证**：服务端归一化 `scope/memberId/coachId`，前端隐藏不算权限。
- **名单与余位为什么一定一致**：`booked_count` 由有效预约数全量重算，并有全量不变量用例兜底。
- **支付回调**：当前 mock 无验签，上线前必须接入 HMAC（已在需求 P0 标注，不掩饰）。

## 6. 交付前检查清单

- [ ] `mvn test` 全绿
- [ ] 集成 / 前端静态 / 二维码 / 浏览器 / BDD / 冒烟 全绿
- [ ] 需求文档、测试文档、覆盖矩阵的 md 与 docx 都是最新
- [ ] PPT 页码连续、无版面溢出（`python build/main.py` 自检）
- [ ] `git log --format='%an %ae'` 无真实姓名
- [ ] 工作区干净，标签已推送
