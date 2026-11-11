"""
迁移验证执行器：只依赖 inventory.py 与标准库。
依次验证：超卖复现 / CAS 修复 / 幂等入库 / 对账。
输出 PASS/FAIL，并生成 report.md。
"""
import os
import sqlite3
import threading
import uuid

import inventory as inv

HERE = os.path.dirname(os.path.abspath(__file__))
DB = os.path.join(HERE, "inventory.db")
lines = []
passed = 0
failed = 0


def log(s=""):
    print(s)
    lines.append(s)


def check(name, cond, detail):
    global passed, failed
    if cond:
        passed += 1
        log(f"PASS  {name}  | {detail}")
    else:
        failed += 1
        log(f"FAIL  {name}  | {detail}")


def fresh(qty=100):
    inv.init_db(DB)
    conn = inv.connect(DB)
    conn.execute(
        "INSERT INTO sku(id, code, name, qty, opening) VALUES(1,'SKU-001','测试商品',?,?)",
        (qty, qty),
    )
    conn.close()


def main():
    log("=" * 62)
    log(" 库存管理 · 迁移验证（Python 3.13 + SQLite，与源项目技术栈无关）")
    log("=" * 62)

    # ---------- 1. 复现超卖：先查再改 ----------
    log("\n[1] 并发扣减 · 错误实现（先查再改）")
    fresh(100)
    ok, fail, err, final = inv.run_concurrent(inv.deduct_naive, DB, 1, 10, 20)
    sold = ok * 10
    log(f"    20 线程各扣 10；成功 {ok} 次（卖出 {sold} 件），失败 {fail}，异常 {err}，最终库存 {final}")
    check(
        "能复现超卖（卖出量 > 实际库存）",
        sold > 100,
        f"初始 100 件，卖出 {sold} 件，账面剩余 {final}",
    )
    check(
        "账面与台账不符（对账能发现）",
        len(inv.reconcile(DB)[0]) > 0,
        f"差异 {len(inv.reconcile(DB)[0])} 个 SKU",
    )

    # ---------- 2. 正确实现：条件更新 ----------
    log("\n[2] 并发扣减 · 正确实现（条件更新 / CAS）")
    fresh(100)
    ok, fail, err, final = inv.run_concurrent(inv.deduct_cas, DB, 1, 10, 20)
    log(f"    20 线程各扣 10；成功 {ok} 次，失败 {fail}，异常 {err}，最终库存 {final}")
    check("成功次数 = 可扣次数（10）", ok == 10, f"成功 {ok} 次")
    check("最终库存归零且不为负", final == 0, f"最终 {final}")
    check("失败请求有明确结果", fail == 10 and err == 0, f"失败 {fail}、异常 {err}")
    check("对账无差异", len(inv.reconcile(DB)[0]) == 0, "0 个 SKU 有差异")

    # ---------- 3. 幂等入库 ----------
    log("\n[3] 幂等入库")
    fresh(0)
    biz = "PO-" + uuid.uuid4().hex[:8].upper()
    results = [inv.inbound_idem(DB, 1, biz, 10) for _ in range(5)]
    first = sum(1 for r in results if r[0])
    conn = inv.connect(DB)
    qty = conn.execute("SELECT qty FROM sku WHERE id=1").fetchone()[0]
    flows = conn.execute("SELECT COUNT(*) FROM stock_flow WHERE biz_no=?", (biz,)).fetchone()[0]
    conn.close()
    log(f"    同一单号串行提交 5 次：首次入账 {first} 次，库存 {qty}，流水 {flows} 条")
    check("串行重复提交只入账一次", first == 1 and qty == 10 and flows == 1,
          f"首次 {first} 次 / 库存 {qty} / 流水 {flows}")

    fresh(0)
    biz2 = "PO-" + uuid.uuid4().hex[:8].upper()
    okc = []
    barrier = threading.Barrier(10)
    lock = threading.Lock()

    def worker():
        barrier.wait()
        r = inv.inbound_idem(DB, 1, biz2, 10)
        with lock:
            okc.append(r[0])
    ts = [threading.Thread(target=worker) for _ in range(10)]
    for t in ts:
        t.start()
    for t in ts:
        t.join()
    conn = inv.connect(DB)
    qty2 = conn.execute("SELECT qty FROM sku WHERE id=1").fetchone()[0]
    flows2 = conn.execute("SELECT COUNT(*) FROM stock_flow WHERE biz_no=?", (biz2,)).fetchone()[0]
    conn.close()
    log(f"    同一单号并发提交 10 次：首次入账 {sum(okc)} 次，库存 {qty2}，流水 {flows2} 条")
    check("并发重复提交只入账一次", sum(okc) == 1 and qty2 == 10 and flows2 == 1,
          f"首次 {sum(okc)} 次 / 库存 {qty2} / 流水 {flows2}")

    # ---------- 4. 对账 ----------
    log("\n[4] 对账（期初 + 入 − 出 = 期末）")
    fresh(100)
    inv.inbound_idem(DB, 1, "PO-A", 50)
    inv.inbound_idem(DB, 1, "PO-B", 30)
    inv.deduct_cas(DB, 1, 20)
    inv.deduct_cas(DB, 1, 15)
    diffs, total = inv.reconcile(DB)
    conn = inv.connect(DB)
    q = conn.execute("SELECT qty, opening FROM sku WHERE id=1").fetchone()
    conn.close()
    log(f"    期初 {q[1]} + 入 80 − 出 35 = 期末 {q[0]}；差异 {len(diffs)} 个 SKU")
    check("对账平衡", len(diffs) == 0 and q[0] == 145, f"期末 {q[0]}（期望 145）")

    # 故意制造不一致，验证对账能发现
    conn = inv.connect(DB)
    conn.execute("UPDATE sku SET qty = qty - 5 WHERE id=1")
    conn.close()
    diffs2, _ = inv.reconcile(DB)
    log(f"    故意改账面（-5，不写流水）：对账发现差异 {len(diffs2)} 个，"
        f"qty={diffs2[0]['qty']} expected={diffs2[0]['expected']}" if diffs2 else "    未发现差异")
    check("对账能定位被篡改的账面", len(diffs2) == 1 and diffs2[0]["expected"] == 145,
          f"差异 {len(diffs2)} 个，期望值 {diffs2[0]['expected'] if diffs2 else '-'}")

    log("\n" + "-" * 62)
    log(f" 结果：通过 {passed} / 失败 {failed} / 共 {passed + failed}")
    log("-" * 62)

    with open(os.path.join(HERE, "report.md"), "w", encoding="utf-8") as f:
        f.write("# 库存系统迁移验证报告\n\n")
        f.write("- 技术栈：Python 3.13 + SQLite（与源项目 Spring Boot + MySQL 无关）\n")
        f.write(f"- 结果：**通过 {passed} / 失败 {failed}**\n\n```\n")
        f.write("\n".join(lines))
        f.write("\n```\n")

    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
