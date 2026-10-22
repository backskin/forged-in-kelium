# -*- coding: utf-8 -*-
"""Картинки низа столбцов цикла раунда (глава 5).

Управление — 4 круга: 4 карты приказа рубашкой вверх в ряд, под каждой номер
круга. Возвращение — 5 карт приказа веером: все карты снова в руке.
Карты — настоящие рубашки из набора текстур.
"""
import os

from PIL import Image, ImageDraw, ImageFont, ImageFilter

D = os.path.dirname(os.path.abspath(__file__))
РУБАШКА = r"C:\shared\forged-in-kelium\data\textures\card\orders\back_blue.png"
ШРИФТ = r"C:\Windows\Fonts\Tektur-ExtraBold.ttf"
ЗЕЛЁНЫЙ, СВЕТЛЫЙ = (24, 108, 36, 255), (247, 241, 225, 255)

КШ = 200
карта = Image.open(РУБАШКА).convert("RGBA")
карта = карта.resize((КШ, round(карта.height * КШ / карта.width)), Image.LANCZOS)
маска = Image.new("L", карта.size, 0)
ImageDraw.Draw(маска).rounded_rectangle((0, 0, карта.width - 1, карта.height - 1), 14, fill=255)
карта.putalpha(маска)
КВ = карта.height


def с_тенью(холст, im, x, y):
    тень = Image.new("RGBA", im.size, (0, 0, 0, 0))
    тень.putalpha(im.getchannel("A").point(lambda a: a * 0.4))
    тень = тень.filter(ImageFilter.GaussianBlur(7))
    холст.alpha_composite(тень, (x + 6, y + 9))
    холст.alpha_composite(im, (x, y))


# ---- 4 круга
ЗАЗОР, ЧИП = 36, 76
W = 4 * КШ + 3 * ЗАЗОР + 40
H = КВ + ЧИП + 60
холст = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(холст)
шрифт = ImageFont.truetype(ШРИФТ, 50)
for k in range(4):
    x = 20 + k * (КШ + ЗАЗОР)
    с_тенью(холст, карта, x, 10)
    cx, cy = x + КШ / 2, КВ + 20 + ЧИП / 2 + 6
    d.ellipse((cx - ЧИП / 2, cy - ЧИП / 2, cx + ЧИП / 2, cy + ЧИП / 2), fill=ЗЕЛЁНЫЙ,
              outline=СВЕТЛЫЙ, width=6)
    d.text((cx, cy + 2), str(k + 1), font=шрифт, fill=СВЕТЛЫЙ, anchor="mm")
холст.save(os.path.join(D, "_фаза-круги.png"))

# ---- веер из 5 карт
import math
R = 900
углы = [-16, -8, 0, 8, 16]
W2, H2 = 900, КВ + 170
холст = Image.new("RGBA", (W2, H2), (0, 0, 0, 0))
for у in углы:
    im = карта.rotate(-у, resample=Image.BICUBIC, expand=True)
    cx = W2 / 2 + R * math.sin(math.radians(у))
    cy = 40 + КВ / 2 + R * (1 - math.cos(math.radians(у)))
    с_тенью(холст, im, round(cx - im.width / 2), round(cy - im.height / 2))
холст = холст.crop(холст.getbbox())
холст.save(os.path.join(D, "_фаза-рука.png"))
print("ok")
