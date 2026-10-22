# -*- coding: utf-8 -*-
"""Рисунок главы 1: модуль блокировки боя и его сторона в 3 победных очка.

На этом месте стояла заглушка под иконку, хотя оба жетона давно нарисованы
(замечание дизайнера 15.09.2026). Переход между сторонами показан той же
зелёной стрелкой, что и обороты жетонов в главе 4.
"""
import os

from PIL import Image, ImageDraw, ImageFont

import стрелка

D = os.path.dirname(os.path.abspath(__file__))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]

ТЕКСТУРЫ = r"C:\shared\forged-in-kelium\data\textures\module"
ШРИФТ = r"C:\Windows\Fonts\TekturNarrow-Bold.ttf"

СТОРОНЫ = [("mod_cu_infantry.png", "модуль блокировки боя"),
           ("mod_cu_trophy.png", "3 победных очка")]
H = 300
ЗАЗОР = 230
подпись = ImageFont.truetype(ШРИФТ, 36)

куски = []
for файл, имя in СТОРОНЫ:
    im = Image.open(os.path.join(ТЕКСТУРЫ, файл)).convert("RGBA")
    im = im.resize((round(im.width * H / im.height), H), Image.LANCZOS)
    # ЯЧЕЙКА ШИРЕ ПОДПИСИ: иначе длинное название обрезается по краю холста.
    мерка = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
    к = Image.new("RGBA", (max(im.width, round(мерка.textlength(имя, font=подпись)) + 24),
                           H + 52), (0, 0, 0, 0))
    к.paste(im, ((к.width - im.width) // 2, 0), im)
    d = ImageDraw.Draw(к)
    ш = d.textlength(имя, font=подпись)
    d.text(((к.width - ш) / 2, H + 8), имя, font=подпись, fill=(42, 35, 24, 255))
    куски.append(к)

W = sum(к.width for к in куски) + ЗАЗОР
холст = Image.new("RGBA", (W, куски[0].height), (0, 0, 0, 0))
холст.paste(куски[0], (0, 0), куски[0])
холст.paste(куски[1], (куски[0].width + ЗАЗОР, 0), куски[1])
стрелка.нарисовать(холст, куски[0].width + 46, куски[0].width + ЗАЗОР - 40, H // 2)
png = os.path.join(D, "_модуль-блокировки.png")
холст.save(png)

figure("модуль-блокировки", png, 900, холст.width, холст.height, [],
       css_w="86%", pad="1.2mm 0 1.4mm", в_колонке=True)
print("ok", холст.size)
