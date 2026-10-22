# -*- coding: utf-8 -*-
"""Кружок мазком кисти (дизайнер 14.10.2026): обвести действие на карте так,
будто его обвели от руки яркой краской. Петля незамкнутая, с заходом
на начало; толщина плавает, концы сходят на нет; внутри мазка — светлые
прожилки щетины. Рисуется втрое крупнее и уменьшается — края гладкие.
"""
import math
import random

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ОРАНЖЕВЫЙ = (246, 104, 18)


def мазок(холст, cx, cy, r, цвет=ОРАНЖЕВЫЙ, толщина=None, зерно=1):
    сл = random.Random(зерно)
    k = 3
    толщина = толщина or r * 0.16
    поле = round((r * 1.35 + толщина) * k)
    размер = поле * 2
    маска = Image.new("L", (размер, размер), 0)
    d = ImageDraw.Draw(маска)
    ф1, ф2, ф3 = (сл.uniform(0, 6.3) for _ in range(3))
    а0 = сл.uniform(-2.6, -1.6)                 # начало — сверху слева
    оборот = 1.12
    точки = []
    n = 900
    for i in range(n + 1):
        t = i / n
        а = а0 + 2 * math.pi * оборот * t
        рр = r * (1 + .045 * math.sin(2 * а + ф1) + .02 * math.sin(3 * а + ф2) + .07 * t)
        рр *= k
        х = поле + рр * math.cos(а) * 1.04
        у = поле + рр * math.sin(а) * .97
        ш = толщина * k * (.25 + .75 * math.sin(math.pi * min(1, t * 1.15)) ** .5) * (1 + .12 * math.sin(7 * t + ф3))
        точки.append((х, у, ш, а))
    for х, у, ш, _ in точки:
        d.ellipse((х - ш / 2, у - ш / 2, х + ш / 2, у + ш / 2), fill=255)
    # прожилки щетины: тонкие светлые нити вдоль мазка, обрывистые
    нити = Image.new("L", маска.size, 0)
    dn = ImageDraw.Draw(нити)
    for _ in range(7):
        сдвиг = сл.uniform(-.38, .38)
        старт, длина = сл.uniform(0, .7), сл.uniform(.15, .5)
        путь = []
        for х, у, ш, а in точки[int(старт * n):int(min(1, старт + длина) * n)]:
            путь.append((х + math.cos(а) * ш * сдвиг, у + math.sin(а) * ш * сдвиг))
        if len(путь) > 2:
            dn.line(путь, fill=сл.randint(70, 140), width=max(1, round(толщина * k * .07)))
    маска = ImageChops.subtract(маска, нити.filter(ImageFilter.GaussianBlur(k * .6)))
    маска = маска.resize((размер // k, размер // k), Image.LANCZOS)
    слой = Image.new("RGBA", маска.size, цвет + (0,))
    слой.putalpha(маска.point(lambda v: round(v * .94)))
    холст.alpha_composite(слой, (round(cx - маска.width / 2), round(cy - маска.height / 2)))
