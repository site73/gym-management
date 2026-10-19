#!/usr/bin/env bash
# 顺序执行七层测试并汇总
# 用法：bash .workbuddy/skills/gym-ops-system/scripts/run-all-tests.sh
# 前置：MySQL 与后端已启动（见 start-system.sh），且已 mvn package

set -u
PROJ="E:/WorkBuddy/健身房管理业务"
CODE="$PROJ/09-代码实现与迭代"
BACKEND="$PROJ/08-系统结构与代码设计/backend"
NODE="C:/Users/MSI_NB/.workbuddy/binaries/node/versions/22.22.2-6/node.exe"
export JAVA_HOME="E:/dev/jdk-17"
export PATH="$JAVA_HOME/bin:$PATH"
export NODE_PATH="E:/dev/pw/node_modules"

pass=0; fail=0; line() { printf '%s\n' "------------------------------------------------------------"; }

run() {  # run <名称> <命令...>
  local name="$1"; shift
  line
  echo "▸ $name"
  if "$@"; then echo "  ✅ $name"; pass=$((pass+1)); else echo "  ❌ $name"; fail=$((fail+1)); fi
}

run "Java 单元/架构/BDD" bash -c "cd '$BACKEND' && 'E:/dev/maven/bin/mvn' -o -s E:/dev/maven/conf/settings-dev.xml test -q"
run "集成测试"        bash -c "cd '$CODE' && '$NODE' verify/integration-test.js"
run "前端静态检查"    bash -c "cd '$CODE' && '$NODE' verify/frontend-check.js"
run "二维码结构校验"  bash -c "cd '$CODE' && '$NODE' verify/qr-check.js"
run "真实浏览器测试"  bash -c "cd '$CODE' && NODE_PATH='E:/dev/pw/node_modules' '$NODE' verify/browser-test.js"
run "Node BDD"        bash -c "cd '$CODE' && '$NODE' verify/run.js"
run "接口冒烟"        bash -c "cd '$CODE' && '$NODE' app/smoke.js"

line
echo "汇总：通过 $pass 层 / 失败 $fail 层"
line
[ "$fail" -eq 0 ] || exit 1
