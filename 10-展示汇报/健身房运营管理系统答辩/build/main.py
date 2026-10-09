# -*- coding: utf-8 -*-
"""组装 22 页并输出 pptx，同时做版面溢出自检"""
import os
import sys
import math

BASE = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.dirname(BASE)
sys.path.insert(0, BASE)
os.chdir(PROJ)

from pptx import Presentation
from pptx.util import Emu
import ppt_build as A
import ppt_build2 as B
import ppt_build3 as C

PX = 914400 / 96.0
OUT = os.path.join(PROJ, '健身房运营管理系统答辩.pptx')

PAGES = [A.p01, A.p02, A.p03, A.p04, A.p05, A.p06, A.p07, A.p08, A.p09, A.p10, A.p11,
         B.p12, B.p13, B.p14, B.p15, B.p16, B.p22b, B.p22c, B.p22d,
         C.p22e, C.p22f, C.p22g, C.p22h,
         B.p17, B.p18, B.p19, B.p20, B.p21, B.p23]


def build():
    prs = Presentation()
    prs.slide_width = Emu(int(1280 * PX))
    prs.slide_height = Emu(int(720 * PX))
    for fn in PAGES:
        fn(prs)
    prs.save(OUT)
    return prs


def eff_len(s):
    """CJK 记 1.0，ASCII 记 0.55"""
    n = 0.0
    for ch in s:
        n += 1.0 if ord(ch) > 0x2E80 else 0.55
    return n


def check(prs):
    issues = []
    for pi, slide in enumerate(prs.slides, 1):
        for shp in slide.shapes:
            if not shp.has_text_frame:
                continue
            tf = shp.text_frame
            plain = tf.text.strip()
            if not plain:
                continue
            if not tf.word_wrap:
                continue
            bw = shp.width / PX
            bh = shp.height / PX
            need = 0.0
            for p in tf.paragraphs:
                runs = p.runs
                size_px = 22.0
                if runs and runs[0].font.size is not None:
                    size_px = runs[0].font.size.pt / 0.75
                ls = p.line_spacing if isinstance(p.line_spacing, float) else 1.2
                segs = ''.join(r.text for r in runs).split('\n')
                total_lines = 0
                for seg in segs:
                    if not seg.strip():
                        total_lines += 1
                        continue
                    cap = max(1.0, bw / (size_px * 1.0))
                    total_lines += max(1, math.ceil(eff_len(seg) / cap))
                need += total_lines * size_px * ls
            if need > bh * 1.03 + 2:
                issues.append((pi, plain[:34].replace('\n', ' '), round(bw), round(bh),
                               round(need), round(need / max(1, bh), 2)))
            if shp.top < 0 or shp.left < 0 or shp.left + shp.width > prs.slide_width + 10000:
                issues.append((pi, '越界: ' + plain[:20].replace('\n', ' '), 0, 0, 0, 0))
    return issues


if __name__ == '__main__':
    prs = build()
    n = len(prs.slides.__iter__.__self__._sldIdLst) if False else len(prs.slides._sldIdLst)
    print('生成完成：%s' % OUT)
    print('页数：%d' % n)
    issues = check(prs)
    if issues:
        print('\n=== 版面自检：发现 %d 处疑似溢出 ===' % len(issues))
        for pi, t, bw, bh, need, ratio in issues:
            print('  第 %02d 页 | %s | 框 %dx%d / 需 %d (x%.2f)' % (pi, t, bw, bh, need, ratio))
    else:
        print('\n=== 版面自检：未发现溢出 ===')
