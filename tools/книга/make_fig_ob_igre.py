# -*- coding: utf-8 -*-
"""Рисунок главы 1: главные вещи на поле — ЦУ, тайл зарождения, келемий, трофей.

Текстуры берутся из data/textures (печатные компоненты дизайнера), подписи —
шрифтом Tektur цветом заголовков книги. Картинка — во всю ширину полосы.
"""
import os

from PIL import Image, ImageDraw, ImageFont

D = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(D))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]

T = os.path.join(ROOT, "data", "textures")
ШРИФТ = os.path.join(ROOT, "gui", "src", "main", "resources", "fonts", "Tektur-Bold.ttf")
ОХРА = (107, 68, 19)

ВЕЩИ = [
    (os.path.join(T, "token", "command_center_p1.png"), "Центр управления"),
    (os.path.join(T, "field", "spawn.png"), "Тайл зарождения"),
    (os.path.join(T, "icons", "kelium.png"), "Келемий"),
    (os.path.join(T, "icons", "trophy.png"), "Трофей"),
]

Ш, В = 2400, 620
ВЫСОТА_КАРТИНКИ = 440
im = Image.new("RGBA", (Ш, В), (0, 0, 0, 0))
d = ImageDraw.Draw(im)
f = ImageFont.truetype(ШРИФТ, 58)
ячейка = Ш // len(ВЕЩИ)
for i, (путь, подпись) in enumerate(ВЕЩИ):
    к = Image.open(путь).convert("RGBA")
    масштаб = min(ВЫСОТА_КАРТИНКИ / к.height, (ячейка - 60) / к.width)
    к = к.resize((round(к.width * масштаб), round(к.height * масштаб)), Image.LANCZOS)
    cx = i * ячейка + ячейка // 2
    im.alpha_composite(к, (cx - к.width // 2, (ВЫСОТА_КАРТИНКИ - к.height) // 2 + 10))
    d.text((cx, ВЫСОТА_КАРТИНКИ + 90), подпись, font=f, fill=ОХРА, anchor="mm")

png = os.path.join(D, "_об-игре.png")
im.save(png)
figure("об-игре", png, Ш, im.width, im.height, [], css_w="96%", pad="2mm 0 1mm")
print("ok", im.size)
