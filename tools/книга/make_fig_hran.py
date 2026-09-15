# -*- coding: utf-8 -*-
"""Рисунок главы «Модули»: две стороны жетона модуля хранилища.

Жетон был описан словами и ни разу не показан (замечание дизайнера 15.09.2026).
Кладут его любой стороной вверх, поэтому показаны обе.
"""
import os

from PIL import Image, ImageDraw, ImageFont

D = os.path.dirname(os.path.abspath(__file__))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]

SRC = (r"C:\shared\Yandex.Disk\Forged in Kelium\Общие компоненты"
       r"\экспорт-жетоны-модулей\Жетон улучшения планшета хранилища-%d.png")
ШРИФТ = r"C:\Windows\Fonts\TekturNarrow-Bold.ttf"

СТОРОНЫ = [(1, "сторона энергии"), (2, "сторона ячейки")]
H = 300
ЗАЗОР = 120
подпись = ImageFont.truetype(ШРИФТ, 38)

куски = []
for номер, имя in СТОРОНЫ:
    im = Image.open(SRC % номер).convert("RGBA")
    im = im.resize((round(im.width * H / im.height), H), Image.LANCZOS)
    мерка = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
    к = Image.new("RGBA", (max(im.width, round(мерка.textlength(имя, font=подпись)) + 24),
                           H + 54), (0, 0, 0, 0))
    к.paste(im, ((к.width - im.width) // 2, 0), im)
    d = ImageDraw.Draw(к)
    ш = d.textlength(имя, font=подпись)
    d.text(((к.width - ш) / 2, H + 8), имя, font=подпись, fill=(42, 35, 24, 255))
    куски.append(к)

W = sum(к.width for к in куски) + ЗАЗОР * (len(куски) - 1)
холст = Image.new("RGBA", (W, куски[0].height), (0, 0, 0, 0))
x = 0
for к in куски:
    холст.paste(к, (x, 0), к)
    x += к.width + ЗАЗОР
png = os.path.join(D, "_модуль-хранилища.png")
холст.save(png)

figure("модуль-хранилища", png, 900, холст.width, холст.height, [],
       css_w="64%", pad="0.8mm 0 1.0mm", в_колонке=True)
print("ok", холст.size)
