# 运行手册（环境 / 启停 / 排障）

## 1. 环境事实

| 组件 | 位置 | 端口 |
|---|---|---|
| JDK 17 | `E:/dev/jdk-17` | — |
| Maven 3.9 | `E:/dev/maven/bin/mvn` + `-s E:/dev/maven/conf/settings-dev.xml` | — |
| 本地仓库 | `E:/dev/m2repo`（离线可用） | — |
| MySQL 8.0 | `E:/dev/mysql`，`my.ini` 指定的 datadir | 3307 |
| 数据库 | `gym`，账号 `gym_app / gym_app_pwd` | — |
| 后端 | `08-系统结构与代码设计/backend/target/gym-backend-1.0.0.jar` | 8080 |
| Playwright-core | `E:/dev/pw/node_modules` | — |
| 浏览器 | `C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe` | — |

无 LibreOffice：PPT/文档预览用 Playwright 截图或 `build/main.py` 的版面自检。

## 2. 启动

```bash
# MySQL（后台常驻，日志不要写到 logs/，目录可能被占用）
cd E:/dev/mysql/bin
./mysqld.exe --defaults-file="E:/dev/mysql/my.ini" > ../mysqld-run3.log 2>&1 &

# 等端口
until netstat -ano | grep -q ':3307.*LISTENING'; do sleep 2; done

# 后端
cd E:/WorkBuddy/健身房管理业务/08-系统结构与代码设计/backend
unset SERVER__PORT
"E:/dev/jdk-17/bin/java.exe" -jar target/gym-backend-1.0.0.jar \
  --spring.profiles.active=mysql --server.port=8080
```

启动成功的判据：

- 端口监听：`netstat -ano | grep ':8080.*LISTENING'`
- 日志出现 `Started GymApplication`
- Flyway 日志 `Successfully applied N migration` 或 `Schema is up to date`
- `[demo] 演示数据已对齐` （有需要修复的演示数据时）

## 3. 停止

```bash
taskkill //IM java.exe //F
taskkill //IM mysqld.exe //F
```

只停单个进程时先 `netstat -ano | grep :8080` 拿 PID，再 `taskkill //PID <pid> //F`。

## 4. 构建

```bash
export JAVA_HOME=E:/dev/jdk-17 && export PATH="$JAVA_HOME/bin:$PATH"
"E:/dev/maven/bin/mvn" -o -s E:/dev/maven/conf/settings-dev.xml test        # 只测试
"E:/dev/maven/bin/mvn" -o -s E:/dev/maven/conf/settings-dev.xml -DskipTests package
```

`repackage` 报 `Unable to rename ... .jar` = jar 正被运行中的后端占用，先停进程再打包。

## 5. 已知坑

1. **SERVER__PORT=2798**：环境里预设了该变量，Spring 会把它当 `server.port`，导致抢占 CodeBuddy 自身端口并启动失败。必须显式 `--server.port=8080`，必要时 `unset SERVER__PORT`。
2. **代理**：`HTTP_PROXY/HTTPS_PROXY` 指向本机代理，端口会变（2467 / 7237 都出现过），且可能返回 502。
   - 本地接口测试加 `--noproxy '*'`
   - git 推送用 `git -c http.proxy= -c https.proxy= push`
   - GitHub 直连也可能超时，属网络环境问题，代码本身无影响
3. **后台进程**：用工具的 `run_in_background` 直接跑 `mysqld/java`；用 `start-system.js` 拉起的子进程会随启动器退出被回收。
4. **Maven 必须带 `-s`**：镜像 id 与 `_remote.repositories` 必须匹配，纯 `-o` 会报 "present but unavailable"。
5. **Flyway R__ 种子不会每次重跑**：只在内容变化时执行，所以种子里的 `CURDATE()` 会漂移，靠 `DemoScheduleAligner` 自愈。
6. **前端改动需重新打包**：`index.html` 在 jar 的 `classpath:/static` 里，改完要 `package` 并重启。

## 6. 数据复位

```bash
# 方式一：接口（管理员令牌）
curl --noproxy '*' -X POST http://127.0.0.1:8080/api/admin/reset -H "X-Token: <admin>"

# 方式二：清库重建（会触发 V1–V13 全量迁移）
mysql -ugym_app -pgym_app_pwd -P3307 -h127.0.0.1 -e "drop database gym; create database gym;"
```

复位后 `DemoScheduleAligner` 会自动对齐本周排课、补齐演示名册并重算余位。
