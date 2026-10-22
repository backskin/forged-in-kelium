# -*- coding: utf-8 -*-
"""LEX TALIONIS — раскладки логотипа (12.10.2026).

Шесть вариантов расположения одной и той же надписи Tektur Black: размеры
LEX и TALIONIS, разделители, шестигранник, пластина, зеркальные рамки.
Это ШАБЛОНЫ для рендера в Leonardo — чёрное на белом, 2400×1000, без
кириллицы (русский подзаголовок «Закон возмездия» кладётся потом настоящим
шрифтом). Листы для выбора — с подзаголовком, чтобы видеть целиком.

    python tools/логотип/лекс_раскладки.py
→ illustrations/2 — ПРОМТЫ/ЛЕКС — шаблон <вариант>.png (2400×1000)
→ design-docs/логотип-лекс/_раскладки — лист.jpg
"""
import math
import os

from PIL import Image, ImageDraw, ImageFont

ШРИФТ = r"C:\Windows\Fonts\Tektur-Black.ttf"
ПОДПИСЬ = r"C:\Windows\Fonts\TekturNarrow-Bold.ttf"
ШАБЛОНЫ = r"C:\Users\backskin\forged-in-kelium\illustrations\2 — ПРОМТЫ"
ЛИСТ = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
                    "design-docs", "логотип-лекс")
W, H = 2400, 1000
ЧЁРНЫЙ, БЕЛЫЙ = (0, 0, 0, 255), (255, 255, 255, 255)


def маска(текст, кегль, разрядка=0.04, путь=ШРИФТ):
    f = ImageFont.truetype(путь, кегль)
    d = ImageDraw.Draw(Image.new("L", (1, 1)))
    ш = [d.textlength(б, font=f) for б in текст]
    w = round(sum(ш) + кегль * разрядка * (len(текст) - 1))
    asc, desc = f.getmetrics()
    m = Image.new("L", (w + 8, asc + desc), 0)
    dd = ImageDraw.Draw(m)
    x = 0
    for б, шб in zip(текст, ш):
        dd.text((x, 0), б, font=f, fill=255)
        x += шб + кегль * разрядка
    return m.crop(m.getbbox())


def по_ширине(текст, ширина, разрядка=0.04, путь=ШРИФТ):
    к = 300
    for _ in range(25):
        m = маска(текст, к, разрядка, путь)
        новый = max(10, round(к * ширина / m.width))
        if abs(новый - к) <= 1:
            break
        к = новый
    return маска(текст, к, разрядка, путь)


def по_высоте(текст, высота, разрядка=0.04, путь=ШРИФТ):
    к = 200
    for _ in range(25):
        m = маска(текст, к, разрядка, путь)
        новый = max(10, round(к * высота / m.height))
        if abs(новый - к) <= 1:
            break
        к = новый
    return маска(текст, к, разрядка, путь)


def с_трекингом(текст, высота, ширина):
    """Строка заданной высоты, разрядка подобрана так, чтобы встать в ширину."""
    lo, hi = 0.0, 4.0
    for _ in range(30):
        mid = (lo + hi) / 2
        m = по_высоте(текст, высота, mid)
        if m.width < ширина:
            lo = mid
        else:
            hi = mid
    return по_высоте(текст, высота, lo)


def чёрным(холст, m, xy):
    холст.paste(ЧЁРНЫЙ, xy, m)


def белым(холст, m, xy):
    холст.paste(БЕЛЫЙ, xy, m)


def планка(d, x0, y0, x1, y1, скос):
    """Чёрная планка со скошенными концами — под стать Tektur."""
    d.polygon([(x0 + скос, y0), (x1 - скос, y0), (x1, y0 + скос), (x1, y1 - скос),
               (x1 - скос, y1), (x0 + скос, y1), (x0, y1 - скос), (x0, y0 + скос)], fill=ЧЁРНЫЙ)


def шестигранник(cx, cy, R):
    return [(cx + R * math.cos(math.radians(60 * i)), cy + R * math.sin(math.radians(60 * i))) for i in range(6)]


def пустой():
    return Image.new("RGBA", (W, H), БЕЛЫЙ)


def центр(холст, m, y, x=None):
    чёрным(холст, m, (round((W - m.width) / 2) if x is None else x, y))


# ---------- шесть раскладок ----------

def в1_линейки():
    """LEX мелко, разрежённо, между двумя планками; TALIONIS во всю ширину."""
    c = пустой()
    d = ImageDraw.Draw(c)
    t = по_ширине("TALIONIS", 2000)
    l = по_высоте("LEX", t.height * 0.34, 0.55)
    общая = l.height + 70 + t.height
    y = (H - общая) // 2
    центр(c, l, y)
    ly = y + l.height // 2
    x0, x1 = (W - l.width) // 2 - 70, (W + l.width) // 2 + 70
    планка(d, 200, ly - 14, x0, ly + 14, 12)
    планка(d, x1, ly - 14, 2200, ly + 14, 12)
    центр(c, t, y + l.height + 70)
    return c


def в2_шестигранник():
    """Шестигранник-значок с LEX врезан в верхний край TALIONIS."""
    c = пустой()
    t = по_ширине("TALIONIS", 2000)
    R = 205
    ty = 150 + 2 * R - 30
    центр(c, t, ty)
    d = ImageDraw.Draw(c)
    cx, cy = W // 2, ty - R + 30 - 0
    d.polygon(шестигранник(cx, cy, R), fill=ЧЁРНЫЙ)
    d.polygon(шестигранник(cx, cy, R - 30), fill=БЕЛЫЙ)
    l = по_ширине("LEX", R * 1.15, 0.06)
    чёрным(c, l, (cx - l.width // 2, cy - l.height // 2))
    return c


def в3_ступень():
    """LEX слева, от него планка уходит вправо до края; TALIONIS ниже; нижняя черта."""
    c = пустой()
    d = ImageDraw.Draw(c)
    t = по_ширине("TALIONIS", 2000)
    l = по_высоте("LEX", t.height * 0.42, 0.10)
    общая = l.height + 56 + t.height + 90
    y = (H - общая) // 2
    чёрным(c, l, (200, y))
    ly = y + l.height // 2
    планка(d, 200 + l.width + 50, ly - 18, 2200, ly + 18, 14)
    чёрным(c, t, (200, y + l.height + 56))
    by = y + l.height + 56 + t.height + 50
    планка(d, 200, by, 2200, by + 16, 8)
    return c


def в4_зеркало():
    """Обе строки одной ширины, между ними планка с ромбом, по углам скобки."""
    c = пустой()
    d = ImageDraw.Draw(c)
    t = по_ширине("TALIONIS", 1900)
    l = с_трекингом("LEX", t.height * 0.36, 1900)
    пр = 90
    общая = l.height + пр + t.height
    y = (H - общая) // 2
    центр(c, l, y)
    py = y + l.height + пр // 2
    планка(d, 260, py - 9, 1120, py + 9, 8)
    планка(d, 1280, py - 9, 2140, py + 9, 8)
    d.polygon([(1200, py - 38), (1238, py), (1200, py + 38), (1162, py)], fill=ЧЁРНЫЙ)
    центр(c, t, y + l.height + пр)
    # скобки по углам
    x0, x1, y0, y1, т, дл = 190, 2210, y - 56, y + общая + 56, 22, 150
    for (x, yy, sx, sy) in ((x0, y0, 1, 1), (x1, y0, -1, 1), (x0, y1, 1, -1), (x1, y1, -1, -1)):
        d.rectangle([min(x, x + sx * дл), min(yy, yy + sy * т), max(x, x + sx * дл), max(yy, yy + sy * т)], fill=ЧЁРНЫЙ)
        d.rectangle([min(x, x + sx * т), min(yy, yy + sy * дл), max(x, x + sx * т), max(yy, yy + sy * дл)], fill=ЧЁРНЫЙ)
    return c


def в5_пластина():
    """LEX белым на чёрной пластине со скосами, пластина сидит на верхнем крае TALIONIS."""
    c = пустой()
    d = ImageDraw.Draw(c)
    t = по_ширине("TALIONIS", 2000)
    l = по_высоте("LEX", t.height * 0.40, 0.18)
    ph, pw = l.height + 60, l.width + 150
    y = (H - (ph + t.height + 36)) // 2
    px = (W - pw) // 2
    центр(c, t, y + ph + 36)
    планка(d, px, y, px + pw, y + ph, 34)
    белым(c, l, (px + (pw - l.width) // 2, y + (ph - l.height) // 2))
    return c


def в6_крупный_лекс():
    """В одну строку: LEX чуть меньше по высоте, вертикальные чёрточки-разделители."""
    c = пустой()
    d = ImageDraw.Draw(c)
    t = по_высоте("TALIONIS", 215, 0.03)
    l = по_высоте("LEX", 150, 0.06)
    пр = 70
    общ = l.width + пр * 2 + 26 + t.width
    x = (W - общ) // 2
    base = H // 2 + t.height // 2
    чёрным(c, l, (x, base - l.height))
    xs = x + l.width + пр
    планка(d, xs, base - t.height, xs + 26, base, 10)
    чёрным(c, t, (xs + 26 + пр, base - t.height))
    return c


def в7_рамка():
    """Плашка LEX над TALIONIS, балки по бокам плашки, четыре угловые скобы вокруг всего."""
    c = пустой()
    d = ImageDraw.Draw(c)
    t = по_ширине("TALIONIS", 1700)
    l = по_высоте("LEX", t.height * 0.40, 0.18)
    ph, pw = l.height + 60, l.width + 150
    зазор = 44
    общая = ph + зазор + t.height
    y = (H - общая) // 2
    px = (W - pw) // 2
    центр(c, t, y + ph + зазор)
    планка(d, px, y, px + pw, y + ph, 34)
    белым(c, l, (px + (pw - l.width) // 2, y + (ph - l.height) // 2))
    # балки по бокам плашки
    cy = y + ph // 2
    планка(d, 330, cy - 20, px - 60, cy + 20, 16)
    планка(d, px + pw + 60, cy - 20, W - 330, cy + 20, 16)
    # угловые скобы
    x0, x1, y0, y1, т, дл = 160, W - 160, y - 70, y + общая + 70, 26, 170
    for (x, yy, sx, sy) in ((x0, y0, 1, 1), (x1, y0, -1, 1), (x0, y1, 1, -1), (x1, y1, -1, -1)):
        d.rectangle([min(x, x + sx * дл), min(yy, yy + sy * т), max(x, x + sx * дл), max(yy, yy + sy * т)], fill=ЧЁРНЫЙ)
        d.rectangle([min(x, x + sx * т), min(yy, yy + sy * дл), max(x, x + sx * т), max(yy, yy + sy * дл)], fill=ЧЁРНЫЙ)
    return c


ВАРИАНТЫ = [("1 линейки", в1_линейки), ("2 шестигранник", в2_шестигранник), ("3 ступень", в3_ступень),
            ("4 зеркало", в4_зеркало), ("5 пластина", в5_пластина), ("6 в строку", в6_крупный_лекс), ("7 рамка", в7_рамка)]


def лист(готовые):
    os.makedirs(ЛИСТ, exist_ok=True)
    к, кол = 700, 3
    h = round(к * H / W)
    out = Image.new("RGB", (кол * (к + 16) + 16, 3 * (h + 56) + 16), (50, 50, 50))
    d = ImageDraw.Draw(out)
    f = ImageFont.truetype(ПОДПИСЬ, 26)
    for i, (имя, im) in enumerate(готовые):
        x, y = 16 + (i % кол) * (к + 16), 16 + (i // кол) * (h + 56)
        out.paste(im.convert("RGB").resize((к, h), Image.LANCZOS), (x, y))
        d.text((x, y + h + 10), имя, font=f, fill=(230, 230, 230))
    out.save(os.path.join(ЛИСТ, "_раскладки — лист.jpg"), quality=90)


if __name__ == "__main__":
    os.makedirs(ШАБЛОНЫ, exist_ok=True)
    готовые = []
    for имя, ф in ВАРИАНТЫ:
        im = ф()
        im.convert("RGB").save(os.path.join(ШАБЛОНЫ, "ЛЕКС — шаблон %s.png" % имя))
        готовые.append((имя, im))
    лист(готовые)
    print("ok", len(готовые))
