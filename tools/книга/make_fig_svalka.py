# -*- coding: utf-8 -*-
"""Рисунок главы 4: свалка игрока.

Свалка — карта приказов рубашкой вверх рядом с планшетом войск; на неё кладут
уничтоженные жетоны ОБОРОТОМ вверх. И карта, и жетоны настоящие: рубашка из
экспорта карт приказов, обороты — из набора текстур.

ОДИН МАСШТАБ НА ВСЕ ЖЕТОНЫ (замечание дизайнера 04.10.2026: «жетоны
неправильных размеров относительно карты и друг друга»). Текстуры жетонов
нарисованы в общем масштабе — сектор поля одной ширины, — поэтому каждый
жетон умножается на одно и то же число, а не подгоняется под долю карты.
У жетона есть толщина (картонное ребро) и тень на карте.
"""
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

D = os.path.dirname(os.path.abspath(__file__))
КОРЕНЬ = os.path.dirname(os.path.dirname(D))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure, скруглить = ns["figure"], ns["скруглить"]

ТОКЕНЫ = os.path.join(КОРЕНЬ, "data", "textures", "token")
РУБАШКА = os.path.join(КОРЕНЬ, "data", "textures", "card", "orders", "back_blue.png")
ШРИФТ = next(п for п in (r"C:\Windows\Fonts\TekturNarrow-Bold.ttf",
                         os.path.expanduser("~/.fonts/TekturNarrow-Bold.ttf"),
                         os.path.join(КОРЕНЬ, "gui", "src", "main", "resources", "fonts",
                                      "TekturNarrow-Bold.ttf")) if os.path.exists(п))

КШ, КВ = 1100, round(1100 * 600 / 933)   # карта лежит набок: ширина > высоты
МАСШТАБ = 0.60                          # точек жетона на точку текстуры — у всех одно
ТОЛЩИНА = 9                             # ребро картона, точек
# жетон, центр (доля ширины / высоты карты), поворот
ЖЕТОНЫ = [
    ("barracks_trophy.png", 0.36, 0.38, -6),
    ("vehicle_trophy1.png", 0.74, 0.34, 8),
    ("miner_l2_trophy.png", 0.62, 0.74, -10),
    ("infantry_trophy1.png", 0.20, 0.74, 12),
]
ПОЛЕ = 50


def с_толщиной(im):
    """Жетон с картонным ребром: тёмные копии силуэта сдвинуты вниз."""
    w, h = im.size
    out = Image.new("RGBA", (w, h + ТОЛЩИНА), (0, 0, 0, 0))
    силуэт = im.getchannel("A")
    for k in range(ТОЛЩИНА, 0, -1):
        тон = 70 + 6 * (ТОЛЩИНА - k)
        слой = Image.new("RGBA", (w, h), (тон, тон - 8, тон - 22, 255))
        слой.putalpha(силуэт)
        out.alpha_composite(слой, (0, k))
    out.alpha_composite(im, (0, 0))
    return out


карта = Image.open(РУБАШКА).convert("RGBA").rotate(90, expand=True)
карта = скруглить(карта.resize((КШ, КВ), Image.LANCZOS), 70.0)
W, H = КШ + ПОЛЕ * 2, КВ + ПОЛЕ * 2 + 150
холст = Image.new("RGBA", (W, H), (0, 0, 0, 0))


def тень(im, x, y, сдвиг=(12, 16), размытие=10, сила=0.42):
    a = im.getchannel("A").point(lambda v: int(v * сила))
    t = Image.new("RGBA", im.size, (25, 18, 8, 0))
    t.putalpha(a)
    слой = Image.new("RGBA", холст.size, (0, 0, 0, 0))
    слой.alpha_composite(t, (x + сдвиг[0], y + сдвиг[1]))
    return слой.filter(ImageFilter.GaussianBlur(размытие))


холст = Image.alpha_composite(холст, тень(карта, ПОЛЕ, ПОЛЕ, (8, 10), 8, 0.3))
холст.alpha_composite(карта, (ПОЛЕ, ПОЛЕ))
for файл, cx, cy, угол in ЖЕТОНЫ:
    im = Image.open(os.path.join(ТОКЕНЫ, файл)).convert("RGBA")
    im = im.resize((round(im.width * МАСШТАБ), round(im.height * МАСШТАБ)), Image.LANCZOS)
    im = с_толщиной(im).rotate(угол, resample=Image.BICUBIC, expand=True)
    x = ПОЛЕ + round(КШ * cx) - im.width // 2
    y = ПОЛЕ + round(КВ * cy) - im.height // 2
    холст = Image.alpha_composite(холст, тень(im, x, y))
    холст.alpha_composite(im, (x, y))

d = ImageDraw.Draw(холст)
подпись = ImageFont.truetype(ШРИФТ, 96)
ТЕКСТ = "свалка"
ш = d.textlength(ТЕКСТ, font=подпись)
d.text(((W - ш) / 2, КВ + ПОЛЕ * 2 + 10), ТЕКСТ, font=подпись, fill=(107, 68, 19, 255))

png = os.path.join(D, "_свалка.png")
холст.save(png)
figure("свалка", png, 1400, W, H, [], css_w="50%", pad="0.6mm 0 0.6mm", в_колонке=True)
print("ok", (W, H))
