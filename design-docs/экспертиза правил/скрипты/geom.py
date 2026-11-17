# -*- coding: utf-8 -*-
"""Геометрия блоков книги: крупные рамки и плашки, края основного текста, отбивки.

Запуск: python geom.py <имя папки образца> [первая] [последняя]
"""
import pymupdf, sys, os, json, collections, statistics as st
sys.stdout.reconfigure(encoding='utf-8')
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "образцы", "рулбуки")
SRC = [p for p in os.environ.get("RULEBOOKS_DIR", "").split(os.pathsep) if p]
MM = 25.4 / 72


def hexc(c):
    if c is None:
        return '-'
    return '#' + ''.join(f'{int(round(x * 255)):02x}' for x in c[:3])


slug = sys.argv[1]
meta = json.load(open(os.path.join(R, slug, 'meta.json'), encoding='utf-8'))
pdf = next(os.path.join(d, meta['file']) for d in SRC if os.path.exists(os.path.join(d, meta['file'])))
doc = pymupdf.open(pdf)
first = int(sys.argv[2]) if len(sys.argv) > 2 else 1
last = int(sys.argv[3]) if len(sys.argv) > 3 else len(doc)
W, H = doc[0].rect.width, doc[0].rect.height

sizes = collections.Counter()
for page in doc:
    for b in page.get_text('dict')['blocks']:
        if b['type'] == 0:
            for l in b['lines']:
                for s in l['spans']:
                    sizes[round(s['size'] * 2) / 2] += len(s['text'].strip())
body = sizes.most_common(1)[0][0]

frames = collections.Counter()   # (заливка, обводка, толщина) -> число
frame_ex = {}
lefts, rights, gaps, para_gaps, indents = [], [], [], [], []
for pno in range(first - 1, last):
    page = doc[pno]
    try:
        drs = page.get_drawings()
    except Exception:
        drs = []
    for d in drs:
        r = d['rect']
        if r.width < W * 0.12 or r.height < H * 0.015:
            continue
        if r.width > W * 0.98 and r.height > H * 0.98:
            continue
        key = (hexc(d.get('fill')), hexc(d.get('color')), round((d.get('width') or 0) * 4) / 4)
        frames[key] += 1
        frame_ex.setdefault(key, []).append((pno + 1, r.x0 * MM, r.y0 * MM, r.width * MM, r.height * MM))
    prev = None
    for b in page.get_text('dict')['blocks']:
        if b['type'] != 0:
            continue
        ls = [l for l in b['lines'] if ''.join(s['text'] for s in l['spans']).strip()]
        if not ls:
            continue
        sz = round(max(ls[0]['spans'], key=lambda s: len(s['text']))['size'] * 2) / 2
        if sz != body or len(ls) < 3:
            prev = None
            continue
        x0 = min(l['bbox'][0] for l in ls[1:])
        lefts.append(round(x0 * MM))
        rights.append(round(max(l['bbox'][2] for l in ls) * MM))
        indents.append(round((ls[0]['bbox'][0] - x0) * MM, 1))
        if prev is not None and abs(prev[0] - x0) < 3 and 0 < b['bbox'][1] - prev[1] < body * 4:
            para_gaps.append(round(b['bbox'][1] - prev[1], 1))
        prev = (x0, b['bbox'][3])

print(f'# {slug}: страница {W*MM:.0f}x{H*MM:.0f} мм, основной кегль {body} пт')
print('левые края абзацев, мм:', collections.Counter(lefts).most_common(8))
print('правые края абзацев, мм:', collections.Counter(rights).most_common(8))
print('абзацный отступ первой строки, мм:', collections.Counter(indents).most_common(4))
print('отбивка между абзацами, пт:', collections.Counter(para_gaps).most_common(5))
print('\nрамки и плашки (заливка, обводка, толщина пт): число; пример (стр, x, y, ширина, высота в мм)')
for k, n in frames.most_common(18):
    e = frame_ex[k]
    ws = st.median(x[3] for x in e)
    hs = st.median(x[4] for x in e)
    pages = sorted(set(x[0] for x in e))
    print(f'{k[0]:8} {k[1]:8} {k[2]:5.2f} : {n:4}  типично {ws:.0f}x{hs:.0f} мм; страницы {pages[:12]}{"..." if len(pages) > 12 else ""}')
