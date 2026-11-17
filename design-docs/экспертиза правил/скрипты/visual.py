# -*- coding: utf-8 -*-
"""Визуальные замеры одной книги: стили текста, цвета, плашки и линии, доля картинок.

Запуск: python visual.py <имя папки образца> [первая страница] [последняя страница]
Папки с исходными PDF задаются переменной окружения RULEBOOKS_DIR.
"""
import pymupdf, sys, os, json, collections, statistics as st
sys.stdout.reconfigure(encoding='utf-8')
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "образцы", "рулбуки")
SRC = [p for p in os.environ.get("RULEBOOKS_DIR", "").split(os.pathsep) if p]
MM = 25.4 / 72


def hexc(c):
    if c is None:
        return '-'
    if isinstance(c, int):
        return f'#{c:06x}'
    return '#' + ''.join(f'{int(round(x * 255)):02x}' for x in c[:3])


slug = sys.argv[1]
meta = json.load(open(os.path.join(R, slug, 'meta.json'), encoding='utf-8'))
pdf = next(os.path.join(d, meta['file']) for d in SRC if os.path.exists(os.path.join(d, meta['file'])))
doc = pymupdf.open(pdf)
first = int(sys.argv[2]) if len(sys.argv) > 2 else 1
last = int(sys.argv[3]) if len(sys.argv) > 3 else len(doc)
W, H = doc[0].rect.width, doc[0].rect.height
area = W * H
print(f'# {slug}: {W*MM:.0f}x{H*MM:.0f} мм, {len(doc)} листов PDF')

styles = collections.defaultdict(lambda: [0, set(), ''])   # (size, font, color, bold, ital) -> chars, pages, sample
fills = collections.defaultdict(lambda: [0, set(), []])    # цвет заливки -> число, страницы, размеры
strokes = collections.Counter()                            # (толщина, цвет) -> число
perpage = []
for pno in range(first - 1, last):
    page = doc[pno]
    tarea = 0
    for b in page.get_text('dict')['blocks']:
        if b['type'] != 0:
            continue
        x0, y0, x1, y1 = b['bbox']
        tarea += max(0, x1 - x0) * max(0, y1 - y0)
        for l in b['lines']:
            for s in l['spans']:
                t = s['text'].strip()
                if not t:
                    continue
                key = (round(s['size'] * 2) / 2, s['font'][-18:], hexc(s['color']), bool(s['flags'] & 16), bool(s['flags'] & 2))
                st_ = styles[key]
                st_[0] += len(t)
                st_[1].add(pno + 1)
                if len(t) > len(st_[2]) and len(st_[2]) < 40:
                    st_[2] = t[:60]
    iarea = 0
    nimg = 0
    for im in page.get_image_info():
        x0, y0, x1, y1 = im['bbox']
        a = max(0, min(x1, W) - max(x0, 0)) * max(0, min(y1, H) - max(y0, 0))
        if a > area * 0.85 or a < area * 0.004:
            continue
        iarea += a
        nimg += 1
    try:
        drs = page.get_drawings()
    except Exception:
        drs = []
    for d in drs:
        r = d['rect']
        a = r.width * r.height
        if d.get('fill') is not None and area * 0.004 < a < area * 0.9:
            f = fills[hexc(d['fill'])]
            f[0] += 1
            f[1].add(pno + 1)
            f[2].append((r.width * MM, r.height * MM))
        if d.get('color') is not None and d.get('width'):
            if r.width > W * 0.03 or r.height > H * 0.03:
                strokes[(round(d['width'] * 4) / 4, hexc(d['color']))] += 1
    perpage.append((pno + 1, tarea / area, min(iarea / area, 1.5), nimg, len(drs)))

print('\n## Стили текста (кегль, шрифт, цвет, полужирный, курсив): знаков, страниц, образец')
tot = sum(v[0] for v in styles.values())
for k, v in sorted(styles.items(), key=lambda kv: (-kv[0][0], -kv[1][0])):
    if v[0] < max(25, tot * 0.0012) or k[0] < 6:
        continue
    size, font, col, bold, ital = k
    print(f'{size:5.1f} {font:18} {col} {"Ж" if bold else " "}{"К" if ital else " "} {v[0]:6} зн {len(v[1]):3} с. | {v[2]}')

print('\n## Заливки (цвет): число фигур, страниц, типичный размер в мм')
for c, v in sorted(fills.items(), key=lambda kv: -kv[1][0])[:14]:
    ws = st.median(x[0] for x in v[2])
    hs = st.median(x[1] for x in v[2])
    print(f'{c} {v[0]:5} фиг {len(v[1]):3} с. | {ws:.0f}x{hs:.0f} мм')

print('\n## Линии (толщина в пт, цвет): число')
for (w, c), n in strokes.most_common(12):
    print(f'{w:5.2f} пт {c} {n}')

print('\n## По страницам: доля текста, доля картинок, число картинок, число векторных фигур')
for p, ta, ia, ni, nd in perpage:
    print(f'с.{p:2}: текст {ta*100:3.0f}%  картинки {ia*100:3.0f}%  картинок {ni:2}  фигур {nd}')
