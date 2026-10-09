# -*- coding: utf-8 -*-
"""
第 20–23 页（第二轮迭代新增）：
  教练端（第五种角色）/ 场地预约（4 私有 + 1 公共）/ 周课表（课表式管理）/ 账号管理与密码安全
风格沿用 ppt_lib 的学术蓝白规范。
"""
import sys
import os

BASE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, BASE)

from ppt_lib import (blank, rect, text, card, table, conclusion, header, footer, C, W)


def p22e(prs):
    """教练端：第五种角色"""
    sl = blank(prs, C['white'])
    header(sl, '教练端：第五种角色')

    text(sl, 40, 106, 700, 32, '教练可登录，有专属界面，而不是门店后台的一个菜单',
         size=23, bold=True, color=C['deep'], valign='middle')

    items = [
        ('01', '独立身份与入口',
         '登录页第五个身份「教练」；账号 coach1 / coach2 / coach3，登录后进入教练端，左侧只有教练菜单'),
        ('02', '我的课表：只看本人所授课程',
         '按周展示本人课程；门店后台可按教练筛选，但教练账号无论传什么参数都只能看到自己的课'),
        ('03', '学员名单：满员也能查全',
         '选择自己的一门课即可看到全部报名会员（姓名 / 编号 / 状态），解决"满了 0/15 却查不到是谁"的问题'),
        ('04', '核销签到：仅限本人课程',
         '教练可对自己课程的报名会员核销签到；对非本人课程的名单与核销请求一律 403'),
    ]
    y = 148
    for num, title, body in items:
        rect(sl, 40, y, 700, 106, fill=C['bg'], line=C['line'], radius=10)
        rect(sl, 60, y + 30, 46, 46, fill=C['primary'], radius=8)
        text(sl, 60, y + 30, 46, 46, num, size=21, bold=True, color='FFFFFF',
             align='center', valign='middle')
        text(sl, 120, y + 14, 596, 30, title, size=21, bold=True, color=C['deep'],
             valign='middle')
        text(sl, 120, y + 48, 596, 48, body, size=16, color=C['t2'], spacing=1.36)
        y += 116

    card(sl, 764, 106, 476, 452, fill=C['light2'], line=None)
    text(sl, 786, 126, 432, 32, '权限边界（服务端强制）', size=22, bold=True,
         color=C['deep'], valign='middle')
    rows = [
        ('动作', '教练', '门店后台'),
        ('查看本人课表', '✔', '✔（可查全部）'),
        ('查看本人课程学员名单', '✔', '✔'),
        ('核销本人课程签到', '✔', '✔'),
        ('核销他人课程签到', '403', '✔'),
        ('排课 / 改课 / 下架', '403', '✔'),
        ('进入门店后台菜单', '403', '✔'),
    ]
    table(sl, 786, 168, 432, 330, rows, col_w=[210, 100, 150], size=15, header_size=15)

    conclusion(sl, 40, 570, 1200, '判断',
               '角色扩张不能靠前端藏按钮：教练的每一次越权请求都在服务端被拦，并写成了用例', h=68)
    footer(sl, '来源：需求 REQ-B3-004~008 · 集成 IT-066/095/096、浏览器 BT-060~065', 20)
    return sl


def p22f(prs):
    """场地预约：4 个私有场馆 + 1 个公共区域"""
    sl = blank(prs, C['white'])
    header(sl, '场地预约：4 个私有场馆 + 1 个公共区域')

    rows = [
        ('编号', '场馆', '类型', '容量', '时费'),
        ('V01', '私教 A 室', '私有场馆', '1 对 1', '¥80'),
        ('V02', '私教 B 室', '私有场馆', '1 对 1', '¥60'),
        ('V03', '瑜伽小班室', '私有场馆', '8 人', '¥50'),
        ('V04', '力量训练室', '私有场馆', '6 人', '¥40'),
        ('V05', '公共自由训练区', '公共区域', '20 人', '免费'),
    ]
    table(sl, 40, 108, 560, 300, rows, col_w=[80, 200, 120, 90, 80],
          size=17, header_size=17)
    text(sl, 40, 418, 560, 120,
         '展示口径：会员端只看到「可用」场馆；门店后台可调整容量、位置、费用与维护状态，'
         '维护中的场馆立即不可预约。',
         size=17, color=C['t2'], spacing=1.5)

    rules = [
        ('时段不可重叠', '同一场地的任何区间重叠都判冲突（沿用排课的 SYS-R3 思路），返回 409'),
        ('会籍差异化', '私有场馆仅对有效会籍开放；公共区域不校验会籍，任何人可约'),
        ('单次最长 4 小时', '超过上限直接拒绝；结束早于开始、开始时间已过同样拒绝'),
        ('两种发起方', '会员自助（createdBy 为空）与门店代约（记录店员 ID），后台一眼可辨'),
        ('取消与释放', '会员可取消本人的预约，门店可取消任意预约；重复取消返回 409'),
    ]
    y = 108
    for title, body in rules:
        rect(sl, 620, y, 620, 86, fill=C['bg'], line=C['line'], radius=10)
        rect(sl, 620, y, 4, 86, fill=C['primary'])
        text(sl, 642, y + 12, 578, 30, title, size=20, bold=True, color=C['deep'],
             valign='middle')
        text(sl, 642, y + 46, 578, 32, body, size=16, color=C['t2'], spacing=1.3)
        y += 96

    conclusion(sl, 40, 570, 1200, '判断',
               '场地与课程是同一类"时段资源"，所以冲突校验复用同一套规则，不另起炉灶', h=68)
    footer(sl, '来源：需求 REQ-B6-002~007 · 迁移 V11 · 集成 IT-073~090', 21)
    return sl


def p22g(prs):
    """周课表：把课程与场馆管理做成课表"""
    sl = blank(prs, C['white'])
    header(sl, '课表式管理：一张周课表，三种视角')

    views = [
        ('会员', '我的课表', '本人已选课程 + 本人场地预约', C['light']),
        ('教练', '我的课表', '本人所授课程 + 全店场馆占用', C['light']),
        ('店长 / 管理员', '全店课表', '全店课程表 + 场馆占用课表（可按会员 / 教练 / 场馆筛选）', 'FFFFFF'),
    ]
    y = 108
    for role, title, scope, bg in views:
        rect(sl, 40, y, 640, 116, fill=bg, line=C['line'], radius=10)
        rect(sl, 60, y + 18, 120, 34, fill=C['deep'], radius=8)
        text(sl, 60, y + 18, 120, 34, role, size=18, bold=True, color='FFFFFF',
             align='center', valign='middle')
        text(sl, 196, y + 16, 460, 32, title, size=22, bold=True, color=C['deep'],
             valign='middle')
        text(sl, 196, y + 56, 460, 46, scope, size=17, color=C['t2'], spacing=1.35)
        y += 128

    card(sl, 700, 108, 540, 348, fill=C['light2'], line=None)
    text(sl, 722, 128, 496, 32, '关键设计：看到什么由服务端决定', size=22, bold=True,
         color=C['deep'], valign='middle')
    pts = [
        '会员与教练的 scope 参数被服务端改写成 mine，直接构造 URL 也拿不到别人的课表',
        'memberId / coachId 由会话绑定，伪造会被改写为本人',
        '课表是只读聚合视图，条目全部来自各模块的对外契约，不存在"课表与业务数据不一致"',
        '固定返回周一至周日七列，无安排的日期保留为空 —— 一眼看出空档',
        '场馆占用课表只显示占用中的时段，已取消的不再占位',
    ]
    y = 168
    for p in pts:
        text(sl, 722, y, 496, 54, '· ' + p, size=16.5, color=C['t1'], spacing=1.32)
        y += 56

    text(sl, 40, 470, 640, 92,
         '课程管理、场馆管理页面都提供「列表 ⇄ 周课表」切换：\n'
         '列表适合逐条维护，课表适合看全局空档；课表里的课程卡片可直接点进编辑。',
         size=18, color=C['t1'], spacing=1.5)

    conclusion(sl, 40, 570, 1200, '判断',
               '课表不是新做的表，而是把已有的课程与场馆预约聚合成一张周视图，因此不会数据不一致', h=68)
    footer(sl, '来源：需求 REQ-B3-007/008、REQ-B6-007、REQ-M0-010 · 单元 TT-01~11、集成 IT-113~128', 22)
    return sl


def p22h(prs):
    """账号管理与"为什么看不到明文密码" """
    sl = blank(prs, C['white'])
    header(sl, '超级管理员：账号管理与密码安全')

    card(sl, 40, 108, 580, 300, fill=C['bg'], line=C['line'])
    text(sl, 62, 128, 536, 32, '新增最高权限角色 superadmin', size=23, bold=True,
         color=C['deep'], valign='middle')
    caps = [
        '查看全部账号：用户名、姓名、角色、数据归属、状态、密码状态',
        '重置任意账号密码 → 重置为默认密码并当场返回新密码',
        '启用 / 停用账号（不能停用自己，也不能停用其他超管）',
        '全部操作写入审计日志',
    ]
    y = 172
    for c in caps:
        text(sl, 62, y, 536, 52, '· ' + c, size=17, color=C['t1'], spacing=1.35)
        y += 56

    perm = [
        ('访问账号接口', '超级管理员', '管理员 / 店长 / 会员', '未登录'),
        ('结果', '200，返回账号列表', '403', '401'),
    ]
    table(sl, 62, 322, 536, 70, perm, col_w=[120, 200, 170, 90], size=15, header_size=15)

    card(sl, 640, 108, 600, 300, fill='FFF8E7', line='F0D9A8')
    rect(sl, 640, 108, 4, 300, fill='C9761A')
    text(sl, 662, 126, 556, 34, '为什么看不到"所有人的明文密码"', size=22, bold=True,
         color='8A5A12', valign='middle')
    text(sl, 662, 168, 556, 226,
         '密码以 BCrypt 哈希存储，单向不可逆 —— 系统没有、也不可能有"查看明文密码"的接口。\n\n'
         '这不是功能缺失，而是安全底线：一旦密码可明文导出，一处泄露就等于全站失守，\n'
         '而且谁看过谁也说不清。\n\n'
         '正确做法是「重置」：超管重置某账号后，系统把新密码返回给超管，\n'
         '管理员始终掌握可登录凭据，同时不破坏密码存储的安全前提。',
         size=17, color='5C4212', spacing=1.42)

    conclusion(sl, 40, 424, 1200, '取舍',
               '宁可少一个"看起来很方便"的功能，也不让全站密码变成可导出的明文', h=68)

    text(sl, 40, 508, 1200, 46,
         '演示账号：superadmin / 123456（超级管理员）　manager / 123456（店长）　'
         'admin / 123456（管理员）　member1 / 123456（会员）　coach1 / 123456（教练）',
         size=17, color=C['t2'], valign='middle')
    footer(sl, '来源：需求 REQ-M0-006~009 · 迁移 V12 · 集成 IT-097~112、浏览器 BT-098~104', 23)
    return sl
