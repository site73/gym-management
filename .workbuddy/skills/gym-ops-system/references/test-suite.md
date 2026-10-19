# 测试体系（七层 · 388 项）

## 1. 分层与职责

| 层次 | 载体 | 项数 | 关注点 |
|---|---|---|---|
| 单元 | JUnit5 + Mockito | 67 | 业务规则、权限判定、阈值算法、课表聚合 |
| 架构守卫 | ArchUnit | 3 | 模块边界、依赖方向、Controller 不直连仓储 |
| 行为驱动 | Cucumber（读 `05-测试用例与测试计划/features`） | 18 | 关键规则可执行 |
| 集成 | `verify/integration-test.js` | 139 | 真实 HTTP：登录/权限/选课/排课/场地/名单/课表/账号 |
| 前端静态 | `verify/frontend-check.js` | 6 | 按钮无响应类缺陷（未定义函数/未加引号/元素缺失/接口不存在/未声明常量） |
| 真实浏览器 | `verify/browser-test.js` | 113 | Playwright + Edge：真实点击、弹窗、导航、课表、名单 |
| 二维码 | `verify/qr-check.js` | 5 | 结构合法、内容可反解 |
| 参考实现 | `verify/run.js` + `app/smoke.js` | 18 + 18 | 与主实现行为一致 |

另有主动安全探测 `verify/security-probe.js`（8 项，不进入 388 计数）。

## 2. 执行顺序建议

先起服务，再按"快 → 慢"：

```bash
mvn test
node verify/frontend-check.js
node verify/qr-check.js
node verify/integration-test.js
node verify/run.js && node app/smoke.js
NODE_PATH=E:/dev/pw/node_modules node verify/browser-test.js
node verify/security-probe.js
```

集成与浏览器脚本自带数据复位，顺序可换；但**浏览器测试会改动数据**，若先跑浏览器再跑集成，集成仍会自己复位，不受影响。

## 3. 用例分组（便于定位）

- 集成：A 认证 / A2 注册 / B 预约 / C 签到 / D 爽约 / E 排课选课 / K 教练 / L 场地 / M 名单 / N 账号管理 / O 周课表 / P 数据闭合 / F 会话
- 浏览器：A 登录页 / B 会员端 / C 门店后台 / D 管理员 / E 课程管理 / F 注册联动 / H 身份导航 / I 搜索 / J 扫码 / K 场地 / L 教练端 / M 管理页 / N 名单分页 + N2 闭合 / O 课表 / P 账号 / F 运行时健康

## 4. 关键不变量用例

- **IT-128 全量闭合**：遍历所有课程，断言「名单条数 == booked_count」。新增会改变预约状态的接口后必须重跑。
- **IT-131~135**：容量 3 的课程，选课/代约/取消/退课每一步同步断言余位与名单。
- **BT-080~083**：满员课程（瑜伽 15/15）名单必须 15 行且含姓名编号。
- **BT-105~108**：无 JS 错误、无 5xx、无未定义函数、无 404。

## 5. 失败定位

| 报错 | 常见原因 | 处理 |
|---|---|---|
| `Cannot find module 'playwright-core'` | 未设 NODE_PATH 或目录不对 | `NODE_PATH=E:/dev/pw/node_modules`，在 `09-代码实现与迭代` 下执行 |
| `waitForSelector: Timeout` | 上一角色 DOM 残留 / 接口慢 | 等待具体文本变化而非仅等元素存在；relogin 后加短等待 |
| 集成某条 404/400 | 测试数据累积导致编号/房间冲突 | 用唯一 `code/room/coachId`（SYS-R3 冲突校验真实生效） |
| `ECONNREFUSED` | 服务未起或端口错 | 先 health-check |
| 名单与余位不一致 | 有路径绕过了 `occupySeat/releaseSeat` | 重启触发 `alignRoster()` 对齐，再补该路径的测试 |

## 6. 新增测试的约定

1. 集成用例编号连续，分组用大写字母，`check(组, 用例名, 期望, 实际, 条件)`。
2. 造数据用唯一后缀（时间戳后 6 位），避免多次运行撞车。
3. 断言"结果 + 副作用"两条：例如选课不仅看 200，还要看余位与名单同步 +1。
4. 前端静态检查会挡住未加引号的 `onclick` 参数；动态行优先用事件委托 + `data-*`。
