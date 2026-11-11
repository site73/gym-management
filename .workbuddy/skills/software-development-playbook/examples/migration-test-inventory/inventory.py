"""
库存管理最小实现（迁移验证用）
技术栈：Python 3.13 标准库 + SQLite（与源项目的 Spring Boot + MySQL 完全不同）

提供的三种实现，用于对照验证：
  deduct_naive   —— 先查再改（read-modify-write），典型超卖写法
  deduct_cas     —— 条件更新（compare-and-set），DB 原子保证不超卖
  inbound_idem   —— 幂等入库（幂等键 + 唯一索引）
  reconcile      —— 对账（qty == 期初 + sum(流水)）
"""
import sqlite3
import threading
import time
import uuid

DB = None

RETRY_MAX = 6


def connect(db_path):
    conn = sqlite3.connect(db_path, timeout=30, isolation_level=None)
    conn.execute("PRAGMA busy_timeout=30000")
    conn.execute("PRAGMA foreign_keys=ON")
    return conn


def retry_write(fn):
    """SQLite 单写者：遇到锁冲突时退避重试（真实系统里对应重试/排队策略）。"""
    def wrapper(*args, **kwargs):
        delay = 0.02
        last = None
        for _ in range(RETRY_MAX):
            try:
                return fn(*args, **kwargs)
            except sqlite3.OperationalError as e:
                msg = str(e).lower()
                if "locked" in msg or "busy" in msg:
                    last = e
                    time.sleep(delay)
                    delay = min(delay * 2, 0.5)
                    continue
                raise
        raise last
    return wrapper


def init_db(db_path):
    conn = sqlite3.connect(db_path, timeout=30, isolation_level=None)
    conn.execute("PRAGMA journal_mode=DELETE")  # 不用 WAL：避免 -shm 并发初始化失败
    conn.execute("PRAGMA busy_timeout=30000")
    conn.executescript(
        """
        DROP TABLE IF EXISTS stock_flow;
        DROP TABLE IF EXISTS idem_key;
        DROP TABLE IF EXISTS sku;

        CREATE TABLE sku (
            id      INTEGER PRIMARY KEY,
            code    TEXT NOT NULL UNIQUE,
            name    TEXT NOT NULL,
            qty     INTEGER NOT NULL CHECK (qty >= 0),   -- 最后一道防线：库层禁止负库存
            opening INTEGER NOT NULL DEFAULT 0           -- 期初数量，用于对账
        );

        CREATE TABLE stock_flow (
            id         INTEGER PRIMARY KEY AUTOINCREMENT,
            sku_id     INTEGER NOT NULL REFERENCES sku(id),
            biz_type   TEXT NOT NULL,          -- inbound / outbound / adjust
            biz_no     TEXT NOT NULL,          -- 业务单号（幂等键来源）
            delta      INTEGER NOT NULL,       -- 正数入库，负数出库
            created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
        );

        CREATE TABLE idem_key (
            key        TEXT PRIMARY KEY,
            result     TEXT NOT NULL,
            created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
        );
        """
    )
    conn.close()


# ---------------- 入库（幂等） ----------------

@retry_write
def inbound_idem(db_path, sku_id, biz_no, qty):
    """幂等入库：同一 biz_no 重复提交，只入账一次。返回 (是否首次, 结果说明)"""
    key = f"INBOUND:{biz_no}"
    conn = connect(db_path)
    try:
        conn.execute("BEGIN IMMEDIATE")
        try:
            conn.execute("INSERT INTO idem_key(key, result) VALUES(?, ?)", (key, "processing"))
        except sqlite3.IntegrityError:
            conn.execute("ROLLBACK")
            return False, "重复提交，已忽略"
        conn.execute(
            "INSERT INTO stock_flow(sku_id, biz_type, biz_no, delta) VALUES(?,?,?,?)",
            (sku_id, "inbound", biz_no, qty),
        )
        conn.execute("UPDATE sku SET qty = qty + ? WHERE id = ?", (qty, sku_id))
        conn.execute("UPDATE idem_key SET result=? WHERE key=?", ("done", key))
        conn.execute("COMMIT")
        return True, "入库成功"
    finally:
        conn.close()


# ---------------- 出库：错误实现 ----------------

def deduct_naive(db_path, sku_id, qty, settle=0.01):
    """先查再改（read-modify-write）：把"读到的值 - qty"作为绝对值写回。
    settle 模拟真实业务在查与改之间的处理耗时，放大竞态窗口。
    """
    conn = connect(db_path)
    try:
        cur = conn.execute("SELECT qty FROM sku WHERE id = ?", (sku_id,))
        row = cur.fetchone()
        if row is None:
            return False, "SKU 不存在"
        if row[0] < qty:
            return False, "库存不足"
        target = row[0] - qty                   # ← 竞态根源：基于过期快照计算
        time.sleep(settle)                      # ← 真实系统里这里是别的校验/远程调用
        try:
            conn.execute("UPDATE sku SET qty = ? WHERE id = ?", (target, sku_id))
            conn.execute(
                "INSERT INTO stock_flow(sku_id, biz_type, biz_no, delta) VALUES(?,?,?,?)",
                (sku_id, "outbound", "N" + uuid.uuid4().hex[:8], -qty),
            )
        except sqlite3.IntegrityError:
            return False, "库存不足（库层约束）"
        return True, "出库成功"
    finally:
        conn.close()


# ---------------- 出库：正确实现 ----------------

@retry_write
def deduct_cas(db_path, sku_id, qty):
    """条件更新：一条语句内判断并扣减，影响行数为 0 即失败。"""
    conn = connect(db_path)
    try:
        conn.execute("BEGIN IMMEDIATE")
        cur = conn.execute(
            "UPDATE sku SET qty = qty - ? WHERE id = ? AND qty >= ?", (qty, sku_id, qty)
        )
        if cur.rowcount != 1:
            conn.execute("ROLLBACK")
            return False, "库存不足"
        conn.execute(
            "INSERT INTO stock_flow(sku_id, biz_type, biz_no, delta) VALUES(?,?,?,?)",
            (sku_id, "outbound", "C" + uuid.uuid4().hex[:8], -qty),
        )
        conn.execute("COMMIT")
        return True, "出库成功"
    finally:
        conn.close()


# ---------------- 对账 ----------------

def reconcile(db_path):
    """逐 SKU 对账：qty 必须等于 期初 + 流水合计。返回差异列表。"""
    conn = connect(db_path)
    try:
        diffs = []
        rows = conn.execute(
            """
            SELECT s.id, s.code, s.qty, s.opening,
                   COALESCE((SELECT SUM(delta) FROM stock_flow f WHERE f.sku_id = s.id), 0) AS flows
            FROM sku s ORDER BY s.id
            """
        ).fetchall()
        for sku_id, code, qty, opening, flows in rows:
            expected = opening + flows
            if qty != expected:
                diffs.append(
                    {"sku": code, "qty": qty, "opening": opening, "flows": flows, "expected": expected}
                )
        return diffs, len(rows)
    finally:
        conn.close()


# ---------------- 并发驱动 ----------------

def run_concurrent(fn, db_path, sku_id, qty, threads):
    """并发执行 fn，返回 (成功数, 失败数, 异常数, 最终库存)"""
    ok = 0
    fail = 0
    err = 0
    lock = threading.Lock()
    barrier = threading.Barrier(threads)

    def worker():
        nonlocal ok, fail, err
        try:
            barrier.wait()
            success, _ = fn(db_path, sku_id, qty)
            with lock:
                if success:
                    ok += 1
                else:
                    fail += 1
        except Exception:
            with lock:
                err += 1

    ts = [threading.Thread(target=worker) for _ in range(threads)]
    for t in ts:
        t.start()
    for t in ts:
        t.join()

    conn = connect(db_path)
    final = conn.execute("SELECT qty FROM sku WHERE id = ?", (sku_id,)).fetchone()[0]
    conn.close()
    return ok, fail, err, final
