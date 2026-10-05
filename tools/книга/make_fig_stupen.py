# -*- coding: utf-8 -*-
"""Рисунок главы 9: занятая ступень и прыжок через неё.

Переделан 05.10.2026 по замечанию дизайнера: не рисовать свои «ступени», а
показать пример поверх куска настоящего планшета науки (красный трек), а
перемещение кубика — его полупрозрачным призраком.

Партия на троих. Бриана (красная) стоит на первой ступени; вторая занята
синим и зелёным кубиками (третья ячейка — только вчетвером). Призрак её
кубика из запаса перелетает через вторую ступень на третью: цена 3 + 4.
Ячейки — по якорям планшета (data/textures/board/anchors.yaml).
"""
import math
import os

import yaml
from PIL import Image, ImageDraw, ImageFont

D = os.path.dirname(os.path.abspath(__file__))
КОРЕНЬ = os.path.dirname(os.path.dirname(D))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]

шр = ImageFont.truetype(os.path.expanduser("~/.fonts/TekturNarrow-Bold.ttf"), 58)
ОХРА = (107, 68, 19, 255)
СВЕТ = (247, 241, 225, 235)
ЗЕЛ = (24, 108, 36, 255)

доска = Image.open(os.path.join(КОРЕНЬ, "data", "textures", "board", "science.png")).convert("RGBA")
я = yaml.safe_load(open(os.path.join(КОРЕНЬ, "data", "textures", "board", "anchors.yaml"), encoding="utf-8"))
наука = next(b for b in я["boards"] if b["id"] == "science")
лев = next(t for t in наука["tracks"] if t["id"] == "left")
ox, oy = лев["origin"]
sx, sy = наука["step"]
rx, ry = наука["row"]


def ячейка(ступень, ряд):
    return ox + sx * ступень + rx * ряд, oy + sy * ступень + ry * ряд


# кусок красного трека: от цены первой ступени до вершины, с наградами
X0, Y0, X1, Y1 = 60, 560, 960, 1180
ЗАПАС = 330                                   # поле слева под «запас Брианы»
кус = доска.crop((X0, Y0, X1, Y1))
W, H = кус.width + ЗАПАС, кус.height + 40
холст = Image.new("RGBA", (W, H), (0, 0, 0, 0))
холст.alpha_composite(кус, (ЗАПАС, 0))


def в_холст(x, y):
    return x - X0 + ЗАПАС, y - Y0


кубы = Image.open(os.path.join(D, "_кубики-технологий.png")).convert("RGBA")
w, h = кубы.size
КУБ = {"синий": кубы.crop((0, 0, w // 2, h // 2)), "красный": кубы.crop((w // 2, 0, w, h // 2)),
       "зелёный": кубы.crop((0, h // 2, w // 2, h)), "жёлтый": кубы.crop((w // 2, h // 2, w, h))}
С = 96
for к in КУБ:
    КУБ[к] = КУБ[к].crop(КУБ[к].getbbox()).resize((С, С), Image.LANCZOS)


def куб(цвет, x, y, прозр=1.0):
    im = КУБ[цвет].copy()
    if прозр < 1:
        im.putalpha(im.getchannel("A").point(lambda v: int(v * прозр)))
    холст.alpha_composite(im, (round(x - С / 2), round(y - С / 2)))


# кто где стоит
куб("красный", *в_холст(*ячейка(0, 0)))
куб("синий", *в_холст(*ячейка(1, 0)))
куб("зелёный", *в_холст(*ячейка(1, 1)))
откуда = (ЗАПАС * 0.42, 300)
куда = в_холст(*ячейка(2, 0))
куб("красный", *откуда, прозр=0.35)          # призрак: кубик из запаса Брианы
куб("красный", *куда)

d = ImageDraw.Draw(холст)
# дуга перелёта над занятой второй ступенью
mx, my = (откуда[0] + куда[0]) / 2, min(откуда[1], куда[1]) - 260
точки = []
for i in range(41):
    t = i / 40
    x = (1 - t) ** 2 * откуда[0] + 2 * (1 - t) * t * mx + t ** 2 * куда[0]
    y = (1 - t) ** 2 * откуда[1] + 2 * (1 - t) * t * my + t ** 2 * куда[1]
    точки.append((x, y))
# обрезать концы у кубиков
точки = [p for p in точки if math.dist(p, откуда) > С * 0.6 and math.dist(p, куда) > С * 0.7]
for цвет, ш in ((СВЕТ, 22), (ОХРА, 8)):
    d.line(точки, fill=цвет, width=ш, joint="curve")
x2, y2 = точки[-1]
x1, y1 = точки[-4]
a = math.atan2(y2 - y1, x2 - x1)
L = 46
нос = [(x2 + math.cos(a) * 20, y2 + math.sin(a) * 20),
       (x2 + math.cos(a + 2.5) * L, y2 + math.sin(a + 2.5) * L),
       (x2 + math.cos(a - 2.5) * L, y2 + math.sin(a - 2.5) * L)]
d.polygon(нос, fill=ОХРА, outline=СВЕТ)

# подписи
d.text((откуда[0], откуда[1] + С / 2 + 14), "запас\nБрианы", font=шр, fill=ОХРА, anchor="ma",
       align="center")
bx, by = в_холст(*ячейка(1, 1))
d.text((bx - 70, by + 10), "занято", font=шр, fill=(160, 40, 30, 255), anchor="rm",
       stroke_width=6, stroke_fill=СВЕТ)
d.text((mx, my + 70), "3 + 4 = 7 трофеев", font=шр, fill=ОХРА, anchor="mm",
       stroke_width=6, stroke_fill=СВЕТ)
# награда третьей ступени — кольцом: карта супер-задания
нx, нy = в_холст(580, 750)
for цвет, ш in ((СВЕТ, 16), (ЗЕЛ, 7)):
    d.ellipse((нx - 92, нy - 92, нx + 92, нy + 92), outline=цвет, width=ш)

png = os.path.join(D, "_ступень.png")
холст.save(png)
figure("ступень", png, 1400, W, H, [], css_w="100%", pad="0.4mm 0 0.4mm", в_колонке=True)
print("ok", холст.size)
