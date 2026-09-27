# -*- coding: utf-8 -*-
"""ОБЛОЖКИ «С НЕБЕС НА ЗЕМЛЮ» — сборка из чистого арта дизайнера (28.09.2026).

Дизайнер отдаёт арт без надписей (папка «Обложка» на Диске):
  «обложка коробки лицо.png» — квадрат для лица коробки;
  «обложка рулбука.png»      — сцена для обложки книги правил.
Всё остальное собирается здесь, по образцу его вёрстки «обложка-new»:

  * надпись названия — кристаллическими буквами: плоские грани зелёного
    стекла, светлый блик по верхним граням, тёмно-зелёный кант и чёрная
    внешняя обводка, мягкая тень под надписью;
  * «настольная игра» над названием (коробка) и плашка «Правила игры»
    (книга);
  * нижняя полоса коробки: издатель, 2–4 игрока, 12+, девиз.

Грани строятся по полю расстояний от края буквы: направление склона
квантуется на несколько граней, у каждой свой тон, — так получается рубленый
«кристалл», а не гладкий градиент.

    python tools/обложка/make_cover.py [папка вывода]
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from scipy import ndimage as nd

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ДИСК = os.path.join(os.path.expanduser("~"), "Yandex.Disk", "Forged in Kelium", "Обложка")
ШРИФТЫ = os.path.join(ROOT, "gui", "src", "main", "resources", "fonts")
ВЫВОД = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "design-docs", "обложка")

НАЗВАНИЕ = ["С НЕБЕС", "НА ЗЕМЛЮ"]
ДЕВИЗ = ["конфликтная 4Х-стратегия,", "вдохновлённая известной RTS"]


def шрифт(имя, размер):
    return ImageFont.truetype(os.path.join(ШРИФТЫ, имя), размер)


# ---------------------------------------------------------------- кристалл
ГРАНИ = [  # тона граней от освещённой к теневой (свет сверху-слева)
    (190, 255, 196), (120, 235, 140), (70, 205, 100), (40, 170, 78),
    (24, 132, 60), (16, 100, 48)]


def кристалл(строка, кегль, разрядка=0.02):
    """Одна строка кристаллическими буквами → RGBA."""
    f = шрифт("Tektur-Bold.ttf", кегль)
    пробный = Image.new("L", (10, 10))
    d0 = ImageDraw.Draw(пробный)
    буквы = list(строка)
    шаг = [d0.textlength(ch, font=f) + (кегль * разрядка if ch != " " else 0) for ch in буквы]
    ш = int(sum(шаг) + кегль * 0.5)
    в = int(кегль * 1.35)
    поле = int(кегль * 0.16)
    маска = Image.new("L", (ш + 2 * поле, в + 2 * поле), 0)
    d = ImageDraw.Draw(маска)
    x = поле
    for ch, s in zip(буквы, шаг):
        d.text((x, поле), ch, font=f, fill=255)
        x += s
    m = np.asarray(маска) > 127
    # поле расстояний внутри буквы и склон по нему
    dist = nd.distance_transform_edt(m)
    глубина = max(1.0, кегль * 0.04)
    склон = np.clip(dist / глубина, 0, 1)
    gy, gx = np.gradient(nd.gaussian_filter(np.minimum(dist, глубина * 1.2), 1.2))
    угол = (np.degrees(np.arctan2(gy, gx)) + 360) % 360
    # свет сверху-слева (225°): чем ближе нормаль склона к свету, тем светлее
    свет = np.cos(np.radians(угол - 225))
    грань = np.clip(((1 - свет) / 2 * 5.99).astype(int), 0, 5)
    тон = np.array(ГРАНИ, dtype=float)[грань]
    # плоская «верхушка» кристалла — колотые грани: ячейки Вороного со своим
    # тоном, границы ячеек — тонкие светлые трещины (как на логотипе «Келемий»)
    верх = склон >= 1
    rng = np.random.default_rng(7)
    ys, xs = np.nonzero(m)
    n = max(8, int(m.sum() / (кегль * кегль * 0.018)))
    sel = rng.choice(len(ys), n, replace=False)
    семена = np.zeros(m.shape, dtype=np.int32)
    семена[ys[sel], xs[sel]] = np.arange(1, n + 1)
    _, (iy, ix) = nd.distance_transform_edt(семена == 0, return_indices=True)
    ячейка = семена[iy, ix]
    тона = np.array([(38, 168, 76), (70, 206, 104), (120, 236, 150), (52, 186, 92),
                     (160, 246, 180), (28, 146, 68), (96, 224, 128)], dtype=float)
    плоск_тон = тона[ячейка % len(тона)]
    трещина = (np.abs(np.diff(ячейка, axis=0, prepend=ячейка[:1])) > 0) |               (np.abs(np.diff(ячейка, axis=1, prepend=ячейка[:, :1])) > 0)
    трещина = nd.binary_dilation(трещина, iterations=max(1, int(кегль * 0.004)))
    плоск_тон[трещина] = (20, 110, 52)
    # свечение изнутри: к середине штриха светлее, как у стекла на просвет
    ядро = np.clip(dist / (глубина * 2.2), 0, 1)[..., None]
    плоск_тон = плоск_тон * (0.92 + 0.22 * ядро)
    rgb = np.where(верх[..., None], плоск_тон, тон)
    # блик: тонкая светлая кромка по верхним-левым скосам
    блик = (склон < 0.35) & (свет > 0.55) & m
    rgb[блик] = (225, 255, 225)
    rgb = np.clip(rgb, 0, 255).astype(np.uint8)
    буква = Image.fromarray(np.dstack([rgb, (m * 255).astype(np.uint8)]), "RGBA")
    # тёмно-зелёный кант и чёрная обводка
    кант = Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(
        max(3, int(кегль * 0.035) | 1)))
    обвод = кант.filter(ImageFilter.MaxFilter(max(3, int(кегль * 0.05) | 1)))
    out = Image.new("RGBA", маска.size, (0, 0, 0, 0))
    тень = Image.new("RGBA", маска.size, (0, 0, 0, 0))
    тень.putalpha(обвод.point(lambda v: v * 150 // 255))
    out.alpha_composite(тень.filter(ImageFilter.GaussianBlur(кегль * 0.05)),
                        (int(кегль * 0.02), int(кегль * 0.05)))
    чёрн = Image.new("RGBA", маска.size, (12, 20, 14, 255))
    out.paste(чёрн, (0, 0), обвод)
    зел = Image.new("RGBA", маска.size, (10, 70, 34, 255))
    out.paste(зел, (0, 0), кант)
    out.alpha_composite(буква)
    box = out.getchannel("A").getbbox()
    return out.crop(box)


def надпись(строки, ширина):
    """Название в две строки, вторая чуть мельче; ширина — по самой широкой."""
    ks = [кристалл(s, 400 if i == 0 else 330) for i, s in enumerate(строки)]
    k = ширина / max(x.width for x in ks)
    ks = [x.resize((round(x.width * k), round(x.height * k)), Image.LANCZOS) for x in ks]
    зазор = round(ks[0].height * -0.04)
    out = Image.new("RGBA", (max(x.width for x in ks),
                             sum(x.height for x in ks) + зазор), (0, 0, 0, 0))
    y = 0
    for x in ks:
        out.alpha_composite(x, ((out.width - x.width) // 2, y))
        y += x.height + зазор
    return out


# ---------------------------------------------------------------- детали
def текст_с_обводкой(d, xy, текст, f, цвет, обвод, толщина, anchor="mm"):
    d.text(xy, текст, font=f, fill=цвет, anchor=anchor, stroke_width=толщина,
           stroke_fill=обвод)


def плашка(im, box, цвет, альфа=205, скос=None):
    x0, y0, x1, y1 = box
    с = скос if скос is not None else (y1 - y0) // 3
    слой = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(слой).polygon([(x0 + с, y0), (x1, y0), (x1 - с, y1), (x0, y1)],
                                 fill=цвет + (альфа,))
    im.alpha_composite(слой)


def квадрат(арт, сторона):
    s = min(арт.size)
    x = (арт.width - s) // 2
    return арт.crop((x, 0, x + s, s)).resize((сторона, сторона), Image.LANCZOS)


def обложка_коробки():
    S = 3000
    im = квадрат(Image.open(os.path.join(ДИСК, "обложка коробки лицо.png")).convert("RGBA"), S)
    d = ImageDraw.Draw(im)
    текст_с_обводкой(d, (S // 2, 150), "настольная игра", шрифт("Tektur-Medium.ttf", 120),
                     (255, 255, 255), (20, 24, 20), 10)
    н = надпись(НАЗВАНИЕ, round(S * 0.62))
    im.alpha_composite(н, ((S - н.width) // 2, 240))
    # нижняя полоса: слева зелёная плашка с издателем и числом игроков, справа девиз
    плашка(im, (870, S - 330, S - 60, S - 50), (178, 132, 30), 205)
    # слева — блок дизайнера со старой обложки: издатель, 2–4 игрока, 12+
    стар = Image.open(os.path.join(ДИСК, "обложка-new-1.png")).convert("RGBA")
    k = S / стар.width
    блок = стар.crop((0, round(стар.height - 470), 1180, стар.height - 40))
    блок = блок.resize((round(блок.width * k), round(блок.height * k)), Image.LANCZOS)
    im.alpha_composite(блок, (0, S - 40 - блок.height))
    d = ImageDraw.Draw(im)
    f_д = шрифт("Tektur-Medium.ttf", 96)
    for i, s in enumerate(ДЕВИЗ):
        текст_с_обводкой(d, (S - 140, S - 255 + i * 115), s, f_д, (255, 255, 255),
                         (60, 44, 10), 4, anchor="rm")
    return im


def обложка_книги():
    S = 3000
    арт = Image.open(os.path.join(ДИСК, "обложка рулбука.png")).convert("RGBA")
    im = квадрат(арт, S)
    н = надпись(НАЗВАНИЕ, round(S * 0.64))
    im.alpha_composite(н, ((S - н.width) // 2, 140))
    # плашка «Правила игры»: полупрозрачная светлая полоса во всю ширину
    y0, y1 = round(S * 0.765), round(S * 0.905)
    слой = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(слой).rectangle((0, y0, S, y1), fill=(160, 200, 120, 150))
    im.alpha_composite(слой)
    d = ImageDraw.Draw(im)
    текст_с_обводкой(d, (S // 2, (y0 + y1) // 2), "Правила игры", шрифт("Tektur-Bold.ttf", 230),
                     (236, 255, 170), (22, 40, 18), 14)
    return im


def main():
    os.makedirs(ВЫВОД, exist_ok=True)
    for имя, im in (("обложка коробки — лицо", обложка_коробки()),
                    ("обложка книги правил", обложка_книги())):
        путь = os.path.join(ВЫВОД, имя + ".png")
        im.convert("RGB").save(путь, optimize=True)
        print(путь)
    кристалл_проба = надпись(НАЗВАНИЕ, 2400)
    кристалл_проба.save(os.path.join(ВЫВОД, "надпись названия.png"))


main()
