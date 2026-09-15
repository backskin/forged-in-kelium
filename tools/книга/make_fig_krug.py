# -*- coding: utf-8 -*-
"""Рисунок главы 5: круг на четверых — четыре вскрытые карты приказов.

Карты НАСТОЯЩИЕ, из экспорта дизайнера, и у каждого игрока свой цвет. Это
важнее, чем кажется: в колоде всего четыре пары «выбранный / чужой приказ»
на цвет, и придуманные пары вроде «Разработка / Приобретения» у синего просто
не существуют. Пример в тексте главы собран по этим же картам.
"""
import os

from PIL import Image, ImageDraw, ImageFont

D = os.path.dirname(os.path.abspath(__file__))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure, скруглить = ns["figure"], ns["скруглить"]

SRC = (r"C:\shared\Yandex.Disk\Forged in Kelium\Компоненты игрока"
       r"\экспорт-карты-приказов\карты-приказов-new-%d.png")
ЖИРНЫЙ = r"C:\Windows\Fonts\Tektur-Bold.ttf"
УЗКИЙ = r"C:\Windows\Fonts\TekturNarrow-Bold.ttf"

# номер карты в экспорте, имя игрока, что он разыгрывает
КРУГ = [
    (1, "Аксель", "Стройка · Смена энергии"),
    (7, "Бриана", "Рынок · Наука"),
    (12, "Вэнс", "Манёвр · Бой · Стройка"),
    (15, "Глория", "Наука · Смена энергии"),
]

Ш = 520                      # ширина карты в рисунке
В = round(Ш * 1028 / 661)
ЗАЗОР = 64
ШАПКА = 96                   # место под кружок с номером
ПОДПИСЬ = 104

имяШ = ImageFont.truetype(ЖИРНЫЙ, 46)
делоШ = ImageFont.truetype(УЗКИЙ, 40)
номерШ = ImageFont.truetype(ЖИРНЫЙ, 52)

W = Ш * len(КРУГ) + ЗАЗОР * (len(КРУГ) - 1)
H = ШАПКА + В + ПОДПИСЬ
холст = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(холст)

for k, (номер, имя, дело) in enumerate(КРУГ):
    x = k * (Ш + ЗАЗОР)
    карта = Image.open(SRC % номер).convert("RGBA").resize((Ш, В), Image.LANCZOS)
    карта = скруглить(карта, 56.0)            # карта приказа печатается 56 мм
    холст.paste(карта, (x, ШАПКА), карта)
    # кружок с порядковым номером хода
    r = 38
    cx, cy = x + r + 6, ШАПКА - r - 12
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(24, 108, 36, 255))
    ш = d.textlength(str(k + 1), font=номерШ)
    d.text((cx - ш / 2, cy - 34), str(k + 1), font=номерШ, fill=(247, 241, 225, 255))
    ш = d.textlength(имя, font=имяШ)
    d.text((x + (Ш - ш) / 2, ШАПКА + В + 14), имя, font=имяШ, fill=(42, 35, 24, 255))
    ш = d.textlength(дело, font=делоШ)
    d.text((x + (Ш - ш) / 2, ШАПКА + В + 62), дело, font=делоШ, fill=(107, 68, 19, 255))

png = os.path.join(D, "_круг.png")
холст.save(png)
figure("круг", png, 2000, W, H, [], css_w="100%", pad="1.2mm 0 1.4mm")
print("ok", (W, H))
