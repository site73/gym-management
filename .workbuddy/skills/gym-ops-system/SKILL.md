---
name: gym-ops-system
description: 健身房运营管理系统（Spring Boot 3 + MySQL + 单页前端）的接手与维护手册。用于在本仓库中启动/构建/测试系统、排查环境与数据不一致问题、新增或修改业务功能、补齐七层测试、执行版本发布与答辩演示。当任务涉及本项目的运行环境（MySQL 3307 / 后端 8080）、五类角色权限、报名名单与课程余位口径、Flyway 迁移、或 388 项自动化检查时使用。
agent_created: true
---

# 健身房运营管理系统 · 项目技能

## Overview

本仓库是一个可运行的健身房运营管理系统（课程设计/答辩级交付），包含需求分析、系统设计、代码实现、七层测试与答辩材料。本技能提供接手该项目所需的**环境启动、构建测试、业务口径、常见故障排查**，避免每次从零摸索。

## When to use

在以下场景加载本技能：

- 在本仓库启动系统、复现演示环境，或修复"起不来/连不上库/端口错"之类问题
- 修改业务功能（课程、预约、场地、账号、课表），需要知道改哪层、守哪些约束
- 跑测试或补测试（单元 / 架构 / BDD / 集成 / 前端静态 / 浏览器 / 二维码 / 冒烟）
- 处理数据不一致（余位与名单不符、演示数据漂移、课表为空）
- 提交、打标签、推送、准备答辩演示

## 项目地图

```
健身房管理业务/
├── 04-目标系统需求分析/      需求报告、角色权限、追溯矩阵、接口清单（md + docx）
├── 05-测试用例与测试计划/    .feature 与用例设计
├── 07-数据库与部署设计/db/    migrations(V1–V13)、seed(R__)、部署说明
├── 08-系统结构与代码设计/backend/
│   ├── src/main/java/com/gym/  模块化单体，按域分包，每域 api/ + internal/
│   ├── src/main/resources/static/index.html   唯一前端（原生 JS 单页）
│   └── target/gym-backend-1.0.0.jar
├── 09-代码实现与迭代/
│   ├── verify/               集成、前端静态、浏览器、二维码、安全探测脚本
│   ├── app/                  参考实现 + 冒烟
│   ├── 系统测试文档.md/docx、需求—测试覆盖矩阵.md/docx
│   └── shots/                界面截图
└── 10-展示汇报/…答辩/         29 页 PPT + 讲稿 + build/ 可复现构建
```

## Quick start

环境为 E 盘便携版：JDK 17、Maven、MySQL 8（3307）、Playwright-core（`E:/dev/pw`）。

```bash
# 1) 启动 MySQL（后台常驻）
cd E:/dev/mysql/bin && ./mysqld.exe --defaults-file="E:/dev/mysql/my.ini" > ../mysqld-run.log 2>&1 &

# 2) 构建（必须带 -s 指定离线仓库配置）
cd E:/WorkBuddy/健身房管理业务/08-系统结构与代码设计/backend
export JAVA_HOME=E:/dev/jdk-17 && export PATH="$JAVA_HOME/bin:$PATH"
"E:/dev/maven/bin/mvn" -o -s E:/dev/maven/conf/settings-dev.xml -DskipTests package

# 3) 启动后端（必须显式指定 8080）
unset SERVER__PORT
"E:/dev/jdk-17/bin/java.exe" -jar target/gym-backend-1.0.0.jar \
  --spring.profiles.active=mysql --server.port=8080

# 4) 打开 http://127.0.0.1:8080/
```

演示账号（密码统一 `123456`）：`member1` 会员、`coach1` 教练、`manager` 店长、`admin` 管理员、`superadmin` 超级管理员。

## 七层测试

在 `09-代码实现与迭代/` 下执行：

```bash
mvn -o -s E:/dev/maven/conf/settings-dev.xml test        # 单元+架构+BDD  88
node verify/integration-test.js                          # 集成          139
node verify/frontend-check.js                            # 前端静态        6
node verify/qr-check.js                                  # 二维码          5（需 NODE_PATH）
NODE_PATH=E:/dev/pw/node_modules node verify/browser-test.js   # 浏览器   113
node verify/run.js                                       # Node BDD       18
node app/smoke.js                                        # 冒烟           18
node verify/security-probe.js                            # 安全探测（8 项）
```

合计 388 项。测试自带数据复位，可独立重复执行。

## 必须守住的业务口径

1. **名单 ⇄ 余位严格闭合**：`course.booked_count` 必须等于该课程有效预约数
   （`status in ('booked','checked_in')`）；已取消的预约不进名单、不占余位。
   新增任何改预约状态的路径后，都要保证这条不变量（集成测试 IT-128 全量遍历校验）。
2. **权限在服务端强制**：会员/教练的 `scope`、`memberId`、`coachId` 由服务端归一化，
   前端隐藏按钮不算权限；越权一律 403，未登录 401。
3. **课时段不可重叠**：排课与场地预约复用同一冲突规则（SYS-R3），冲突返回 409。
4. **密码不可逆**：BCrypt 存储，不提供明文查看，只能由超级管理员重置并返回新密码。
5. **演示数据可漂移自愈**：`DemoScheduleAligner` 在启动与重置后对齐本周排课并补齐名册；
   它只在"本周无未来排课 / 无占用 / 余位与名单不符"时介入，不覆盖真实数据。

## 常见故障

| 现象 | 原因 | 处理 |
|---|---|---|
| 后端启动即失败：`Port 2798 already in use` | 环境变量 `SERVER__PORT=2798` 被 relaxed binding 读取 | 启动命令显式加 `--server.port=8080` |
| Maven 离线解析失败 | 未指定 settings 文件 | 始终带 `-s E:/dev/maven/conf/settings-dev.xml` |
| 课表 / 场馆占用全空 | 演示课程日期漂移到过去 | 重启后端触发 `DemoScheduleAligner`，或调 `POST /api/admin/reset` |
| 满员课程名单为 0 人 | 种子只写了 `booked_count` 没有 booking 行 | 同上；`alignRoster()` 会补齐并全量重算 |
| `curl` 访问 8080 失败 / 502 | 本机代理注入（端口会变，如 7237） | 加 `--noproxy '*'`；git 用 `-c http.proxy=` |
| 浏览器测试 `Cannot find module 'playwright-core'` | 未设 NODE_PATH | `NODE_PATH=E:/dev/pw/node_modules`，且从 `09-代码实现与迭代` 目录执行 |
| MySQL 日志重定向 Permission denied | 日志目录被占用 | 改写到 `E:/dev/mysql/mysqld-runN.log` |

## Resources

- `references/runbook.md` —— 环境细节、启停、故障排查、端口与进程管理
- `references/test-suite.md` —— 七层测试的范围、用例分组、失败定位方法
- `references/domain-rules.md` —— 角色权限矩阵、状态机、R1–R10 规则、数据口径
- `references/release.md` —— 提交规范、版本标签、推送与答辩演示流程
- `scripts/start-system.sh` —— 启动 MySQL + 后端并等待就绪
- `scripts/run-all-tests.sh` —— 顺序执行七层测试并汇总结果
- `scripts/health-check.js` —— 检查服务存活、登录链路、名单与余位闭合、课表有内容
