# -*- coding: utf-8 -*-
"""Рисунок главы 4: жетоны войск и как они встают на гекс.

Верх — четыре печатных жетона войск с подписями; низ — шесть гексов,
нарисованных ДВИЖКОМ (kelium.gui.replay2.СнимокЖетонов): посадка жетона по
секторам не может разойтись с игрой.
"""
import os
import subprocess

from PIL import Image, ImageFilter, ImageDraw, ImageFont

D = os.path.dirname(os.path.abspath(__file__))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]

ТОКЕНЫ = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "data", "textures", "token")
КОРЕНЬ = os.path.dirname(os.path.dirname(D))
РАННЕР = os.path.join(КОРЕНЬ, "gui", "target", "kelium-runner.jar")
ШРИФТ = os.path.expanduser("~/.fonts/TekturNarrow-Bold.ttf")

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
# ОДИН МАСШТАБ НА ВСЕ ЖЕТОНЫ. Печатные жетоны разного размера (пехота 213×213,
# техника 461×337, авиация 255×236, вышка 378×246 — и экспорт дизайнера, и набор
# текстур сходятся в этих числах). Выравнивать их по высоте — врать о размере:
# пехота становилась ростом с технику (замечание дизайнера 15.09.2026).
исходные = [Image.open(os.path.join(ТОКЕНЫ, f + ".png")).convert("RGBA")
            for f, _ in ВОЙСКА]
к = H / max(im.height for im in исходные)     # техника — самый крупный жетон
ims = [im.resize((round(im.width * к), round(im.height * к)), Image.LANCZOS)
       for im in исходные]

шрифт = ImageFont.truetype(ШРИФТ, 50)
подпись_h = 94
строка_w = sum(i.width for i in ims) + GAP * (len(ims) - 1)

низ = Image.open(гексы).convert("RGBA")
W = max(строка_w, низ.width)
разрыв = 54
canvas = Image.new("RGBA", (W, H + подпись_h + разрыв + низ.height), (0, 0, 0, 0))



def с_торцом(im, рамка=(0x2b, 0x6c, 0xb0)):
    """Толщина картона, как у жетонов на поле (FieldPainter.книжныйТорец):
    мягкая тень и торец строго вниз в цвет рамки, только темнее
    (дизайнер 05.10.2026: жетоны без толщины рядом с объёмными — разнобой)."""
    t = max(3, round(im.width * 0.07))
    out = Image.new("RGBA", (im.width + 2 * t, im.height + 2 * t), (0, 0, 0, 0))
    a = im.getchannel("A")
    тень = Image.new("RGBA", im.size, (20, 16, 10, 0))
    тень.putalpha(a.point(lambda v: int(v * 0.35)))
    слой = Image.new("RGBA", out.size, (0, 0, 0, 0))
    слой.alpha_composite(тень, (t + round(t * 0.9), t + round(t * 1.3)))
    out.alpha_composite(слой.filter(ImageFilter.GaussianBlur(t * 0.8)))
    for i in range(t, 0, -1):
        k = 0.45 + 0.30 * (t - i) / max(1, t - 1)
        с = Image.new("RGBA", im.size, tuple(int(v * k) for v in рамка) + (255,))
        с.putalpha(a)
        out.alpha_composite(с, (t, t + i))
    out.alpha_composite(im, (t, t))
    return out


x = (W - строка_w) // 2
d = ImageDraw.Draw(canvas)
for im, (_, имя) in zip(ims, ВОЙСКА):
    т = с_торцом(im)
    п = (т.width - im.width) // 2
    canvas.alpha_composite(т, (x - п, H - im.height - п))
    w = d.textlength(имя, font=шрифт)
    d.text((x + im.width / 2 - w / 2, H + 34), имя, font=шрифт, fill=(42, 35, 24, 255))
    x += im.width + GAP
canvas.paste(низ, ((W - низ.width) // 2, H + подпись_h + разрыв), низ)

png = os.path.join(D, "_войска.png")
canvas.save(png)
vw, vh = canvas.size
print("холст", canvas.size)

figure("войска", png, 1800, vw, vh, [], css_w="66%", pad="0.4mm 0 0.9mm")
print("ok")
