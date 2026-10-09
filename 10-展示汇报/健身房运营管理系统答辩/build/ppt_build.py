# -*- coding: utf-8 -*-
"""健身房运营管理系统 · 课程答辩 PPT（22 页，学术风）—— 第 1–11 页"""
from pptx import Presentation
from ppt_lib import (W, H, TOTAL, E, fs, C, FONT, blank, rect, text, picture,
                     icon, header, footer, card, table, conclusion)


def p01(prs):
    """封面"""
    sl = blank(prs, C['white'])
    rect(sl, 0, 0, W, 14, fill=C['primary'])
    rect(sl, 0, 14, W, 6, fill=C['blue'])
    text(sl, 140, 60, 1000, 34, '课程大作业答辩', size=24, color=C['blue'],
         align='center', valign='middle')
    text(sl, 60, 104, 1160, 100, '健身房运营管理系统', size=72, bold=True,
         color=C['deep'], align='center', valign='middle')
    text(sl, 60, 210, 1160, 46, 'Gym Operation Management System', size=30,
         color=C['t2'], align='center', valign='middle')
    rect(sl, 550, 274, 180, 4, fill=C['primary'])
    text(sl, 140, 296, 1000, 78,
         '从业务梳理到可运行系统：需求可追溯 · 系统可运行 · 质量可复现',
         size=23, color=C['t1'], align='center', valign='middle', spacing=1.5)
    rect(sl, 0, 490, W, 230, fill=C['light2'])
    text(sl, 300, 528, 340, 34, '汇报人：＿＿＿＿＿＿', size=20, color=C['t1'], valign='middle')
    text(sl, 660, 528, 340, 34, '学号：＿＿＿＿＿＿', size=20, color=C['t1'], valign='middle')
    text(sl, 300, 576, 340, 34, '指导教师：＿＿＿＿＿＿', size=20, color=C['t1'], valign='middle')
    text(sl, 660, 576, 340, 34, '日期：2026 年 10 月', size=20, color=C['t1'], valign='middle')
    text(sl, 140, 640, 1000, 30, '技术栈：Spring Boot 3 · MySQL 8.0 · 174 项自动化检查',
         size=15, color=C['t3'], align='center', valign='middle')
    return sl


def p02(prs):
    """目录"""
    sl = blank(prs, C['white'])
    rect(sl, 0, 0, 380, H, fill=C['deep'])
    text(sl, 48, 250, 290, 70, '目录', size=54, bold=True, color='FFFFFF', valign='middle')
    text(sl, 48, 320, 290, 34, 'CONTENTS', size=22, color=C['onDeep'], valign='middle')
    rect(sl, 48, 362, 88, 4, fill=C['yellow'])
    text(sl, 48, 388, 292, 110,
         '五个部分，从业务理解一路讲到测试证据与后续规划',
         size=19, color=C['onDeep'], spacing=1.6)
    rows = [
        ('01', '业务理解与需求分析', '业务定位 → 业务基线 → 流程优化 → 需求追溯（第 3–6 页）', C['light']),
        ('02', '系统设计与架构', '模块边界 → 数据设计 → 工程环境（第 7–10 页）', C['bg']),
        ('03', '关键实现与业务规则', '规则外置 → 角色鉴权 → 约课主链路 → 两项创新点（第 11–16 页）', C['bg']),
        ('04', '质量保障与测试', '六层 174 项检查 → 缺陷根因与修复（第 17–19 页）', C['bg']),
        ('05', '总结与展望', '已完成 / 未完成 / 下一步（第 20–21 页）', C['bg']),
    ]
    y = 121
    for num, title, desc, bg in rows:
        rect(sl, 428, y, 812, 86, fill=bg, radius=10)
        rect(sl, 448, y + 20, 46, 46, fill=C['primary'], radius=8)
        text(sl, 448, y + 20, 46, 46, num, size=22, bold=True, color='FFFFFF',
             align='center', valign='middle')
        text(sl, 514, y + 14, 700, 34, title, size=25, bold=True,
             color=C['deep'], valign='middle')
        text(sl, 514, y + 48, 700, 26, desc, size=17, color=C['t2'], valign='middle')
        y += 100
    return sl


def p03(prs):
    """章节扉页 01"""
    sl = blank(prs, C['bg'])
    text(sl, 0, 130, W, 320, '01', size=240, bold=True, color=C['light'],
         align='center', valign='middle')
    text(sl, 140, 240, 1000, 36, '第一部分', size=22, color=C['blue'],
         align='center', valign='middle')
    text(sl, 140, 282, 1000, 86, '业务理解与需求分析', size=54, bold=True,
         color=C['deep'], align='center', valign='middle')
    rect(sl, 580, 378, 120, 4, fill=C['primary'])
    text(sl, 180, 400, 920, 60,
         '先回答"这家健身房是怎么运转的"，再回答"系统要做什么"——需求来自业务基线，而不是功能清单',
         size=21, color=C['t2'], align='center', valign='middle', spacing=1.5)
    labels = ['业务定位', '业务基线', '流程梳理', '需求追溯']
    x = 424
    for lb in labels:
        rect(sl, x, 486, 108, 40, fill='FFFFFF', line=C['line'], radius=20)
        text(sl, x, 486, 108, 40, lb, size=19, color=C['t1'], align='center', valign='middle')
        x += 122
    footer(sl, '健身房运营管理系统 · 课程答辩', 3)
    return sl


def p04(prs):
    """为什么要重做这套系统"""
    sl = blank(prs, C['white'])
    header(sl, '为什么要重做这套系统')
    pains = [
        ('排课靠电话和微信群：高峰期漏记、重复记，改一次课要通知一圈人'),
        ('爽约没有任何代价：名额被占着不来，真正想练的人反而约不上'),
        ('会籍到期没人盯：续费全靠店员记性，会员流失了才被发现'),
        ('课消与收费对不上：月底靠 Excel 手工核，差错出现后无法追溯'),
    ]
    text(sl, 40, 108, 720, 34, '门店里真实存在的四类麻烦', size=24, bold=True,
         color=C['deep'], valign='middle')
    y = 152
    for s in pains:
        rect(sl, 40, y, 720, 88, fill=C['bg'], line=C['line'], radius=10)
        rect(sl, 56, y + 30, 28, 28, fill=C['red'], radius=14)
        text(sl, 98, y, 646, 88, s, size=22, color=C['t1'], valign='middle', spacing=1.5)
        y += 98
    card(sl, 780, 108, 460, 200, fill=C['light'], line=None)
    text(sl, 802, 128, 416, 34, '共同缺口', size=24, bold=True, color=C['deep'], valign='middle')
    text(sl, 802, 170, 416, 120,
         '四件事各自记录、彼此不通：约课、签到、课消、收费之间没有闭环',
         size=22, color=C['t1'], spacing=1.55)
    card(sl, 780, 320, 460, 200, fill='FFFFFF', line=C['line'])
    text(sl, 802, 340, 416, 34, '所以目标不是"做个 App"', size=24, bold=True,
         color=C['deep'], valign='middle')
    text(sl, 802, 382, 416, 120,
         '而是把 预约 → 到场 → 课消 → 收费 串成一条可查、可约束、可预警的链路',
         size=22, color=C['t2'], spacing=1.55)
    conclusion(sl, 40, 556, 1200, '结论', '要先补齐"约课与课消之间的闭环"，而不是先堆功能')
    footer(sl, '来源：业务分析报告 · 业务基线 V1.0（项目真实文档）', 4)
    return sl


def p05(prs):
    """业务理解方法（Hero）"""
    sl = blank(prs, C['white'])
    text(sl, 48, 62, 620, 32, '业务理解方法', size=20, color=C['blue'], valign='middle')
    text(sl, 48, 96, 620, 60, '先把业务讲清楚，再谈系统', size=40, bold=True,
         color=C['deep'], valign='middle')
    rect(sl, 48, 164, 100, 4, fill=C['primary'])
    text(sl, 48, 184, 612, 240,
         '我们没有一上来就列功能，而是先做业务定位：明确这家健身房靠什么赚钱、会员从进门到上课要经过哪些环节。'
         '随后把现状写成业务基线（V0.1 → 确认版 V1.0），再用流程优化说明找出"哪些环节该由系统承接、哪些仍由人来做"，'
         '最后才产出需求。这样做的好处很直接——每一条需求都能回答"它来自哪个业务环节"。',
         size=22, color=C['t1'], spacing=1.55)
    labels = ['① 业务定位', '② 业务基线', '③ 流程优化', '④ 人机分工']
    x = 48
    for lb in labels:
        rect(sl, x, 436, 138, 40, fill=C['light'], radius=8)
        text(sl, x, 436, 138, 40, lb, size=18, bold=True, color=C['primary'],
             align='center', valign='middle')
        x += 148
    conclusion(sl, 48, 500, 612, '判断', '先有业务基线，再有需求；需求再向下追溯到规则与测试', h=76)
    rect(sl, 700, 0, 580, 652, fill=C['light2'])
    picture(sl, 'assets/b0_flow.png', 720, 28, 540, 570, mode='contain')
    text(sl, 700, 612, 580, 30, '图：B0 总体业务流程图（项目真实产出）', size=15,
         color=C['t3'], align='center', valign='middle')
    footer(sl, '来源：业务基线 V1.0 · 业务流程优化说明', 5)
    return sl


def p06(prs):
    """需求追溯矩阵"""
    sl = blank(prs, C['white'])
    header(sl, '需求不是清单，是可追溯的链条')
    data = [
        ['业务目标', '系统需求', '关键规则', '验收测试'],
        ['会员只能约有效会籍的课', 'REQ-B4-001', 'SYS-R1 / SYS-R2', 'booking.feature'],
        ['课程不满员且无时间冲突', 'REQ-B4-001', 'SYS-R3', 'booking.feature'],
        ['约课取消必须释放名额', 'REQ-B4-002', 'SYS-R3', 'attendance.feature'],
        ['累计爽约要有限制', 'REQ-B4-003', 'SYS-R4', 'attendance.feature'],
        ['会籍到期要有人提醒', 'REQ-B5-003', 'SYS-R6', 'membership.feature'],
        ['流失与爽约要能预警', 'REQ-B4-006', 'SYS-R7 / R8 / R9', 'warning.feature'],
    ]
    table(sl, 40, 112, 1200, 372, data, col_w=[380, 190, 300, 330],
          size=17, header_size=18, zebra=C['bg'])
    conclusion(sl, 40, 512, 1200, '作用',
               '任一需求都能回答三件事：为什么做、由哪条规则约束、用哪个用例验收')
    footer(sl, '来源：业务—系统需求追溯矩阵 · 关键规则—需求—测试溯源表', 6)
    return sl


def p07(prs):
    """章节扉页 02"""
    sl = blank(prs, C['bg'])
    text(sl, 0, 130, W, 320, '02', size=240, bold=True, color=C['light'],
         align='center', valign='middle')
    text(sl, 140, 240, 1000, 36, '第二部分', size=22, color=C['blue'],
         align='center', valign='middle')
    text(sl, 140, 282, 1000, 86, '系统设计与架构', size=54, bold=True,
         color=C['deep'], align='center', valign='middle')
    rect(sl, 580, 378, 120, 4, fill=C['primary'])
    text(sl, 200, 400, 880, 60,
         '把业务规则放进确定的模块边界里：每一层只依赖契约，不依赖实现',
         size=21, color=C['t2'], align='center', valign='middle', spacing=1.5)
    labels = ['模块边界', '接口契约', '数据设计', '工程环境']
    x = 424
    for lb in labels:
        rect(sl, x, 486, 108, 40, fill='FFFFFF', line=C['line'], radius=20)
        text(sl, x, 486, 108, 40, lb, size=19, color=C['t1'], align='center', valign='middle')
        x += 122
    footer(sl, '健身房运营管理系统 · 课程答辩', 7)
    return sl


def p08(prs):
    """12 个模块与契约隔离（Hero）"""
    sl = blank(prs, C['white'])
    text(sl, 40, 24, 820, 46, '12 个模块，靠契约而不是靠自觉', size=32, bold=True,
         color=C['deep'], valign='middle', spacing=1.0)
    text(sl, 860, 26, 380, 38, '02 系统设计与架构', size=15, color=C['t3'],
         align='right', valign='middle')
    rect(sl, 40, 74, 1200, 1, fill=C['line2'])
    rect(sl, 40, 96, 680, 486, fill=C['light2'])
    picture(sl, 'assets/architecture.png', 54, 110, 652, 432, mode='contain')
    text(sl, 40, 542, 680, 30, '图：系统架构图（项目真实产出）', size=15,
         color=C['t3'], align='center', valign='middle')
    text(sl, 748, 96, 492, 32, '模块划分（com.gym.*）', size=24, bold=True,
         color=C['deep'], valign='middle')
    mods = ['identity 身份', 'membership 会籍', 'course 课程', 'booking 约课',
            'payment 收费', 'warning 预警', 'report 报表', 'assessment 业绩',
            'equipment 设备', 'system 系统', 'shared 共享', 'app 启动']
    mx, my = 748, 138
    for i, m in enumerate(mods):
        cx = mx + (i % 2) * 250
        cy = my + (i // 2) * 42
        rect(sl, cx, cy, 238, 34, fill=C['light'], radius=6)
        text(sl, cx + 10, cy, 218, 34, m, size=17, color=C['primary'], valign='middle')
    text(sl, 748, 402, 492, 130,
         '每个模块只暴露 api 包下的契约接口，internal 包对外不可见；'
         '跨模块调用一律走契约，禁止直连对方数据表。',
         size=21, color=C['t1'], spacing=1.55)
    conclusion(sl, 748, 528, 492, '守边界', '模块边界不靠代码评审守，靠自动化架构测试守', h=54)
    footer(sl, '来源：系统结构设计说明 · 代码结构与模块划分说明', 8)
    return sl


def p09(prs):
    """26 张表与 9 次迁移收敛"""
    sl = blank(prs, C['white'])
    header(sl, '26 张表与 12 次迁移收敛')
    picture(sl, 'assets/db_overview.png', 300, 128, 680, 420, mode='contain')
    text(sl, 330, 556, 620, 30, '图：数据库表结构总览（项目真实产出）', size=15,
         color=C['t3'], align='center', valign='middle')
    card(sl, 40, 128, 240, 200, fill=C['light'], line=None)
    text(sl, 58, 150, 204, 78, '26', size=62, bold=True, color=C['deep'],
         valign='middle', spacing=1.0)
    text(sl, 58, 234, 204, 34, '张数据表', size=21, color=C['t1'], valign='middle')
    text(sl, 58, 268, 204, 46, '覆盖会籍 / 约课 / 收费 / 预警 / 设备',
         size=16, color=C['t2'], spacing=1.4)
    card(sl, 40, 348, 240, 200, fill='FFFFFF', line=C['line'])
    text(sl, 58, 370, 204, 78, '12', size=62, bold=True, color=C['primary'],
         valign='middle', spacing=1.0)
    text(sl, 58, 454, 204, 34, '次版本化迁移', size=21, color=C['t1'], valign='middle')
    text(sl, 58, 488, 204, 46, 'V1–V12，结构变更全部可回放', size=16,
         color=C['t2'], spacing=1.4)
    card(sl, 1000, 128, 240, 200, fill=C['light2'], line=None)
    text(sl, 1018, 152, 204, 34, '规则参数外置', size=22, bold=True,
         color=C['deep'], valign='middle')
    text(sl, 1018, 194, 204, 118,
         '阈值不写死在代码里，改参数不动代码、不发版', size=18, color=C['t1'], spacing=1.5)
    card(sl, 1000, 348, 240, 200, fill='FFFFFF', line=C['line'])
    text(sl, 1018, 372, 204, 34, '禁止手工改库', size=22, bold=True,
         color=C['deep'], valign='middle')
    text(sl, 1018, 414, 204, 118,
         '任何结构变更都要落成迁移脚本，保证环境间一致', size=18, color=C['t1'], spacing=1.5)
    conclusion(sl, 40, 566, 1200, '判断',
               '结构演进必须可回放，所以用版本化迁移而不是手工改库', h=62)
    footer(sl, '来源：数据库设计说明 · 业务数据初始化与迁移方案', 9)
    return sl


def p10(prs):
    """零成本环境与可复现构建"""
    sl = blank(prs, C['white'])
    header(sl, '零成本环境与可复现构建')
    rows = [
        ('Spring Boot 3.2', '分层清晰、生态成熟，适合把业务规则做成可测试的模块'),
        ('MySQL 8.0', '关系模型贴合会籍/约课/收费的强约束场景，迁移工具成熟'),
        ('原生前端单页', '减少构建链依赖，答辩现场不受环境与网络影响'),
    ]
    text(sl, 40, 108, 700, 32, '技术选型与理由', size=24, bold=True,
         color=C['deep'], valign='middle')
    y = 148
    for t, d in rows:
        rect(sl, 40, y, 700, 116, fill=C['bg'], line=C['line'], radius=10)
        text(sl, 60, y + 14, 660, 32, t, size=23, bold=True, color=C['primary'], valign='middle')
        text(sl, 60, y + 52, 660, 52, d, size=20, color=C['t1'], spacing=1.45)
        y += 128
    card(sl, 780, 108, 460, 200, fill=C['light'], line=None)
    text(sl, 800, 128, 420, 32, '便携环境（全部在 E 盘）', size=22, bold=True,
         color=C['deep'], valign='middle')
    text(sl, 800, 168, 420, 126,
         'JDK 17 · Maven · MySQL · Node 合计约 1.8 GB，C 盘写入为零；'
         '环境变量只在命令行临时生效，不改系统设置',
         size=19, color=C['t1'], spacing=1.5)
    card(sl, 780, 324, 460, 196, fill='FFFFFF', line=C['line'])
    text(sl, 800, 344, 420, 32, '一键启动（三步合一）', size=22, bold=True,
         color=C['deep'], valign='middle')
    text(sl, 800, 384, 420, 122,
         '双击一个脚本：启动数据库 → 等待就绪 → 启动后端 → 自动打开浏览器；'
         '数据库起不来会自动回退到免数据库模式',
         size=19, color=C['t1'], spacing=1.5)
    conclusion(sl, 40, 548, 1200, '判断',
               '演示环境可复现，答辩当天不依赖现场配置与网络', h=70)
    footer(sl, '来源：部署方案设计 · 本地运行手册', 10)
    return sl


def p11(prs):
    """章节扉页 03"""
    sl = blank(prs, C['bg'])
    text(sl, 0, 130, W, 320, '03', size=240, bold=True, color=C['light'],
         align='center', valign='middle')
    text(sl, 140, 240, 1000, 36, '第三部分', size=22, color=C['blue'],
         align='center', valign='middle')
    text(sl, 140, 282, 1000, 86, '关键实现与业务规则', size=54, bold=True,
         color=C['deep'], align='center', valign='middle')
    rect(sl, 580, 378, 120, 4, fill=C['primary'])
    text(sl, 200, 400, 880, 60,
         '规则外置、权限服务端强制、主链路的异常分支同样要有明确语义',
         size=21, color=C['t2'], align='center', valign='middle', spacing=1.5)
    labels = ['规则外置', '角色鉴权', '约课主链路', '两项创新点']
    x = 424
    for lb in labels:
        rect(sl, x, 486, 108, 40, fill='FFFFFF', line=C['line'], radius=20)
        text(sl, x, 486, 108, 40, lb, size=19, color=C['t1'], align='center', valign='middle')
        x += 122
    footer(sl, '健身房运营管理系统 · 课程答辩', 11)
    return sl
