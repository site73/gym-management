# -*- coding: utf-8 -*-
"""
PPTX 生成框架（学术风 · 蓝白克制）
画布 1280x720 px，按 96dpi 映射到 EMU；字号按 px→pt(×0.75) 换算。
配色与字号严格遵循学术风规范：主蓝 #1E4FA8 / 藏蓝 #0E3F8C / 浅蓝 / 白底，红黄合计 ≤5%，金色为 0。
"""
import struct
from pptx.util import Emu, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE

W, H = 1280, 720
TOTAL = 23


def E(px):
    """px → EMU（96 dpi）"""
    return Emu(int(round(px * 914400 / 96)))


def fs(px):
    """设计字号 px → 实际 pt"""
    return Pt(px * 0.75)


C = {
    'primary': '1E4FA8', 'deep': '0E3F8C', 'blue': '3D7BD9',
    'light': 'E8EFF8', 'light2': 'F0F5FC', 'bg': 'F7F9FC', 'white': 'FFFFFF',
    'red': 'D9534F', 'yellow': 'FFC107',
    't1': '1A2230', 't2': '4A5568', 't3': '8B97A8',
    'line': 'D6DCE5', 'line2': 'E5E7EB',
    'onDeep': 'BFD3F0',
}
FONT = 'Microsoft YaHei'


# ---------------- 基础元素 ----------------

def blank(prs, bg='FFFFFF'):
    sl = prs.slides.add_slide(prs.slide_layouts[6])
    if bg:
        f = sl.background.fill
        f.solid()
        f.fore_color.rgb = RGBColor.from_string(bg)
    return sl


def rect(sl, x, y, w, h, fill=None, line=None, lw=1.0, radius=None):
    shp_type = MSO_SHAPE.ROUNDED_RECTANGLE if radius else MSO_SHAPE.RECTANGLE
    shp = sl.shapes.add_shape(shp_type, E(x), E(y), E(w), E(h))
    if radius:
        try:
            shp.adjustments[0] = min(0.5, float(radius) / max(1.0, min(w, h)))
        except Exception:
            pass
    if fill:
        shp.fill.solid()
        shp.fill.fore_color.rgb = RGBColor.from_string(fill)
    else:
        shp.fill.background()
    if line:
        shp.line.color.rgb = RGBColor.from_string(line)
        shp.line.width = Pt(lw)
    else:
        shp.line.fill.background()
    shp.shadow.inherit = False
    shp.text_frame.word_wrap = True
    return shp


def text(sl, x, y, w, h, content, size=22, bold=False, color='1A2230',
         align='left', spacing=1.3, valign='top', space_after=0):
    """content: str（\n 分段）或 list（每项为 str 或 [(t,bold,color), ...]）"""
    tb = sl.shapes.add_textbox(E(x), E(y), E(w), E(h))
    tf = tb.text_frame
    tf.word_wrap = True
    tf.margin_left = tf.margin_right = tf.margin_top = tf.margin_bottom = 0
    tf.vertical_anchor = {'top': MSO_ANCHOR.TOP, 'middle': MSO_ANCHOR.MIDDLE,
                          'bottom': MSO_ANCHOR.BOTTOM}[valign]
    lines = content.split('\n') if isinstance(content, str) else content
    for i, ln in enumerate(lines):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.alignment = {'left': PP_ALIGN.LEFT, 'center': PP_ALIGN.CENTER,
                       'right': PP_ALIGN.RIGHT}[align]
        p.line_spacing = spacing
        if space_after:
            p.space_after = Pt(space_after * 0.75)
        runs = ln if isinstance(ln, list) else [(ln, bold, color)]
        for item in runs:
            t, b, c = (item if isinstance(item, tuple) else (item, bold, color))
            r = p.add_run()
            r.text = t
            r.font.size = fs(size)
            r.font.bold = bool(b)
            r.font.color.rgb = RGBColor.from_string(c or color)
            r.font.name = FONT
    return tb


def picture(sl, path, x, y, w, h, mode='contain', border=None):
    """按 contain 保持比例；返回实际占位"""
    iw, ih = png_size(path)
    if mode == 'contain':
        k = min(w / iw, h / ih)
        rw, rh = iw * k, ih * k
    else:  # cover
        k = max(w / iw, h / ih)
        rw, rh = iw * k, ih * k
    left, top = x + (w - rw) / 2, y + (h - rh) / 2
    pic = sl.shapes.add_picture(path, E(left), E(top), E(rw), E(rh))
    if border:
        pic.line.color.rgb = RGBColor.from_string(border)
        pic.line.width = Pt(1)
    return pic


def png_size(path):
    with open(path, 'rb') as f:
        d = f.read(33)
    if d[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError('not a PNG: ' + path)
    return struct.unpack('>II', d[16:24])


def icon(sl, name, x, y, size, color):
    """用几何符号代替图标，避免字体依赖：圆形底 + 符号"""
    rect(sl, x, y, size, size, fill=color, radius=size / 2)
    return None


# ---------------- 母版（A3 页眉 + D 页脚） ----------------

def header(sl, title, breadcrumb='健身房运营管理系统 · 课程答辩'):
    rect(sl, 0, 0, W, 80, fill=C['primary'])
    text(sl, 40, 0, 760, 80, title, size=32, bold=True, color='FFFFFF', valign='middle')
    text(sl, 800, 0, 440, 80, breadcrumb, size=14, color=C['onDeep'],
         align='right', valign='middle')


def footer(sl, source, page):
    rect(sl, 40, 660, 1200, 1, fill=C['line2'])
    text(sl, 40, 664, 920, 30, source, size=15, color=C['t3'], valign='middle')
    text(sl, 1000, 664, 240, 30, '%02d / %d' % (page, TOTAL), size=16, bold=True,
         color=C['primary'], align='right', valign='middle')


def card(sl, x, y, w, h, fill=C['bg'], line=C['line'], radius=12):
    return rect(sl, x, y, w, h, fill=fill, line=line, radius=radius)


# ---------------- 表格 ----------------

def table(sl, x, y, w, h, data, col_w=None, size=16, header_size=16,
          header_fill=None, zebra=None, aligns=None):
    header_fill = header_fill or C['deep']
    rows, cols = len(data), len(data[0])
    gf = sl.shapes.add_table(rows, cols, E(x), E(y), E(w), E(h))
    tbl = gf.table
    if col_w:
        total = sum(col_w)
        for i, cw in enumerate(col_w):
            tbl.columns[i].width = Emu(int(E(w) * cw / total))
    for ri, row in enumerate(data):
        for ci, val in enumerate(row):
            cell = tbl.cell(ri, ci)
            cell.margin_left = E(8)
            cell.margin_right = E(8)
            cell.margin_top = E(3)
            cell.margin_bottom = E(3)
            cell.vertical_anchor = MSO_ANCHOR.MIDDLE
            if ri == 0:
                cell.fill.solid()
                cell.fill.fore_color.rgb = RGBColor.from_string(header_fill)
            else:
                cell.fill.solid()
                cell.fill.fore_color.rgb = RGBColor.from_string(
                    zebra if (zebra and ri % 2 == 0) else C['white'])
            tf = cell.text_frame
            tf.word_wrap = True
            lines = val.split('\n') if isinstance(val, str) else val
            for li, ln in enumerate(lines):
                p = tf.paragraphs[0] if li == 0 else tf.add_paragraph()
                align = (aligns[ci] if aligns else 'left')
                p.alignment = {'left': PP_ALIGN.LEFT, 'center': PP_ALIGN.CENTER,
                               'right': PP_ALIGN.RIGHT}[align]
                p.line_spacing = 1.15
                runs = ln if isinstance(ln, list) else [(ln, ri == 0, None)]
                for item in runs:
                    t, b, c = (item if isinstance(item, tuple) else (item, ri == 0, None))
                    r = p.add_run()
                    r.text = t
                    r.font.size = fs(header_size if ri == 0 else size)
                    r.font.bold = bool(b) or ri == 0
                    r.font.name = FONT
                    r.font.color.rgb = RGBColor.from_string(
                        c or (C['white'] if ri == 0 else C['t1']))
    return tbl


def conclusion(sl, x, y, w, label, body, fill=C['light'], bar=C['primary'], h=74):
    """底部结论条（双重锚定之一）"""
    rect(sl, x, y, w, h, fill=fill, radius=8)
    rect(sl, x, y, 4, h, fill=bar)
    text(sl, x + 16, y, 96, h, label, size=20, bold=True, color=bar, valign='middle')
    text(sl, x + 118, y, w - 136, h, body, size=20, color=C['t1'],
         valign='middle', spacing=1.35)
