# -*- coding: utf-8 -*-
"""Рисунки главы 5 для пяти действий-развилок (28.09.2026).

  * «карта-приказа» — карта ОСВОИТЬ с выносками-цифрами;
  * «круг» — пример круга на четверых: четыре вскрытые карты, обведено то,
    что игрок разыгрывает.

Карты — печатные лица дизайнера из выпуска 27.09 «пять развилок».
"""
import os

from PIL import Image, ImageDraw, ImageFont

D = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(D))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure, скруглить = ns["figure"], ns["скруглить"]

ЛИЦА = os.path.join(os.path.expanduser("~"), "Yandex.Disk", "Forged in Kelium",
                    "комплект — пять развилок (27.09.2026)", "лица", "orders")
ШРИФТЫ = os.path.join(ROOT, "gui", "src", "main", "resources", "fonts")
ЖИРНЫЙ = os.path.join(ШРИФТЫ, "Tektur-Bold.ttf")
УЗКИЙ = os.path.join(ШРИФТЫ, "TekturNarrow-Bold.ttf")


def лицо(имя):
    im = Image.open(os.path.join(ЛИЦА, имя + ".png")).convert("RGBA")
    return im.resize((661, round(im.height * 661 / im.width)), Image.LANCZOS)


# ---------------------------------------------------------- карта с выносками
карта = скруглить(лицо("yellow_settle"), 56.0)
путь = os.path.join(D, "_карта-приказа-5.png")
карта.save(путь)
figure("карта-приказа", путь, 560, карта.width, карта.height, [
    ("1", 330, 66, 740, 66),
    ("2", 70, 166, -80, 166),
    ("3", 330, 166, 740, 190),
    ("4", 578, 166, 740, 300),
    ("5", 171, 331, -80, 331),
    ("6", 331, 556, 740, 556),
    ("7", 78, 683, -80, 683),
    ("8", 166, 834, -80, 834),
    ("9", 331, 954, 740, 954),
], css_w="86%", pad="1.0mm 0 1.2mm", карта_мм=None, в_колонке=True)

# ---------------------------------------------------------- круг на четверых
КРУГ = [
    ("blue_settle", "Аксель", "Добыча · Питание", (0, 1)),
    ("red_advance", "Бриана", "Снабжение · Командование", (0, 1)),
    ("green_secure", "Вэнс", "Развитие · Добыча", (1, 2)),
    ("yellow_advance", "Глория", "Командование", (1,)),
]
ЯЧЕЙКИ = [(0.26, 0.302), (0.735, 0.302), (0.25, 0.788), (0.725, 0.788)]
Ш = 520
В = round(Ш * 1028 / 661)
ЗАЗОР, ШАПКА, ПОДПИСЬ = 64, 96, 110
имяШ, делоШ, номерШ = (ImageFont.truetype(ЖИРНЫЙ, 46), ImageFont.truetype(УЗКИЙ, 38),
                       ImageFont.truetype(ЖИРНЫЙ, 52))
W = Ш * len(КРУГ) + ЗАЗОР * (len(КРУГ) - 1)
H = ШАПКА + В + ПОДПИСЬ
холст = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(холст)
for k, (файл, имя, дело, играет) in enumerate(КРУГ):
    x = k * (Ш + ЗАЗОР)
    к = скруглить(лицо(файл).resize((Ш, В), Image.LANCZOS), 56.0)
    холст.paste(к, (x, ШАПКА), к)
    for я in играет:
        дx, дy = ЯЧЕЙКИ[я]
        cx, cy = x + Ш * дx, ШАПКА + В * дy
        r = Ш * 0.165
        d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=(24, 108, 36, 255),
                  width=round(Ш * 0.018))
    r = 38
    cx, cy = x + r + 6, ШАПКА - r - 12
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(24, 108, 36, 255))
    d.text((cx, cy), str(k + 1), font=номерШ, fill=(247, 241, 225, 255), anchor="mm")
    d.text((x + Ш / 2, ШАПКА + В + 36), имя, font=имяШ, fill=(42, 35, 24, 255), anchor="mm")
    d.text((x + Ш / 2, ШАПКА + В + 86), дело, font=делоШ, fill=(107, 68, 19, 255), anchor="mm")
путь = os.path.join(D, "_круг.png")
холст.save(путь)
figure("круг", путь, 2000, W, H, [], css_w="100%", pad="1.2mm 0 3mm")
print("ok")
