# -*- coding: utf-8 -*-
"""Рисунок главы 6 к примеру «Станция Брианы уничтожена».

Станция №4 (красная) давала 3 кубика: 2 лежат на ней, 3-й — в ячейке
казармы. Станцию уничтожили — любые 3 кубика Брианы уходят в общий запас.
Жетоны из набора текстур — они в одном масштабе с печатью (461×236 и
736×428, как в экспорте дизайнера).
"""
import os

from PIL import Image, ImageDraw, ImageFont

import стрелка

D = os.path.dirname(os.path.abspath(__file__))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]

Т = r"C:\shared\forged-in-kelium\data\textures"
ШРИФТ = r"C:\Windows\Fonts\TekturNarrow-Bold.ttf"
КУБ = 104                         # кубик размером с ячейку энергии на жетоне

станция = Image.open(os.path.join(Т, "token", "power_plant_l4_p2.png")).convert("RGBA")
казарма = Image.open(os.path.join(Т, "token", "barracks_p2.png")).convert("RGBA")
куб = Image.open(os.path.join(Т, "icons", "energy.png")).convert("RGBA").resize((КУБ, КУБ), Image.LANCZOS)

ПОЛЕ, ЗАЗОР, СТРЕЛКА, ЗАПАС = 10, 50, 170, 300
ПОДПИСЬ = 0
ВЕРХ = 10
W = ПОЛЕ + станция.width + ЗАЗОР + казарма.width + СТРЕЛКА + ЗАПАС + ПОЛЕ
H = ВЕРХ + казарма.height + ПОДПИСЬ
холст = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(холст)
шрифт = ImageFont.truetype(ШРИФТ, 72)
КОРИЧНЕВЫЙ, КРАСНЫЙ = (107, 68, 19, 255), (176, 30, 24, 255)

тень = Image.new("RGBA", куб.size, (0, 0, 0, 0))
тень.putalpha(куб.getchannel("A").point(lambda a: a * 0.45))
from PIL import ImageFilter
тень = тень.filter(ImageFilter.GaussianBlur(6))


номер = ImageFont.truetype(ШРИФТ, 44)


def положить(x, y, н=None):
    """Кубик с тенью: настоящий кубик на жетоне, а не напечатанный.
    Номер в зелёном кружке отличает его от кубиков, нарисованных на жетоне."""
    холст.alpha_composite(тень, (x + 8, y + 10))
    холст.alpha_composite(куб, (x, y))
    if н:
        cx, cy, r = x + КУБ - 6, y + 8, 27
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(24, 108, 36, 255), outline=(255, 255, 255, 255), width=5)
        d.text((cx, cy + 1), str(н), font=номер, fill=(255, 255, 255, 255), anchor="mm")


# станция — по низу казармы
xс, yс = ПОЛЕ, ВЕРХ + казарма.height - станция.height
холст.alpha_composite(станция, (xс, yс))
for н, (dx, dy) in enumerate(((225, 14), (318, 14)), 1):   # 2 кубика лежат на станции
    положить(xс + dx, yс + dy, н)

xк = xс + станция.width + ЗАЗОР
холст.alpha_composite(казарма, (xк, ВЕРХ))
положить(xк + 430 - КУБ // 2, ВЕРХ + 270 - КУБ // 2, 3)   # в ячейке энергии

x0 = xк + казарма.width + 20
стрелка.нарисовать(холст, x0, x0 + СТРЕЛКА - 40, ВЕРХ + казарма.height // 2)

# общий запас: 3 кубика пирамидой
xз = x0 + СТРЕЛКА
цз = xз + ЗАПАС // 2
низ = ВЕРХ + казарма.height // 2 + КУБ + 10
for н, (x, y) in enumerate(((цз - КУБ // 2, низ - 2 * КУБ - 20), (цз - КУБ - 20, низ - КУБ), (цз + 20, низ - КУБ)), 1):
    положить(x, y, н)

png = os.path.join(D, "_энергия.png")
холст.save(png)
figure("энергия-уничтожение", png, 1400, W, H, [], css_w="84%", pad="0.3mm 0 1.2mm",
       в_колонке=True)
print("ok", (W, H))
