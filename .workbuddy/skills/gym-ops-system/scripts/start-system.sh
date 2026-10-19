#!/usr/bin/env bash
# 一键启动：MySQL(3307) → 后端(8080)，并等待就绪
# 用法：bash .workbuddy/skills/gym-ops-system/scripts/start-system.sh
#
# 说明：脚本内用 nohup 拉起进程，适合人工执行；
#       在 WorkBuddy 中请改用 run_in_background 直接运行 mysqld/java，
#       否则进程可能随 shell 退出被回收。

set -u
PROJ="E:/WorkBuddy/健身房管理业务"
BACKEND="$PROJ/08-系统结构与代码设计/backend"
JAVA="E:/dev/jdk-17/bin/java.exe"
MYSQLD="E:/dev/mysql/bin/mysqld.exe"
MY_INI="E:/dev/mysql/my.ini"
JAR="$BACKEND/target/gym-backend-1.0.0.jar"

port_listening() { netstat -ano | grep -q ":$1 .*LISTENING"; }

echo "==> 1/3 检查 MySQL"
if port_listening 3307; then
  echo "    MySQL 已在 3307 运行"
else
  ( cd E:/dev/mysql/bin && nohup "$MYSQLD" --defaults-file="$MY_INI" > ../mysqld-run.log 2>&1 & )
  for i in $(seq 1 30); do port_listening 3307 && break; sleep 2; done
  port_listening 3307 || { echo "    MySQL 启动失败，查看 E:/dev/mysql/mysqld-run.log"; exit 1; }
  echo "    MySQL 就绪"
fi

echo "==> 2/3 检查后端 jar"
[ -f "$JAR" ] || { echo "    缺少 $JAR，请先执行 mvn package"; exit 1; }

if port_listening 8080; then
  echo "    后端已在 8080 运行"
else
  unset SERVER__PORT
  ( cd "$BACKEND" && nohup "$JAVA" -jar "$JAR" --spring.profiles.active=mysql --server.port=8080 \
      > logs/gym-backend.log 2>&1 & )
  for i in $(seq 1 45); do port_listening 8080 && break; sleep 2; done
  port_listening 8080 || { echo "    后端启动失败，查看 $BACKEND/logs/gym-backend.log"; exit 1; }
  echo "    后端就绪"
fi

echo "==> 3/3 完成"
cat <<'EOF'
    页面：http://127.0.0.1:8080/
    账号（密码均为 123456）：
      member1     会员
      coach1      教练
      manager     店长
      admin       管理员
      superadmin  超级管理员
    停止：taskkill /IM java.exe /F && taskkill /IM mysqld.exe /F
EOF
