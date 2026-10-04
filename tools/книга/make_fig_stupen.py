# -*- coding: utf-8 -*-
"""Рисунок главы 9: занятая ступень и прыжок через неё (дизайнер 04.10.2026).

Схема одного трека: ступени 1–4 с ценой и ячейками. У Брианы (красная)
кубик на 1-й ступени, 2-я занята целиком (синий и зелёный) — она платит
3 + 4 и встаёт сразу на 3-ю. Кубики — те же, что в составе игры.
"""
import os

from PIL import Image, ImageDraw, ImageFont

D = os.path.dirname(os.path.abspath(__file__))
КОРЕНЬ = os.path.dirname(os.path.dirname(D))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]
ШРИФТЫ = [os.path.expanduser("~/.fonts"), r"C:\Windows\Fonts"]


def шрифт(имя, р):
    for п in ШРИФТЫ:
        f = os.path.join(п, имя)
        if os.path.exists(f):
            return ImageFont.truetype(f, р)
    raise FileNotFoundError(имя)


жир, узк = шрифт("Tektur-Bold.ttf", 64), шрифт("TekturNarrow-Bold.ttf", 52)
кубы = Image.open(os.path.join(D, "_кубики-технологий.png")).convert("RGBA")
w, h = кубы.size
КУБ = {"синий": кубы.crop((0, 0, w // 2, h // 2)), "красный": кубы.crop((w // 2, 0, w, h // 2)),
       "зелёный": кубы.crop((0, h // 2, w // 2, h)), "жёлтый": кубы.crop((w // 2, h // 2, w, h))}
for к in КУБ:
    КУБ[к] = КУБ[к].crop(КУБ[к].getbbox()).resize((110, 110), Image.LANCZOS)

ЗЕЛ, ОХРА, БУМ, КАНТ = (24, 108, 36, 255), (107, 68, 19, 255), (250, 245, 232, 255), (170, 150, 110, 255)
СТ, ЗАЗ = 330, 40                      # ширина ступени и зазор
W, H = 4 * СТ + 3 * ЗАЗ + 80, 640
холст = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(холст)
# ступень: номер, цена, две ячейки
стоят = {1: ["красный", None], 2: ["синий", "зелёный"], 3: [None, None], 4: [None, None]}
верх = 230
for k in range(1, 5):
    x = 40 + (k - 1) * (СТ + ЗАЗ)
    d.rounded_rectangle((x, верх, x + СТ, верх + 330), radius=26, fill=БУМ, outline=КАНТ, width=5)
    d.rounded_rectangle((x, верх, x + СТ, верх + 80), radius=26, fill=ЗЕЛ)
    d.rectangle((x, верх + 50, x + СТ, верх + 80), fill=ЗЕЛ)
    d.text((x + 24, верх + 8), "ступень %d" % k, font=узк, fill=(255, 255, 255, 255))
    d.text((x + СТ - 30, верх + 8), str(k + 1), font=жир, fill=(255, 225, 120, 255), anchor="ra")
    for j in range(2):
        cx = x + 40 + j * 140
        d.rounded_rectangle((cx, верх + 130, cx + 120, верх + 250), radius=14, outline=КАНТ, width=5)
        if стоят[k][j]:
            холст.alpha_composite(КУБ[стоят[k][j]], (cx + 5, верх + 135))
# новый кубик Брианы на 3-й ступени — бледной рамкой «сюда»
x3 = 40 + 2 * (СТ + ЗАЗ) + 40
холст.alpha_composite(КУБ["красный"], (x3 + 5, верх + 135))
# дуга прыжка 1 → 3 над треком
x1 = 40 + 40 + 60
x3c = x3 + 60
d.arc((x1, 60, x3c, верх + 260), 200, 340, fill=ОХРА, width=14)
d.polygon([(x3c - 30, 165), (x3c + 34, 160), (x3c + 2, 222)], fill=ОХРА)
d.text(((x1 + x3c) // 2, 120), "3 + 4 = 7 трофеев", font=узк, fill=ОХРА, anchor="ma")
# крест на 2-й ступени: «занято»
x2 = 40 + (СТ + ЗАЗ) + СТ // 2
d.text((x2, верх + 270), "занято", font=узк, fill=(176, 58, 46, 255), anchor="ma")
png = os.path.join(D, "_ступень.png")
холст.save(png)
figure("ступень", png, 1400, W, H, [], css_w="100%", pad="0.4mm 0 0.4mm", в_колонке=True)
print("ok", (W, H))
