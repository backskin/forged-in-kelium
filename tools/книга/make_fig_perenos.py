# -*- coding: utf-8 -*-
"""Рисунок главы 10: перенос модуля (дизайнер 14.10.2026).

Вырезка планшета войск жёлтых (troop-p4) — две ячейки специальной атаки,
пехоты и техники. На ячейке пехоты — полупрозрачный «бывший» модуль боя,
дуговая стрелка, на ячейке техники — тот же модуль, объёмный, с торцом
и тенью, как жетоны на полях книги. Цвет стрелки — фиолетовый, в книге
им больше ничего не показано.
"""
import math
import os

import yaml
from PIL import Image, ImageDraw, ImageFilter

D = os.path.dirname(os.path.abspath(__file__))
КОРЕНЬ = os.path.dirname(os.path.dirname(D))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]

ПЛАНШЕТ = os.path.join(КОРЕНЬ, "data", "textures", "board", "troop-p4.png")
МОДУЛЬ = os.path.join(os.path.expanduser("~"), "Yandex.Disk", "Forged in Kelium", "Общие компоненты",
                      "экспорт-жетоны-модулей", "Жетон модулей боя-1.png")
ЦВЕТ = (123, 63, 160, 255)

якоря = yaml.safe_load(open(os.path.join(КОРЕНЬ, "data", "textures", "board", "anchors.yaml"), encoding="utf-8"))
доска = next(b for b in якоря["boards"] if b.get("id") == "troop-p4")
кол = {c["unit"]: c["attack"] for c in доска["columns"]}
откуда, куда = кол["infantry"], кол["vehicle"]

пл = Image.open(ПЛАНШЕТ).convert("RGBA")
x0, x1 = откуда[0] - 90, куда[0] + куда[2] + 90
y0, y1 = откуда[1] - 150, откуда[1] + откуда[3] + 60
холст = пл.crop((x0, y0, x1, y1))

с = round(откуда[2] * 1.02)
мод = Image.open(МОДУЛЬ).convert("RGBA").resize((с, с), Image.LANCZOS)


def центр(r):
    return r[0] - x0 + r[2] / 2, r[1] - y0 + r[3] / 2


# бывшее место — полупрозрачный модуль
пр = мод.copy()
пр.putalpha(мод.getchannel("A").point(lambda a: a * 0.35))
cx, cy = центр(откуда)
холст.alpha_composite(пр, (round(cx - с / 2), round(cy - с / 2)))

# новое место — объёмный модуль: тень, торец, лицо
nx, ny = центр(куда)
толщ = 14
тень = Image.new("RGBA", холст.size, (0, 0, 0, 0))
тень.paste((0, 0, 0, 140), (round(nx - с / 2) + 18, round(ny - с / 2) + 26), мод.getchannel("A"))
холст.alpha_composite(тень.filter(ImageFilter.GaussianBlur(14)))
торец = Image.new("RGBA", мод.size, (60, 20, 20, 255))
торец.putalpha(мод.getchannel("A"))
for k in range(толщ, 0, -1):
    холст.alpha_composite(торец, (round(nx - с / 2), round(ny - с / 2) + k))
холст.alpha_composite(мод, (round(nx - с / 2), round(ny - с / 2) - 4))

# дуговая стрелка над ячейками
d = ImageDraw.Draw(холст)
ax, bx = cx + с * 0.15, nx - с * 0.15
верх = min(cy, ny) - с * 0.55
точки = []
for i in range(41):
    t = i / 40
    x = (1 - t) ** 2 * ax + 2 * (1 - t) * t * (ax + bx) / 2 + t ** 2 * bx
    y = (1 - t) ** 2 * (cy - с * 0.45) + 2 * (1 - t) * t * верх + t ** 2 * (ny - с * 0.55)
    точки.append((x, y))
(ex, ey), (px, py) = точки[-1], точки[-4]
уг = math.atan2(ey - py, ex - px)
dl, dw = 70, 46
остриё = [(ex, ey),
          (ex - dl * math.cos(уг) + dw * math.sin(уг), ey - dl * math.sin(уг) - dw * math.cos(уг)),
          (ex - dl * math.cos(уг) - dw * math.sin(уг), ey - dl * math.sin(уг) + dw * math.cos(уг))]
# ОДНОЙ ФИГУРОЙ (дизайнер 14.10.2026): белая подложка общая у дуги и острия,
# остриё ложится на конец дуги без своей каймы — нет «палочки и треугольника»
БЕЛ = (255, 255, 255, 230)
d.line(точки[:-3], fill=БЕЛ, width=30, joint="curve")
d.polygon(остриё, fill=БЕЛ, outline=БЕЛ, width=12)
d.line(точки[:-3], fill=ЦВЕТ, width=18, joint="curve")
d.polygon(остриё, fill=ЦВЕТ)

png = os.path.join(D, "_перенос-модуля.png")
холст.save(png)
figure("перенос-модуля", png, 1400, холст.width, холст.height, [], css_w="74%", pad="0.4mm 0 0.8mm",
       в_колонке=True)
print("ok", холст.size)
