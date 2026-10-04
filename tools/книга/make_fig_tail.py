# -*- coding: utf-8 -*-
"""Рисунок главы 7: опустевший тайл зарождения (дизайнер 04.10.2026).

Два кадра движка (kelium.gui.replay2.СнимокСцены): добытчик снимает
последний келемий с оборота стартового тайла → тайл уходит с поля, гекс
свободен, за опустевший оборот — 2 трофея.
"""
import os
import subprocess

from PIL import Image, ImageDraw, ImageFont

D = os.path.dirname(os.path.abspath(__file__))
КОРЕНЬ = os.path.dirname(os.path.dirname(D))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]
РАННЕР = os.path.join(КОРЕНЬ, "gui", "target", "kelium-runner.jar")


def сцена(имя):
    png = os.path.join(D, "_%s.png" % имя)
    subprocess.run(["java", "-Dfile.encoding=UTF-8", "-Djava.awt.headless=true", "-cp", РАННЕР,
                    "kelium.gui.replay2.СнимокСцены", os.path.join(D, "сцены", имя + ".yaml"), png],
                   check=True, capture_output=True, cwd=КОРЕНЬ)
    return Image.open(png).convert("RGBA")


до, после = сцена("тайл-до"), сцена("тайл-после")
куб = Image.open(os.path.join(КОРЕНЬ, "rules", "иконки-экспорт", "кубик-трофея.png")).convert("RGBA")
куб = куб.resize((120, round(куб.height * 120 / куб.width)), Image.LANCZOS)
ШРИФТ = next(п for п in (r"C:\Windows\Fonts\TekturNarrow-Bold.ttf",
                         os.path.expanduser("~/.fonts/TekturNarrow-Bold.ttf")) if os.path.exists(п))
шр = ImageFont.truetype(ШРИФТ, 90)

# кадры в ряд: «до» → стрелка с наградой → «после» (только освободившийся гекс)
зазор = 420
W = до.width + зазор + после.width
H = max(до.height, после.height)
холст = Image.new("RGBA", (W, H), (0, 0, 0, 0))
холст.alpha_composite(до, (0, (H - до.height) // 2))
холст.alpha_composite(после, (до.width + зазор, (H - после.height) // 2))
d = ImageDraw.Draw(холст)
цв = (107, 68, 19, 255)
x0, y = до.width + 40, H // 2 - 40
d.polygon([(x0, y), (x0 + 250, y), (x0 + 250, y - 45), (x0 + 340, y + 40),
           (x0 + 250, y + 125), (x0 + 250, y + 80), (x0, y + 80)], fill=цв)
d.text((x0 + 10, y - 190), "+2", font=шр, fill=цв)
холст.alpha_composite(куб, (x0 + 120, y - 200))
холст.alpha_composite(куб, (x0 + 230, y - 200))
png = os.path.join(D, "_тайл-опустел.png")
холст.save(png)
figure("тайл-опустел", png, 1400, W, H, [], css_w="100%", pad="0.6mm 0 0.6mm", в_колонке=True)
figure("тайл-опустел-крупно", png, 1800, W, H, [], css_w="72%", pad="0.6mm 0 1.4mm")
print("ok", (W, H))
