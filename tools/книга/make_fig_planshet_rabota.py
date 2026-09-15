# -*- coding: utf-8 -*-
"""Иллюстрация главы 4: планшет войск в работе.

Модули лежат на своих ячейках, рядом кубики боеприпасов. Места ячеек взяты
из якорей печатного планшета (data/textures/board/anchors.yaml), а не на глаз:
иначе жетон садится мимо паза.
"""
import io
import os
import random

import yaml
from PIL import Image

D = os.path.dirname(os.path.abspath(__file__))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]

КОРЕНЬ = os.path.join("C:", os.sep, "shared", "forged-in-kelium")
ТЕКСТУРЫ = os.path.join(КОРЕНЬ, "data", "textures")
ЯКОРЯ = os.path.join(ТЕКСТУРЫ, "board", "anchors.yaml")
ДОСКА = (r"C:\shared\Yandex.Disk\Forged in Kelium\Компоненты игрока"
         r"\экспорт-планшеты\планшет-войск-new-2.png")

доска = Image.open(ДОСКА).convert("RGBA")
W, H = доска.size

я = yaml.safe_load(io.open(ЯКОРЯ, encoding="utf-8"))
столбцы = next(b for b in я["boards"] if b["id"] == "troop-p2")["columns"]

# модуль, в какой столбец, на какую ячейку
МОДУЛИ = [
    ("module/mod_red_infantry_vehicle.png", 0, "attack"),
    ("module/mod_blue_a1u2_units_gold.png", 1, "assembly"),
    ("module/mod_red_vehicle_aircraft_gold.png", 2, "attack"),
    ("module/mod_cu_infantry.png", 3, "attack"),
]

НИЗ = 200                       # место под кубики боеприпасов рядом с планшетом
холст = Image.new("RGBA", (W, H + НИЗ), (0, 0, 0, 0))
холст.alpha_composite(доска, (0, 0))

for файл, к, ячейка in МОДУЛИ:
    x, y, ш, в = столбцы[к][ячейка]
    im = Image.open(os.path.join(ТЕКСТУРЫ, файл)).convert("RGBA")
    k = min(ш / im.width, в / im.height) * 0.94
    im = im.resize((round(im.width * k), round(im.height * k)), Image.LANCZOS)
    холст.alpha_composite(im, (x + (ш - im.width) // 2, y + (в - im.height) // 2))

# кубики боеприпасов «под рукой» — врассыпную у нижней кромки планшета
кубик = Image.open(os.path.join(КОРЕНЬ, "rules", "иконки-экспорт",
                                "боеприпас.png")).convert("RGBA")
random.seed(7)
for доля, сдвиг in ((0.14, 0), (0.22, 46), (0.30, -18), (0.62, 24), (0.70, -30),
                    (0.78, 38), (0.86, -6)):
    ш = random.randint(150, 186)
    im = кубик.resize((ш, round(кубик.height * ш / кубик.width)), Image.LANCZOS)
    im = im.rotate(random.randint(-18, 18), resample=Image.BICUBIC, expand=True)
    холст.alpha_composite(im, (round(W * доля), H - 40 + сдвиг))

png = os.path.join(D, "_планшет-в-работе.png")
холст.save(png)
figure("планшет-в-работе", png, 1800, W, H + НИЗ, [], css_w="76%",
       pad="1.0mm 0 1.2mm")
print("ok", (W, H + НИЗ))
