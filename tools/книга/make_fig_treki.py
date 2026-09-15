# -*- coding: utf-8 -*-
"""Иллюстрация главы 10: треки технологий в середине партии.

Кубики игроков стоят на ступенях, на вершинах лежат карты супер-арсенала.
Места ячеек и вершин взяты из якорей планшета (data/textures/board/anchors.yaml),
поэтому кубик садится ровно в ячейку, а не рядом с ней.

Кубик технологий — это простой деревянный кубик; рисовать его заново незачем:
берётся печатный кубик трофея (он без символа) и перекрашивается в цвет игрока
с сохранением всей теневой раскладки.
"""
import io
import math
import os

import yaml
from PIL import Image

D = os.path.dirname(os.path.abspath(__file__))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure, скруглить = ns["figure"], ns["скруглить"]

КОРЕНЬ = os.path.join("C:", os.sep, "shared", "forged-in-kelium")
ЯКОРЯ = os.path.join(КОРЕНЬ, "data", "textures", "board", "anchors.yaml")
ДОСКА = (r"C:\shared\Yandex.Disk\Forged in Kelium\Общие компоненты"
         r"\экспорт-планшеты\Планшет научный отдел.png")
СУПЕР = (r"C:\shared\Yandex.Disk\Forged in Kelium\Общие компоненты"
         r"\экспорт-рубашки\арсенал-супер.png")
КУБИК = os.path.join(КОРЕНЬ, "rules", "иконки-экспорт", "кубик-трофея.png")

ЦВЕТА = {
    "синий": (44, 96, 168),
    "красный": (176, 52, 44),
    "зелёный": (36, 122, 52),
    "жёлтый": (206, 154, 34),
}


def кубик(цвет, сторона):
    """Печатный кубик, перекрашенный в цвет игрока: тени и блики те же."""
    im = Image.open(КУБИК).convert("RGBA")
    r, g, b, a = im.split()
    сер = Image.merge("RGB", (r, g, b)).convert("L")
    пиксели = сер.load()
    out = Image.new("RGBA", im.size)
    зап = out.load()
    альфа = a.load()
    cr, cg, cb = цвет
    for y in range(im.height):
        for x in range(im.width):
            v = пиксели[x, y] / 255.0
            # светлее середины — блик, темнее — тень; цвет тянется к обоим краям
            k = 0.45 + v * 1.15
            зап[x, y] = (min(255, round(cr * k)), min(255, round(cg * k)),
                         min(255, round(cb * k)), альфа[x, y])
    return out.resize((сторона, round(im.height * сторона / im.width)), Image.LANCZOS)


доска = Image.open(ДОСКА).convert("RGBA")
W, H = доска.size
я = yaml.safe_load(io.open(ЯКОРЯ, encoding="utf-8"))
нау = next(b for b in я["boards"] if b["id"] == "science")
ЯЧ_Ш, ЯЧ_В = нау["cell"]
ШАГ = нау["step"]
РЯД = нау["row"]
УГОЛ = нау["angle"]

холст = Image.new("RGBA", (W, H), (0, 0, 0, 0))
холст.alpha_composite(доска, (0, 0))

# кто где стоит: трек, ступень (0..3), ряд (0..3), цвет
РАССТАНОВКА = [
    ("left", 0, 0, "красный"), ("left", 0, 2, "синий"), ("left", 1, 0, "красный"),
    ("left", 1, 2, "жёлтый"), ("left", 2, 1, "красный"),
    ("middle", 0, 0, "зелёный"), ("middle", 0, 2, "синий"),
    ("middle", 1, 1, "зелёный"), ("middle", 2, 0, "синий"),
    ("right", 0, 1, "жёлтый"), ("right", 0, 3, "зелёный"),
    ("right", 1, 0, "жёлтый"), ("right", 1, 2, "синий"), ("right", 2, 1, "жёлтый"),
    ("right", 3, 0, "жёлтый"),
]
треки = {t["id"]: t for t in нау["tracks"]}
сторона = round(ЯЧ_Ш * 0.86)
for трек, шаг, ряд, цвет in РАССТАНОВКА:
    ox, oy = треки[трек]["origin"]
    x = ox + ШАГ[0] * шаг + РЯД[0] * ряд
    y = oy + ШАГ[1] * шаг + РЯД[1] * ряд
    им = кубик(ЦВЕТА[цвет], сторона)
    холст.alpha_composite(им, (round(x - им.width / 2), round(y - им.height / 2)))

# карты супер-арсенала на вершинах треков
карта = Image.open(СУПЕР).convert("RGBA")
for трек in треки.values():
    x, y, ш, в = трек["card"]
    им = карта.resize((ш, в), Image.LANCZOS)
    им = скруглить(им, 68.0)
    холст.alpha_composite(им, (x, y))

# ОБРЕЗКА ПО САМОМУ ПЛАНШЕТУ: вокруг него на картинке пустые поля, из-за
# которых на полосе он выходил вдвое мельче, чем мог (15.09.2026).
bb = холст.split()[3].point(lambda v: 255 if v > 8 else 0).getbbox()
if bb:
    холст = холст.crop(bb)
W, H = холст.size
png = os.path.join(D, "_треки.png")
холст.save(png)
figure("треки", png, 2000, W, H, [], css_w="52%", pad="1.0mm 0 1.2mm")
print("ok", (W, H))
