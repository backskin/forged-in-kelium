# -*- coding: utf-8 -*-
"""Врезки к примеру боя (дизайнер 05.10.2026): какие атаки Аксель взял
со своего планшета войск. Три куска синего планшета (файл troop-p3), на
каждом обведена использованная ячейка; номера — как у стрелок на рисунке боя.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

D = os.path.dirname(os.path.abspath(__file__))
КОРЕНЬ = os.path.dirname(os.path.dirname(D))
sys.path.insert(0, D)
from контур import обвести  # noqa: E402

ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]
шр = ImageFont.truetype(os.path.expanduser("~/.fonts/TekturNarrow-Bold.ttf"), 40)
номер_шр = ImageFont.truetype(os.path.expanduser("~/.fonts/TekturNarrow-Bold.ttf"), 44)
АТАКА = (176, 58, 46, 255)
ОХРА = (107, 68, 19, 255)

пл = Image.open(os.path.join(КОРЕНЬ, "data", "textures", "board", "troop-p3.png")).convert("RGBA")
# (номер, колонка рода по x, ячейка атаки, подпись) — координаты планшета 3354×886
КУСКИ = [
    ("1", (880, 1640), (1284, 454, 1580, 750), "техника:\nспециальная"),
    ("2", (40, 800), (228, 454, 402, 750), "пехота:\nуниверсальная"),
    ("3", (2490, 3250), (2672, 454, 2846, 750), "вышка:\nуниверсальная"),
]
Y0, Y1 = 340, 770
ВЫС = 300
куски = []
for н, (x0, x1), (a, b, c, d), подпись in КУСКИ:
    # только использованная ячейка с полями (узко — три врезки встают в
    # колонку текста примера)
    x0, x1 = a - 40, c + 40
    к = пл.crop((x0, Y0 + 90, x1, Y1))
    dr = ImageDraw.Draw(к)
    dr.rounded_rectangle((a - x0 - 8, b - Y0 - 98, c - x0 + 8, d - Y0 - 82), radius=26,
                         outline=(247, 241, 225, 255), width=22)
    dr.rounded_rectangle((a - x0 - 8, b - Y0 - 98, c - x0 + 8, d - Y0 - 82), radius=26,
                         outline=АТАКА, width=10)
    к = обвести(к.resize((round(к.width * ВЫС / к.height), ВЫС), Image.LANCZOS), 4)
    куски.append((н, к, подпись))

зазор = 40
мерка = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
кол = [max(к.width + 40, max(int(мерка.textlength(стр, font=шр)) for стр in п.split("\n")) + 20)
       for _, к, п in куски]
W = sum(кол) + зазор * (len(куски) - 1)
H = ВЫС + 40 + 120
холст = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(холст)
x = 0
for (н, к, подпись), w in zip(куски, кол):
    kx = x + (w - к.width) // 2 + 14
    холст.alpha_composite(к, (kx, 34))
    # номер атаки — как на стрелке, на углу врезки, не закрывая ячейку
    r = 30
    cx, cy = kx - 4, 34
    d.ellipse((cx - r - 6, cy - r - 6, cx + r + 6, cy + r + 6), fill=(247, 241, 225, 255))
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=АТАКА)
    d.text((cx, cy), н, font=номер_шр, fill=(255, 255, 255, 255), anchor="mm")
    d.multiline_text((x + w // 2, ВЫС + 34 + 12), подпись, font=шр, fill=ОХРА, anchor="ma",
                     align="center", spacing=2)
    x += w + зазор
png = os.path.join(D, "_бой-атаки.png")
холст.save(png)
figure("бой-атаки", png, 1600, W, H, [], css_w="100%", pad="1.6mm 0 0.4mm", в_колонке=True)
print("ok", холст.size)
