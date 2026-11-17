# -*- coding: utf-8 -*-
"""Раскладка страницы: текстовые блоки (место, кегль, шрифт, объём) и картинки крупнее 1,5% полосы."""
import sys, pymupdf
sys.stdout.reconfigure(encoding='utf-8')
pdf, first, last = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
doc = pymupdf.open(pdf)
MM = 25.4/72
for pno in range(first-1, min(last, len(doc))):
    page = doc[pno]
    W, H = page.rect.width, page.rect.height
    print(f"\n===== СТР {pno+1}  {W*MM:.0f}x{H*MM:.0f} мм =====")
    d = page.get_text("dict")
    rows = []; tot = 0; body = 0
    for b in d["blocks"]:
        if b["type"] != 0: continue
        txt = ""; sizes = {}; fonts = {}
        for l in b["lines"]:
            for s in l["spans"]:
                t = s["text"]; txt += t; n = len(t.strip())
                sizes[round(s["size"],1)] = sizes.get(round(s["size"],1),0)+n
                fonts[s["font"]] = fonts.get(s["font"],0)+n
            txt += " "
        txt = " ".join(txt.split())
        if not txt: continue
        sz = max(sizes, key=sizes.get); fn = max(fonts, key=fonts.get)
        if sz < 6: continue
        x0,y0,x1,y1 = b["bbox"]
        tot += len(txt)
        rows.append((round(y0/H*100), x0, f"T x{x0/W*100:3.0f}-{x1/W*100:3.0f} y{y0/H*100:3.0f}-{y1/H*100:3.0f} | {sz:4.1f} {fn[-14:]:14} | {len(b['lines']):2}с {len(txt):4}зн | {txt[:60]}"))
    seen = []
    for im in page.get_image_info():
        x0,y0,x1,y1 = im["bbox"]
        x0=max(x0,0); y0=max(y0,0); x1=min(x1,W); y1=min(y1,H)
        a = (x1-x0)*(y1-y0)/(W*H)
        if a < 0.015 or a > 0.85: continue
        key = (round(x0/W*20), round(y0/H*20), round(x1/W*20), round(y1/H*20))
        if key in seen: continue
        seen.append(key)
        rows.append((round(y0/H*100), x0, f"I x{x0/W*100:3.0f}-{x1/W*100:3.0f} y{y0/H*100:3.0f}-{y1/H*100:3.0f} | картинка {a*100:.0f}% полосы"))
    for r in sorted(rows): print(r[2])
    print(f"   знаков: {tot}")
