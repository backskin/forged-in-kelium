# -*- coding: utf-8 -*-
"""Рисунок главы 4: свалка игрока.

Свалка — карта приказов рубашкой вверх рядом с планшетом войск; на неё кладут
уничтоженные жетоны ОБОРОТОМ вверх. Здесь и карта, и жетоны настоящие:
рубашка из экспорта карт приказов, обороты — из набора текстур.
"""
import os

from PIL import Image, ImageDraw, ImageFont

D = os.path.dirname(os.path.abspath(__file__))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure, скруглить = ns["figure"], ns["скруглить"]

ТОКЕНЫ = r"C:\shared\forged-in-kelium\data\textures\token"
РУБАШКА = r"C:\shared\forged-in-kelium\data\textures\card\orders\back_blue.png"
ШРИФТ = r"C:\Windows\Fonts\TekturNarrow-Bold.ttf"

# жетон, во сколько от ширины карты, куда (доля ширины/высоты карты), поворот
ЖЕТОНЫ = [
    ("infantry_trophy1.png", 0.26, 0.24, 0.20, -9),
    ("vehicle_trophy1.png", 0.44, 0.63, 0.31, 7),
    ("barracks_trophy.png", 0.50, 0.32, 0.55, -5),
    ("miner_l2_trophy.png", 0.36, 0.70, 0.75, 12),
]

КШ = 700
КВ = round(КШ * 1028 / 661)
ПОЛЕ = 40
подпись = ImageFont.truetype(ШРИФТ, 54)
ТЕКСТ = "карта приказов рубашкой вверх · жетоны оборотом вверх"

# СВАЛКА ЛЕЖИТ ГОРИЗОНТАЛЬНО. Карта приказов на столе кладётся набок
# (просьба дизайнера 15.09.2026): так она занимает меньше высоты полосы,
# и жетоны на ней можно дать крупнее.
карта = Image.open(РУБАШКА).convert("RGBA").resize((КШ, КВ), Image.LANCZOS)
карта = скруглить(карта, 56.0)
слой = Image.new("RGBA", (КШ, КВ), (0, 0, 0, 0))
слой.alpha_composite(карта, (0, 0))
for файл, доля, cx, cy, угол in ЖЕТОНЫ:
    im = Image.open(os.path.join(ТОКЕНЫ, файл)).convert("RGBA")
    ш = round(КШ * доля)
    im = im.resize((ш, round(im.height * ш / im.width)), Image.LANCZOS)
    im = im.rotate(угол, resample=Image.BICUBIC, expand=True)
    слой.alpha_composite(im, (round(КШ * cx) - im.width // 2,
                              round(КВ * cy) - im.height // 2))
слой = слой.rotate(90, expand=True)          # против часовой стрелки

мерка = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
W = max(слой.width + ПОЛЕ * 2, round(мерка.textlength(ТЕКСТ, font=подпись)) + 40)
H = слой.height + ПОЛЕ * 2 + 74
холст = Image.new("RGBA", (W, H), (0, 0, 0, 0))
тень = Image.new("RGBA", (слой.width, слой.height), (0, 0, 0, 0))
ЛЕВО = (W - слой.width) // 2
холст.alpha_composite(слой, (ЛЕВО, ПОЛЕ))

d = ImageDraw.Draw(холст)
ш = d.textlength(ТЕКСТ, font=подпись)
d.text(((W - ш) / 2, слой.height + ПОЛЕ * 2 + 4), ТЕКСТ, font=подпись,
       fill=(107, 68, 19, 255))

png = os.path.join(D, "_свалка.png")
холст.save(png)
figure("свалка", png, 1400, W, H, [], css_w="100%", pad="1.0mm 0 1.0mm",
       в_колонке=True)
print("ok", (W, H))
