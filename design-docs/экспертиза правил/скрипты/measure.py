# -*- coding: utf-8 -*-
"""Замеры набора по PDF: формат, кегль, интерлиньяж, колонка, строка, поля, плотность."""
import pymupdf, sys, os, json, statistics as st, collections
sys.stdout.reconfigure(encoding='utf-8')
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "образцы", "рулбуки")
SRC = [p for p in os.environ.get("RULEBOOKS_DIR", "").split(os.pathsep) if p]  # папки с исходными PDF
SLUGS = ['dune-war-arrakis', 'root', 'dune-imperium-uprising', 'great-machine-rules', 'scythe', 'rising-sun', 'plotina',
         'circadians-chaos-order', 'cyclades', 'clash-of-cultures', 'ankh', 'forbidden-stars-learn-en',
         'forbidden-stars-ref-en', 'cry-havoc', 'nexus-ops', 'kemet-rules']
MM = 25.4 / 72


def pdf_of(slug):
    meta = json.load(open(os.path.join(R, slug, 'meta.json'), encoding='utf-8'))
    for d in SRC:
        p = os.path.join(d, meta['file'])
        if os.path.exists(p):
            return p
    return None


for slug in SLUGS:
    p = pdf_of(slug)
    if not p:
        print(slug, 'нет файла')
        continue
    doc = pymupdf.open(p)
    W, H = doc[0].rect.width, doc[0].rect.height
    wide = W > H * 1.3  # развороты в одном листе
    sizes = collections.Counter()
    fonts = collections.Counter()
    lines = []  # (size, width_pt, nchars, page, x0, x1, y0, y1)
    leads = []
    per_page = []
    img_share = []
    for pno, page in enumerate(doc):
        d = page.get_text('dict')
        tot = 0
        for b in d['blocks']:
            if b['type'] != 0:
                continue
            prev_y = None
            for l in b['lines']:
                txt = ''.join(s['text'] for s in l['spans'])
                n = len(txt.strip())
                if not n:
                    continue
                sz = max(l['spans'], key=lambda s: len(s['text']))['size']
                fn = max(l['spans'], key=lambda s: len(s['text']))['font']
                sizes[round(sz * 2) / 2] += n
                fonts[fn] += n
                tot += n
                x0, y0, x1, y1 = l['bbox']
                lines.append((round(sz * 2) / 2, x1 - x0, n, pno, x0, x1, y0, y1, len(b['lines'])))
                if prev_y is not None and 0 < y0 - prev_y < sz * 2.2:
                    leads.append((round(sz * 2) / 2, y0 - prev_y))
                prev_y = y0
        per_page.append(tot)
    if sum(per_page) < 3000:
        print(f'{slug}: {W*MM:.0f}x{H*MM:.0f} мм, {len(doc)} с., текстового слоя нет')
        continue
    body = sizes.most_common(1)[0][0]
    bfont = fonts.most_common(1)[0][0]
    bl = [x for x in lines if x[0] == body and x[8] >= 4]
    # полные строки: шире 85% от самой частой ширины
    widths = sorted(x[1] for x in bl)
    full = widths[int(len(widths) * 0.8)] if widths else 0
    fl = [x for x in bl if x[1] >= full * 0.9]
    colw = st.median(x[1] for x in fl) * MM if fl else 0
    cpl = st.median(x[2] for x in fl) if fl else 0
    lead = st.median(v for s, v in leads if s == body) if leads else 0
    pw = W / 2 if wide else W
    xs0 = min(x[4] % pw for x in bl) * MM
    xs1 = (pw - max((x[5] - 0.01) % pw for x in bl)) * MM
    ys0 = min(x[6] for x in bl) * MM
    ys1 = (H - max(x[7] for x in bl)) * MM
    heads = [(s, c) for s, c in sorted(sizes.items(), reverse=True) if s > body * 1.15 and c > 40][:6]
    small = [(s, c) for s, c in sorted(sizes.items()) if s < body * 0.93 and c > 150][-3:]
    rule_pages = [c for c in per_page if c > 1200]
    pagearea = (pw * MM) * (H * MM)
    print(f'{slug}: {pw*MM:.0f}x{H*MM:.0f} мм, {len(doc) * (2 if wide else 1)} с.; основной {body} пт {bfont[-22:]}; '
          f'интерлиньяж {lead:.1f} пт ({lead/body*100:.0f}%); колонка {colw:.0f} мм, {cpl:.0f} зн.; '
          f'поля лев {xs0:.0f} прав {xs1:.0f} верх {ys0:.0f} низ {ys1:.0f} мм; '
          f'знаков на стр. правил: медиана {st.median(rule_pages) if rule_pages else 0:.0f}, макс {max(per_page)}; '
          f'заголовки {heads}; мелкий {small}; зн/см2 {st.median(rule_pages)/(pagearea/100) if rule_pages else 0:.1f}')
