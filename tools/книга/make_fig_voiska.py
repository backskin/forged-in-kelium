# -*- coding: utf-8 -*-
"""Рисунок главы 4: жетоны войск и как они встают на гекс.

Верх — четыре печатных жетона войск с подписями; низ — шесть гексов,
нарисованных ДВИЖКОМ (kelium.gui.replay2.СнимокЖетонов): посадка жетона по
секторам не может разойтись с игрой.
"""
import os
import subprocess

from PIL import Image, ImageDraw, ImageFont

D = os.path.dirname(os.path.abspath(__file__))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]

ТОКЕНЫ = r"C:\shared\forged-in-kelium\data\textures\token"
КОРЕНЬ = os.path.join("C:", os.sep, "shared", "forged-in-kelium")
РАННЕР = os.path.join(КОРЕНЬ, "gui", "target", "kelium-runner.jar")
ШРИФТ = r"C:\Windows\Fonts\TekturNarrow-Bold.ttf"

ВОЙСКА = [("infantry_p1", "пехота"), ("vehicle_p1", "техника"),
          ("aircraft_p1", "авиация"), ("tower_p1", "вышка")]

# Гексы рисует движок — они же источник правды о секторах.
гексы = os.path.join(D, "_войска-гексы.png")
# ЗАПУСК ИЗ КОРНЯ РЕПОЗИТОРИЯ: движок ищет data/ относительно рабочей папки,
# и без этого текстуры жетонов не находятся — гексы выходят силуэтами.
subprocess.run(["java", "-Dfile.encoding=UTF-8", "-Djava.awt.headless=true",
                "-cp", РАННЕР, "kelium.gui.replay2.СнимокЖетонов", гексы, "340"],
               check=True, capture_output=True, cwd=КОРЕНЬ)

H = 360
GAP = 92
ims = []
for f, _ in ВОЙСКА:
    im = Image.open(os.path.join(ТОКЕНЫ, f + ".png")).convert("RGBA")
    ims.append(im.resize((round(im.width * H / im.height), H), Image.LANCZOS))

шрифт = ImageFont.truetype(ШРИФТ, 50)
подпись_h = 74
строка_w = sum(i.width for i in ims) + GAP * (len(ims) - 1)

низ = Image.open(гексы).convert("RGBA")
W = max(строка_w, низ.width)
разрыв = 54
canvas = Image.new("RGBA", (W, H + подпись_h + разрыв + низ.height), (0, 0, 0, 0))

x = (W - строка_w) // 2
d = ImageDraw.Draw(canvas)
for im, (_, имя) in zip(ims, ВОЙСКА):
    canvas.paste(im, (x, 0), im)
    w = d.textlength(имя, font=шрифт)
    d.text((x + im.width / 2 - w / 2, H + 14), имя, font=шрифт, fill=(42, 35, 24, 255))
    x += im.width + GAP
canvas.paste(низ, ((W - низ.width) // 2, H + подпись_h + разрыв), низ)

png = os.path.join(D, "_войска.png")
canvas.save(png)
vw, vh = canvas.size
print("холст", canvas.size)

figure("войска", png, 1800, vw, vh, [], css_w="79%", pad="0.4mm 0 1.0mm")
print("ok")
