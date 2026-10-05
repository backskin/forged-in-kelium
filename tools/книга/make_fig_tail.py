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


def разорванный_тайл(высота):
    """Опустевший тайл, разорванный по диагонали неровным краем: две половины
    чуть разъехались — тайл уходит с поля."""
    from PIL import ImageFilter
    import random
    random.seed(7)
    т = Image.open(os.path.join(КОРЕНЬ, "data", "textures", "field", "spawn_start_flipped.png")).convert("RGBA")
    т = т.resize((round(т.width * высота / т.height), высота), Image.LANCZOS)
    w, h = т.size
    # неровная линия разрыва из верхнего правого угла в нижний левый
    шаги = 14
    линия = []
    for k in range(шаги + 1):
        f = k / шаги
        x = w * (0.72 - 0.44 * f) + random.uniform(-0.035, 0.035) * w
        y = h * f
        линия.append((x, y))
    левая = Image.new("L", т.size, 0)
    ImageDraw.Draw(левая).polygon([(0, 0)] + линия + [(0, h)], fill=255)
    правая = Image.new("L", т.size, 0)
    ImageDraw.Draw(правая).polygon([(w, 0)] + линия + [(w, h)], fill=255)
    куски = []
    for маска, угол, сдвиг in ((левая, 7, (-0.06, 0.03)), (правая, -6, (0.06, -0.02))):
        кус = т.copy()
        кус.putalpha(Image.composite(т.getchannel("A"), Image.new("L", т.size, 0), маска))
        кус = кус.rotate(угол, resample=Image.BICUBIC, expand=True)
        куски.append((кус, сдвиг))
    W, H = round(w * 1.35), round(h * 1.25)
    холст = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for кус, (dx, dy) in куски:
        x = (W - кус.width) // 2 + round(dx * w)
        y = (H - кус.height) // 2 + round(dy * h)
        тень = Image.new("RGBA", кус.size, (20, 15, 5, 0))
        тень.putalpha(кус.getchannel("A").point(lambda v: int(v * 0.35)))
        слой = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        слой.alpha_composite(тень, (x + 10, y + 12))
        холст = Image.alpha_composite(холст, слой.filter(ImageFilter.GaussianBlur(8)))
        холст.alpha_composite(кус, (x, y))
    return холст


обрывки = разорванный_тайл(round(после.height * 0.62))
# обрывки — в верхнем левом углу «после», чуть наезжая на пустой гекс
сдвиг_x = обрывки.width // 2
широкий = Image.new("RGBA", (после.width + сдвиг_x, после.height), (0, 0, 0, 0))
широкий.alpha_composite(после, (сдвиг_x, 0))
широкий.alpha_composite(обрывки, (0, 0))
после = широкий
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
figure("тайл-опустел-крупно", png, 1800, W, H, [], css_w="70%", pad="0.4mm 0 0.8mm")
print("ok", (W, H))
