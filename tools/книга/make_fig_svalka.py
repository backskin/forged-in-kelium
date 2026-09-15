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
ПОЛЕ = 90
подпись = ImageFont.truetype(ШРИФТ, 40)

# ХОЛСТ ШИРЕ ПОДПИСИ: иначе строка под картой обрезается по краю рисунка.
мерка = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
ТЕКСТ = "карта приказов рубашкой вверх · жетоны оборотом вверх"
W = max(КШ + ПОЛЕ * 2, round(мерка.textlength(ТЕКСТ, font=подпись)) + 40)
H = КВ + ПОЛЕ * 2 + 56
холст = Image.new("RGBA", (W, H), (0, 0, 0, 0))

карта = Image.open(РУБАШКА).convert("RGBA").resize((КШ, КВ), Image.LANCZOS)
карта = скруглить(карта, 56.0)
# лёгкая тень, чтобы карта не сливалась с полосой книги
тень = Image.new("RGBA", (КШ, КВ), (42, 35, 24, 60))
тень = скруглить(тень, 56.0)
ЛЕВО = (W - КШ) // 2
холст.alpha_composite(тень, (ЛЕВО + 10, ПОЛЕ + 12))
холст.alpha_composite(карта, (ЛЕВО, ПОЛЕ))

for файл, доля, cx, cy, угол in ЖЕТОНЫ:
    im = Image.open(os.path.join(ТОКЕНЫ, файл)).convert("RGBA")
    ш = round(КШ * доля)
    im = im.resize((ш, round(im.height * ш / im.width)), Image.LANCZOS)
    im = im.rotate(угол, resample=Image.BICUBIC, expand=True)
    x = ЛЕВО + round(КШ * cx) - im.width // 2
    y = ПОЛЕ + round(КВ * cy) - im.height // 2
    холст.alpha_composite(im, (x, y))

d = ImageDraw.Draw(холст)
ш = d.textlength(ТЕКСТ, font=подпись)
d.text(((W - ш) / 2, КВ + ПОЛЕ * 2 + 4), ТЕКСТ, font=подпись, fill=(107, 68, 19, 255))

png = os.path.join(D, "_свалка.png")
холст.save(png)
figure("свалка", png, 1100, W, H, [], css_w="44%", pad="1.0mm 0 1.0mm", в_колонке=True)
print("ok", (W, H))
