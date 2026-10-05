# -*- coding: utf-8 -*-
"""Рисунок главы 7: «Выпустить» тремя военными зданиями (дизайнер 05.10.2026).

Казарма, завод и авиабаза синих — в НАСТОЯЩЕМ масштабе (data/components/
sizes.yaml), каждая запитана; справа — что она выпустила по планшету войск
синих: казарма — 1 пехоту, завод — 1 боеприпас, авиабаза — 2 авиации.
"""
import os
import sys

import yaml
from PIL import Image, ImageDraw, ImageFont

D = os.path.dirname(os.path.abspath(__file__))
КОРЕНЬ = os.path.dirname(os.path.dirname(D))
sys.path.insert(0, D)
from контур import обвести  # noqa: E402

ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]
Т = os.path.join(КОРЕНЬ, "data", "textures")
РАЗМ = yaml.safe_load(open(os.path.join(КОРЕНЬ, "data", "components", "sizes.yaml"), encoding="utf-8"))
ТНМ = РАЗМ["печать"]["точек_на_мм"]
К = 1.4                                   # общий масштаб рисунка
шр = ImageFont.truetype(os.path.expanduser("~/.fonts/TekturNarrow-Bold.ttf"), 56)
ОХРА = (107, 68, 19, 255)


def запитать(im, файл):
    """Кубики энергии в ячейки здания: ячейки — жёлтые пятна в .zones.png."""
    import numpy as np
    from scipy import ndimage
    п = os.path.join(Т, "token", файл.replace(".png", ".zones.png"))
    if not os.path.exists(п):
        return im
    z = np.asarray(Image.open(п).convert("RGBA").resize(im.size, Image.NEAREST))
    жёлт = (z[..., 0] > 220) & (z[..., 1] > 220) & (z[..., 2] < 40)
    метки, n = ndimage.label(жёлт)
    куб = Image.open(os.path.join(Т, "icons", "energy.png")).convert("RGBA")
    for k in range(1, n + 1):
        ys, xs = np.nonzero(метки == k)
        if len(xs) < 50:
            continue
        сторона = round(min(np.ptp(xs), np.ptp(ys)) * 0.9)
        к = куб.resize((сторона, сторона), Image.LANCZOS)
        im.alpha_composite(к, (int(xs.mean()) - сторона // 2, int(ys.mean()) - сторона // 2))
    return im


def жетон(файл, тип):
    im = запитать(Image.open(os.path.join(Т, "token", файл)).convert("RGBA"), файл)
    w_мм = РАЗМ["жетоны"][тип][0]
    w = round(w_мм * ТНМ * К)
    return обвести(im.resize((w, round(im.height * w / im.width)), Image.LANCZOS), 3)


def кубик(файл, h=110):
    im = Image.open(os.path.join(Т, "icons", файл)).convert("RGBA")
    return im.resize((round(im.width * h / im.height), h), Image.LANCZOS)


ряды = [
    ("казарма", жетон("barracks_p1.png", "barracks"), [жетон("infantry_p1.png", "infantry")], "1 пехота"),
    ("завод", жетон("factory_p1.png", "factory"), [кубик("ammo.png", 170)], "1 боеприпас"),
    ("авиабаза", жетон("airbase_p1.png", "airbase"),
     [жетон("aircraft_p1.png", "aircraft"), жетон("aircraft_p1.png", "aircraft")], "2 авиации"),
]
ш_зд = max(р[1].width for р in ряды)
ш_вых = max(sum(x.width for x in р[2]) + 30 * (len(р[2]) - 1) for р in ряды)
мерка = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
ш_подп = max(int(мерка.textlength(р[3], font=шр)) for р in ряды)
РЯД = max(max(р[1].height, max(x.height for x in р[2]) + 90) for р in ряды) + 40
W = ш_зд + 260 + 0 + max(ш_вых, ш_подп) + 40
H = РЯД * len(ряды)
ЛЕВО = 10
холст = Image.new("RGBA", (W + ЛЕВО, H), (0, 0, 0, 0))
d = ImageDraw.Draw(холст)
for i, (имя, зд, вых, подп) in enumerate(ряды):
    y = i * РЯД
    ц = y + РЯД // 2
    холст.alpha_composite(зд, (ЛЕВО + ш_зд - зд.width, ц - зд.height // 2))
    x = ЛЕВО + ш_зд + 40
    d.polygon([(x, ц - 26), (x + 120, ц - 26), (x + 120, ц - 56), (x + 180, ц),
               (x + 120, ц + 56), (x + 120, ц + 26), (x, ц + 26)], fill=ОХРА)
    x += 220
    вв = max(im.height for im in вых)
    верх = ц - (вв + 80) // 2
    for im in вых:
        холст.alpha_composite(im, (x, верх + (вв - im.height) // 2))
        x += im.width + 30
    d.text((ЛЕВО + ш_зд + 260, верх + вв + 18), подп, font=шр, fill=ОХРА)

png = os.path.join(D, "_выпуск.png")
холст.save(png)
figure("выпуск", png, 1400, W + ЛЕВО, H, [], css_w="72%", pad="0.2mm 0 0.2mm", в_колонке=True)
print("ok", холст.size)
