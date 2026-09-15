# -*- coding: utf-8 -*-
"""Иллюстрация мира игры для главы 1.

Картинку выбрал дизайнер (15.09.2026); лежит в репозитории, чтобы вёрстка
не зависела от Яндекс.Диска. Углы скруглены и обрезаны косо — тем же приёмом,
что панели «командной панели».
"""
import os

from PIL import Image, ImageDraw

D = os.path.dirname(os.path.abspath(__file__))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]

SRC = r"C:\shared\forged-in-kelium\rules\иллюстрации\мир.jpg"

im = Image.open(SRC).convert("RGBA")
Ш = 1800
im = im.resize((Ш, round(im.height * Ш / im.width)), Image.LANCZOS)

# косой срез двух углов — как у панелей памятки
с = round(Ш * 0.045)
маска = Image.new("L", im.size, 0)
ImageDraw.Draw(маска).polygon(
    [(с, 0), (im.width, 0), (im.width, im.height - с),
     (im.width - с, im.height), (0, im.height), (0, с)], fill=255)
im.putalpha(маска)

png = os.path.join(D, "_мир.png")
im.save(png)
figure("мир", png, Ш, im.width, im.height, [], css_w="64%", pad="1.0mm 0 1.6mm")
print("ok", im.size)
