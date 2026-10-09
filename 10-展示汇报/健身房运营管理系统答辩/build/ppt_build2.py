# -*- coding: utf-8 -*-
"""健身房运营管理系统 · 课程答辩 PPT（22 页，学术风）—— 第 12–22 页"""
from ppt_lib import (W, H, TOTAL, E, fs, C, FONT, blank, rect, text, picture,
                     icon, header, footer, card, table, conclusion)


def p12(prs):
    """SYS-R1~R10 规则表（Hero）"""
    sl = blank(prs, C['white'])
    text(sl, 40, 24, 900, 46, '10 条业务规则，改阈值不用发版', size=32, bold=True,
         color=C['deep'], valign='middle', spacing=1.0)
    text(sl, 940, 26, 300, 38, '03 关键实现与业务规则', size=15, color=C['t3'],
         align='right', valign='middle')
    rect(sl, 40, 74, 1200, 1, fill=C['line2'])
    data = [
        ['编号', '规则名称', '关键参数', '作用'],
        ['SYS-R1', '会籍有效期校验', 'requireStatus = active', '过期会籍不可约课'],
        ['SYS-R2', '请假冻结', 'maxDays = 90', '冻结期间不可约课'],
        ['SYS-R3', '约课上限与冲突', '容量校验 + 时间冲突校验', '不超卖、不撞课'],
        ['SYS-R4', '爽约惩罚', 'N = 3 次，限制 7 天', '让爽约有代价'],
        ['SYS-R5', '私教课包核销', '每次核销 1 次', '课消与课包对齐'],
        ['SYS-R6', '到期提醒', '提前 D = 7 天', '续费有人跟'],
        ['SYS-R7', '流失风险规则', '4 周未到店 或 爽约率>30%', '生成跟进任务'],
        ['SYS-R8', '爽约预测规则', '阈值 T = 0.60', '提前处置名额'],
        ['SYS-R9', '新会员首月跟进', '30 天内到店 < 2 次', '新客不流失'],
        ['SYS-R10', '私教业绩提成', '按课时计，比例 20%', '业绩可核算'],
    ]
    table(sl, 40, 96, 1200, 402, data, col_w=[110, 250, 360, 480],
          size=16, header_size=17, zebra=C['bg'],
          aligns=['center', 'left', 'left', 'left'])
    conclusion(sl, 40, 520, 1200, '判断',
               '规则参数集中在 rule_config 表里，运营改参数不动代码——这才是"规则可配置"的落地方式',
               h=70)
    footer(sl, '来源：rule_config 种子数据（项目真实配置）', 12)
    return sl


def p13(prs):
    """一个入口，三种角色"""
    sl = blank(prs, C['white'])
    header(sl, '一个入口，三种角色')
    text(sl, 40, 108, 740, 32, '登录后按角色进入对应界面', size=24, bold=True,
         color=C['deep'], valign='middle')
    roles = [
        ('member1 / member2', '会员端', '我的会籍 · 约课 · 扫码签到 · 取消预约', C['light']),
        ('manager', '门店后台', '约课管理 · 收费对账 · 风险预测 · 业绩提成 · 摘要审计', C['bg']),
        ('admin', '门店后台 + 系统管理', '在店长权限之上，可重置演示数据', C['bg']),
    ]
    y = 152
    for acct, role, scope, bg in roles:
        rect(sl, 40, y, 740, 116, fill=bg, line=C['line'], radius=10)
        text(sl, 62, y + 16, 300, 34, role, size=24, bold=True, color=C['deep'], valign='middle')
        text(sl, 62, y + 58, 700, 44, scope, size=19, color=C['t2'], spacing=1.4)
        rect(sl, 380, y + 16, 380, 32, fill='FFFFFF', line=C['line'], radius=16)
        text(sl, 380, y + 16, 380, 32, '账号：' + acct + '　密码：123456', size=16,
             color=C['primary'], align='center', valign='middle')
        y += 128
    conclusion(sl, 40, 540, 740, '判断', '权限必须在服务端强制，前端隐藏按钮不算权限', h=74)
    card(sl, 812, 108, 428, 506, fill=C['light2'], line=None)
    text(sl, 834, 128, 384, 32, '服务端强制的三种结果', size=23, bold=True,
         color=C['deep'], valign='middle')
    items = [
        ('401', '未登录或令牌过期', C['t2']),
        ('403', '越权访问他人数据、会员调用门店接口、非管理员执行重置', C['red']),
        ('409', '重复签到、重复取消、状态不允许的操作', C['primary']),
    ]
    y = 176
    for code, desc, col in items:
        rect(sl, 834, y, 384, 128, fill='FFFFFF', line=C['line'], radius=10)
        text(sl, 852, y + 14, 348, 46, code, size=36, bold=True, color=col, valign='middle')
        text(sl, 852, y + 64, 348, 54, desc, size=18, color=C['t1'], spacing=1.4)
        y += 142
    text(sl, 834, 600, 384, 30, '均已编写集成用例验证', size=15, color=C['t3'],
         align='center', valign='middle')
    footer(sl, '来源：系统测试文档 · 集成测试报告', 13)
    return sl


def p14(prs):
    """约课主链路"""
    sl = blank(prs, C['white'])
    header(sl, '约课主链路：约、到、退、罚')
    steps = [
        ('01', '预约', '有效会籍 + 不满员\n+ 无时间冲突', '重复提交保持幂等：返回同一预约号'),
        ('02', '签到', '扫码或前台代签\n状态改为已签到', '重复签到返回 409，不再重复核销'),
        ('03', '取消', '仅未签到可取消\n名额立即释放', '重复取消返回 409'),
        ('04', '爽约', '未签到判爽约\n累计达 3 次受限', '重复判定返回 409，限制 7 天'),
    ]
    x = 40
    for num, title, body, exc in steps:
        rect(sl, x, 112, 282, 180, fill=C['bg'], line=C['line'], radius=12)
        rect(sl, x + 24, 132, 44, 44, fill=C['primary'], radius=8)
        text(sl, x + 24, 132, 44, 44, num, size=21, bold=True, color='FFFFFF',
             align='center', valign='middle')
        text(sl, x + 80, 132, 180, 44, title, size=26, bold=True, color=C['deep'],
             valign='middle')
        text(sl, x + 24, 192, 240, 84, body, size=19, color=C['t1'], spacing=1.5)
        rect(sl, x, 306, 282, 112, fill=C['light'], radius=10)
        text(sl, x + 18, 320, 246, 84, exc, size=17, color=C['t2'], spacing=1.45)
        x += 306
    text(sl, 40, 436, 1200, 32, '同一个动作的异常分支，语义必须明确', size=22, bold=True,
         color=C['deep'], valign='middle')
    conclusion(sl, 40, 480, 1200, '判断',
               '主链路的完成度不看正常路径，而看异常路径：幂等、重复操作、状态冲突都要有确定结果', h=76)
    footer(sl, '来源：booking.feature · attendance.feature（真实执行 18 个场景）', 14)
    return sl


def p15(prs):
    """创新点一：流失风险（Hero）"""
    sl = blank(prs, C['white'])
    text(sl, 40, 24, 900, 46, '创新点一：把"会员会流失"变成可执行任务', size=32, bold=True,
         color=C['deep'], valign='middle', spacing=1.0)
    text(sl, 940, 26, 300, 38, 'SYS-R7 / SYS-R9', size=15, color=C['t3'],
         align='right', valign='middle')
    rect(sl, 40, 74, 1200, 1, fill=C['line2'])
    nums = [('4', '周', '连续未到店即判高风险'), ('30', '%', '近 30 天爽约率阈值'),
            ('2', '条', '可自动生成的跟进任务类型')]
    x = 40
    for n, unit, desc in nums:
        rect(sl, x, 96, 384, 208, fill=C['light'], line=None, radius=12)
        text(sl, x + 28, 116, 300, 106, n, size=84, bold=True, color=C['deep'],
             valign='middle', spacing=1.0)
        text(sl, x + 190, 156, 100, 40, unit, size=26, bold=True, color=C['primary'],
             valign='middle')
        text(sl, x + 28, 236, 328, 52, desc, size=19, color=C['t1'], spacing=1.4)
        x += 408
    card(sl, 40, 322, 588, 176, fill='FFFFFF', line=C['line'])
    text(sl, 62, 342, 544, 32, '命中判据（任一成立即生成任务）', size=22, bold=True,
         color=C['deep'], valign='middle')
    text(sl, 62, 382, 544, 100,
         '① 连续 4 周未到店\n② 近 30 天爽约率超过 30%\n③ 办卡 30 天内到店不足 2 次（SYS-R9）',
         size=19, color=C['t1'], spacing=1.6)
    card(sl, 652, 322, 588, 176, fill=C['light2'], line=None)
    text(sl, 674, 342, 544, 32, '产出物：一条有人负责的跟进任务', size=22, bold=True,
         color=C['deep'], valign='middle')
    text(sl, 674, 382, 544, 100,
         '任务带会员、命中原因、规则编号与到期时间；\n店长在后台直接看到待跟进清单，而不是看一张报表。',
         size=19, color=C['t1'], spacing=1.6)
    conclusion(sl, 40, 518, 1200, '判断',
               '预警的价值在于"生成一条有人负责的任务"，而不是多一个报表', h=72)
    footer(sl, '来源：warning.feature · 风险扫描实测输出（7 条任务）', 15)
    return sl


def p16(prs):
    """创新点二：爽约预测"""
    sl = blank(prs, C['white'])
    header(sl, '创新点二：预测爽约，提前处置名额')
    card(sl, 40, 108, 700, 430, fill=C['bg'], line=C['line'])
    text(sl, 62, 128, 656, 32, '概率算法（规则式起步，可替换为模型）', size=23, bold=True,
         color=C['deep'], valign='middle')
    terms = [
        ('0.10', '基线概率'),
        ('+ 爽约占比 × 0.60', '历史爽约次数 / 总次数'),
        ('+ 0.20', '超过 14 天未到店'),
        ('+ 0.15', '办卡不超过 30 天'),
        ('= min(0.99, p)', '上限封顶'),
    ]
    y = 176
    for k, v in terms:
        rect(sl, 62, y, 656, 62, fill='FFFFFF', line=C['line'], radius=8)
        text(sl, 82, y, 300, 62, k, size=21, bold=True, color=C['primary'], valign='middle')
        text(sl, 392, y, 310, 62, v, size=18, color=C['t2'], valign='middle')
        y += 72
    card(sl, 764, 108, 476, 200, fill=C['light'], line=None)
    text(sl, 786, 128, 432, 32, '阈值与动作的对应关系', size=22, bold=True,
         color=C['deep'], valign='middle')
    text(sl, 786, 168, 432, 126,
         '阈值 T = 0.60 来自规则表（可配置）。\n概率 > T 且课程已满 → release（释放名额给候补）\n'
         '概率 > T 且未满 → remind（提前提醒会员）',
         size=18, color=C['t1'], spacing=1.55)
    card(sl, 764, 324, 476, 214, fill='FFFFFF', line=C['line'])
    text(sl, 786, 344, 432, 32, '输出的是动作，不只是数字', size=22, bold=True,
         color=C['deep'], valign='middle')
    text(sl, 786, 384, 432, 140,
         '按概率倒序列出某节课的每位已约会员，附建议动作与会员姓名；'
         '店长照着清单打电话或放名额即可。',
         size=18, color=C['t1'], spacing=1.55)
    conclusion(sl, 40, 552, 1200, '判断',
               '预测必须落到动作上，否则只是一个好看的百分比', h=68)
    footer(sl, '来源：warning.feature · 爽约预测接口实测输出', 16)
    return sl


def p22b(prs):
    """排课、选课与课程维护"""
    sl = blank(prs, C['white'])
    header(sl, '排课、选课与课程维护')

    items = [
        ('01', '排课：管理员与店长均可新建课程',
         '填写编号、名称、类型、教练、场地、起止时间与容量；校验编号唯一、时间合法，并按 SYS-R3 检测教练或场地时段冲突'),
        ('02', '选课：所有登录用户可自愿报名',
         '会员账号固定作用于本人（伪造 memberId 会被改写）；重复选课幂等，返回同一预约号；会籍过期或爽约受限会被拦截'),
        ('03', '退课：自愿退出，规则明确',
         '仅「已预约」状态可退；课程开始后不可退；重复退课返回 409；退课后名额立即释放'),
        ('04', '维护：编辑、下架与重新上架',
         '修改时容量不得小于已预约人数；下架为软删除（保留历史预约可追溯），存在待上课预约时拒绝下架'),
    ]
    y = 100
    for num, title, body in items:
        rect(sl, 40, y, 1200, 106, fill=C['bg'], line=C['line'], radius=12)
        rect(sl, 62, y + 30, 46, 46, fill=C['primary'], radius=8)
        text(sl, 62, y + 30, 46, 46, num, size=21, bold=True, color='FFFFFF',
             align='center', valign='middle')
        text(sl, 126, y + 14, 1080, 30, title, size=23, bold=True, color=C['deep'],
             valign='middle')
        text(sl, 126, y + 48, 1080, 48, body, size=17, color=C['t2'], spacing=1.42)
        y += 116
    conclusion(sl, 40, 566, 1200, '权限设计',
               '会员调用排课接口返回 403；排课与课程维护对管理员和店长开放，数据重置仍仅管理员', h=64)
    footer(sl, '来源：系统测试文档 · E2 排课与课程维护、E3 会员自愿选课退课', 17)
    return sl


def p22c(prs):
    """会员注册与前后台联动"""
    sl = blank(prs, C['white'])
    header(sl, '会员注册与前后台联动')

    text(sl, 40, 106, 660, 32, '会员自助注册（免登录）', size=24, bold=True,
         color=C['deep'], valign='middle')
    steps = [
        ('01', '填写信息', '用户名、密码（至少 6 位）、姓名、手机号'),
        ('02', '一次事务创建', '登录账号 + 会员档案 + 角色绑定，不留半成品数据'),
        ('03', '自动编号并登录', '会员编号自动生成 M001…，注册成功即签发令牌'),
    ]
    y = 148
    for num, t, d in steps:
        rect(sl, 40, y, 660, 104, fill=C['bg'], line=C['line'], radius=10)
        rect(sl, 60, y + 30, 46, 46, fill=C['primary'], radius=8)
        text(sl, 60, y + 30, 46, 46, num, size=21, bold=True, color='FFFFFF',
             align='center', valign='middle')
        text(sl, 122, y + 16, 556, 30, t, size=22, bold=True, color=C['deep'],
             valign='middle')
        text(sl, 122, y + 50, 556, 44, d, size=17, color=C['t2'], spacing=1.4)
        y += 114

    rect(sl, 724, 106, 516, 232, fill=C['light2'], line=None, radius=12)
    text(sl, 746, 126, 472, 32, '门店后台：会员 ↔ 课程联动', size=23, bold=True,
         color=C['deep'], valign='middle')
    text(sl, 746, 166, 472, 160,
         '① 选中会员卡片 → 每门课出现「为 TA 约课」\n'
         '② 课程卡显示「已报名（N）：姓名…」与余位\n'
         '③ 未选会员时只提示，不显示代客按钮\n'
         '④ 约课 / 签到 / 取消后，预约、余位与名单、已约门数三级联动',
         size=17, color=C['t1'], spacing=1.55)

    rect(sl, 724, 350, 516, 176, fill='FFFFFF', line=C['line'], radius=12)
    text(sl, 746, 366, 472, 30, '角色与能力', size=22, bold=True, color=C['deep'],
         valign='middle')
    text(sl, 746, 402, 472, 112,
         '会员：选课 / 退课 / 签到（仅本人）\n'
         '店长：以上 + 排课与课程维护\n'
         '管理员：以上 + 数据重置',
         size=17, color=C['t1'], spacing=1.6)

    conclusion(sl, 40, 540, 1200, '联动价值',
               '注册把潜在会员纳入系统；联动让门店一眼看清每门课的真实报名情况', h=66)
    footer(sl, '来源：系统测试文档 · A2 会员自助注册、G 会员↔课程余位联动', 18)
    return sl


def p22d(prs):
    """扫码签到与前端体验优化"""
    sl = blank(prs, C['white'])
    text(sl, 40, 24, 900, 46, '扫码签到与前端体验优化', size=32, bold=True,
         color=C['deep'], valign='middle', spacing=1.0)
    text(sl, 940, 24, 300, 46, '03 关键实现与业务规则', size=15, color=C['t3'],
         align='right', valign='middle')
    rect(sl, 40, 74, 1200, 1, fill=C['line2'])

    rect(sl, 40, 96, 380, 470, fill=C['light2'], radius=12)
    picture(sl, 'assets/checkin_qr.png', 70, 112, 320, 320, mode='contain')
    text(sl, 60, 442, 340, 28, '图：真实二维码（现场可扫）', size=15, color=C['t3'],
         align='center', valign='middle')
    text(sl, 60, 474, 340, 78,
         '二维码内容\nGYM-CHECKIN:{预约号}:{会员号}\n会员出示，门店扫码核销',
         size=16, color=C['t2'], align='center', spacing=1.45)

    rect(sl, 440, 96, 800, 226, fill=C['bg'], line=C['line'], radius=12)
    text(sl, 462, 112, 760, 30, '扫码签到闭环（取代原先"点一下就完事"）', size=22,
         bold=True, color=C['deep'], valign='middle')
    steps = [
        ('① 会员', '在「我的预约」点扫码签到 → 弹出二维码'),
        ('② 门店', '在「约课管理 → 扫码核销签到」扫描或粘贴内容'),
        ('③ 系统', '核销成功并记录渠道为扫码；重复核销返回状态冲突'),
    ]
    y = 152
    for a, b in steps:
        text(sl, 462, y, 78, 32, a, size=19, bold=True, color=C['primary'], valign='middle')
        text(sl, 546, y, 670, 32, b, size=17, color=C['t1'], valign='middle')
        y += 46

    rect(sl, 440, 334, 800, 232, fill='FFFFFF', line=C['line'], radius=12)
    text(sl, 462, 350, 760, 30, '前端体验优化（4 项）', size=22, bold=True,
         color=C['deep'], valign='middle')
    opts = [
        '左侧导航 + 独立页面：会员 3 页 / 门店 8 页，按角色渲染菜单',
        '身份选择登录：先选会员 / 店长 / 管理员，再输入账号密码',
        '选课搜索：按课程名称 / 编号 / 教练 / 场地实时过滤',
        '二维码由前端内嵌生成器产出，不依赖外部库，也不依赖网络',
    ]
    y = 388
    for o in opts:
        text(sl, 462, y, 760, 36, '· ' + o, size=17, color=C['t2'], valign='middle')
        y += 40

    conclusion(sl, 40, 578, 1200, '判断',
               '签到从"点一下"升级为完整闭环；前端从单页堆叠改为分角色导航，演示路径更清晰', h=64)
    footer(sl, '来源：系统测试文档 · J 扫码签到、H 身份与导航、I 搜索', 19)
    return sl


def p17(prs):
    """章节扉页 04"""
    sl = blank(prs, C['bg'])
    text(sl, 0, 130, W, 320, '04', size=240, bold=True, color=C['light'],
         align='center', valign='middle')
    text(sl, 140, 240, 1000, 36, '第四部分', size=22, color=C['blue'],
         align='center', valign='middle')
    text(sl, 140, 282, 1000, 86, '质量保障与测试', size=54, bold=True,
         color=C['deep'], align='center', valign='middle')
    rect(sl, 580, 378, 120, 4, fill=C['primary'])
    text(sl, 200, 400, 880, 60,
         '测试不只是"跑通了"：六层检查全部可复现，缺陷都能说出根因',
         size=21, color=C['t2'], align='center', valign='middle', spacing=1.5)
    labels = ['六层检查', '缺陷根因', '防回归']
    x = 470
    for lb in labels:
        rect(sl, x, 486, 108, 40, fill='FFFFFF', line=C['line'], radius=20)
        text(sl, x, 486, 108, 40, lb, size=19, color=C['t1'], align='center', valign='middle')
        x += 122
    footer(sl, '健身房运营管理系统 · 课程答辩', 20)
    return sl


def p18(prs):
    """200 项六层检查"""
    sl = blank(prs, C['white'])
    header(sl, '243 项检查，覆盖七个层次')
    rect(sl, 40, 108, 420, 452, fill=C['light'], line=None, radius=12)
    text(sl, 60, 146, 380, 152, '243', size=116, bold=True, color=C['deep'],
         valign='middle', spacing=1.0)
    text(sl, 60, 306, 380, 40, '项自动化检查全部通过', size=22, color=C['t1'], valign='middle')
    text(sl, 60, 356, 380, 180,
         '从单元测试到真实浏览器点击、再到二维码反解，\n每一层都有可复现的执行命令与\n报告文件，答辩现场可逐条演示。',
         size=19, color=C['t2'], spacing=1.6)
    rows = [
        ('Java 单元 · 架构守卫 · 行为驱动', '73'),
        ('集成测试（登录 / 权限 / 全流程）', '68'),
        ('前端静态检查（防按钮无响应）', '5'),
        ('真实浏览器测试（含身份与二维码）', '56'),
        ('二维码结构校验（反解还原）', '5'),
        ('参考实现 BDD + 接口冒烟（对照）', '18 ／ 18'),
    ]
    y = 108
    for name, cnt in rows:
        rect(sl, 484, y, 756, 66, fill=C['bg'], line=C['line'], radius=10)
        text(sl, 508, y, 560, 66, name, size=21, color=C['t1'], valign='middle')
        text(sl, 1080, y, 140, 66, cnt, size=30, bold=True, color=C['primary'],
             align='right', valign='middle')
        y += 76
    conclusion(sl, 40, 578, 1200, '判断',
               '测试要能挡住"点了没反应"这类问题，所以既做静态检查，也做真实浏览器点击', h=62)
    footer(sl, '来源：五份测试报告文件（verify/ 与 app/ 目录）', 21)
    return sl


def p19(prs):
    """15 个缺陷"""
    sl = blank(prs, C['white'])
    header(sl, '15 个缺陷：把踩过的坑讲清楚')
    levels = [('严重', '2', C['red']), ('中等', '9', C['primary']), ('轻微', '4', C['t2'])]
    x = 40
    for name, cnt, col in levels:
        rect(sl, x, 108, 250, 116, fill=C['bg'], line=C['line'], radius=10)
        text(sl, x + 22, 122, 200, 60, cnt, size=48, bold=True, color=col, valign='middle')
        text(sl, x + 22, 182, 200, 30, name + '缺陷', size=20, color=C['t1'], valign='middle')
        x += 266
    text(sl, 838, 108, 402, 116, '全部已修复\n并补充了防回归手段', size=22, bold=True,
         color=C['deep'], spacing=1.5)
    bugs = [
        ('严重', '账号初始化不幂等，后端第二次启动直接失败',
         '重复绑角色触发主键冲突，事务被标记回滚 → 改为 SQL 层幂等写入'),
        ('中等', '字符串预约号未加引号，按钮点了没反应',
         '前端拼接事件参数时少了引号，被当成变量 → 修复并加静态检查永久防回归'),
        ('中等', '切换标签时的请求竞态，面板偶发空白',
         '自动加载的旧响应覆盖了新结果 → 统一响应结构并丢弃过期响应'),
    ]
    y = 244
    for lv, sym, cause in bugs:
        rect(sl, 40, y, 1200, 106, fill='FFFFFF', line=C['line'], radius=10)
        rect(sl, 60, y + 34, 70, 38, fill=C['red'] if lv == '严重' else C['primary'], radius=8)
        text(sl, 60, y + 34, 70, 38, lv, size=17, bold=True, color='FFFFFF',
             align='center', valign='middle')
        text(sl, 148, y + 14, 1070, 34, sym, size=21, bold=True, color=C['t1'], valign='middle')
        text(sl, 148, y + 52, 1070, 42, cause, size=18, color=C['t2'], spacing=1.4)
        y += 118
    conclusion(sl, 40, 598, 1200, '判断',
               '能复现、能定位、能防回归，缺陷才是资产；只写"已修复"等于没讲', h=46)
    footer(sl, '来源：系统测试文档 · 缺陷记录章节', 22)
    return sl


def p20(prs):
    """章节扉页 05"""
    sl = blank(prs, C['bg'])
    text(sl, 0, 130, W, 320, '05', size=240, bold=True, color=C['light'],
         align='center', valign='middle')
    text(sl, 140, 240, 1000, 36, '第五部分', size=22, color=C['blue'],
         align='center', valign='middle')
    text(sl, 140, 282, 1000, 86, '总结与展望', size=54, bold=True,
         color=C['deep'], align='center', valign='middle')
    rect(sl, 580, 378, 120, 4, fill=C['primary'])
    text(sl, 200, 400, 880, 60,
         '做完的说清楚，没做完的也交代明白',
         size=21, color=C['t2'], align='center', valign='middle', spacing=1.5)
    labels = ['已完成', '未完成', '下一步']
    x = 470
    for lb in labels:
        rect(sl, x, 486, 108, 40, fill='FFFFFF', line=C['line'], radius=20)
        text(sl, x, 486, 108, 40, lb, size=19, color=C['t1'], align='center', valign='middle')
        x += 122
    footer(sl, '健身房运营管理系统 · 课程答辩', 23)
    return sl


def p21(prs):
    """没做完的部分"""
    sl = blank(prs, C['white'])
    header(sl, '没做完的部分，以及下一步')
    card(sl, 40, 108, 700, 452, fill=C['light'], line=None)
    text(sl, 62, 128, 656, 34, '已完成并验证', size=24, bold=True, color=C['deep'],
         valign='middle')
    done = ['S1 约课与签到主线（含取消与爽约惩罚）',
            'S2 收费：下单、支付、回调幂等、异常订单、对账',
            'S4 预警：流失风险、爽约预测、新会员跟进',
            '登录与角色鉴权（服务端强制，越权拦截已测）',
            '七层 243 项自动化检查 + 一键启动脚本']
    y = 176
    for d in done:
        rect(sl, 62, y, 656, 66, fill='FFFFFF', line=C['line'], radius=8)
        text(sl, 82, y, 620, 66, '✓  ' + d, size=19, color=C['t1'], valign='middle')
        y += 74
    card(sl, 764, 108, 476, 216, fill='FFFFFF', line=C['line'])
    text(sl, 786, 128, 432, 34, '仅有表结构与骨架', size=23, bold=True,
         color=C['deep'], valign='middle')
    text(sl, 786, 170, 432, 134,
         'S5 设备与工单：已完成数据库设计与迁移（V5），'
         'Java 侧仅保留模块骨架，尚未实现业务逻辑。',
         size=19, color=C['t1'], spacing=1.55)
    card(sl, 764, 344, 476, 216, fill='FFFFFF', line=C['line'])
    text(sl, 786, 364, 432, 34, '完全未开始', size=23, bold=True, color=C['deep'],
         valign='middle')
    text(sl, 786, 406, 432, 134,
         '真实微信支付对接（当前为 Mock 回调）、'
         'JWT 无状态令牌（当前为内存会话）、CI 流水线。',
         size=19, color=C['t1'], spacing=1.55)
    conclusion(sl, 40, 578, 1200, '下一步',
               '先补 S5 业务逻辑，再把会话改为 JWT，最后接入支付沙箱与 CI', h=62)
    footer(sl, '来源：代码实现与测试报告 · 开放问题台账', 24)
    return sl


def p23(prs):
    """结束页"""
    sl = blank(prs, C['white'])
    rect(sl, 0, 0, W, 14, fill=C['primary'])
    rect(sl, 0, 14, W, 6, fill=C['blue'])
    text(sl, 140, 200, 1000, 90, '谢谢聆听，请老师指正', size=60, bold=True,
         color=C['deep'], align='center', valign='middle')
    rect(sl, 550, 316, 180, 4, fill=C['primary'])
    text(sl, 180, 350, 920, 60,
         '需求可追溯 · 系统可运行 · 质量可复现',
         size=26, color=C['t1'], align='center', valign='middle', spacing=1.5)
    text(sl, 180, 440, 920, 90,
         '项目文档与代码已托管至版本仓库，包含需求、设计、测试与运行手册；'
         '需要现场演示可随时切换。',
         size=19, color=C['t2'], align='center', valign='middle', spacing=1.6)
    rect(sl, 0, 640, W, 80, fill=C['light2'])
    text(sl, 140, 640, 1000, 80, '健身房运营管理系统 · 课程答辩 · 2026 年 10 月',
         size=15, color=C['t3'], align='center', valign='middle')
    return sl
