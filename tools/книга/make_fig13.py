# -*- coding: utf-8 -*-
"""Рисунок главы 13: ячейки под планшетом войск — закрытая карта арсенала,
установленная и два контейнера.

Карты кладутся НЕ НА ГЛАЗОК, а по якорям печатного планшета
(data/textures/board/anchors.yaml, ключ card_slots): там и размер карты
относительно планшета, и точное место паза. Отсюда же берётся, насколько
установленная карта задвинута под планшет: её верхний блок с утиль-эффектом
планшет закрывает целиком, до сине-белой полосы над названием (правило
дизайнера 14.09.2026). Закрытая карта задвинута до упора и видна только
кромкой в вырезе паза.
"""
import os
import yaml
from PIL import Image

D = os.path.dirname(os.path.abspath(__file__))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure, карта = ns["figure"], ns["карта"]

G = r"C:\shared\Yandex.Disk\Forged in Kelium"
ЯКОРЯ = r"C:\shared\forged-in-kelium\data\textures\board\anchors.yaml"

доска = os.path.join(G, "Компоненты игрока", "экспорт-планшеты", "планшет-войск-new-1.png")
board = Image.open(доска).convert("RGBA")
W, H = board.size                      # 3354×886

я = yaml.safe_load(open(ЯКОРЯ, encoding="utf-8"))
troop = next(b for b in я["boards"] if b["id"] == "troop-p1")
пазы = troop["card_slots"]
арс = пазы["arsenal_back"]
конт = пазы["container"]
ОТКРЫТО_СКРЫТО = арс[0][3] - пазы["arsenal_open"][0][3]   # 521 − 388 = 133 px
НИЗ = H - 1                                               # нижняя кромка планшета

EXTRA = 500
canvas = Image.new("RGBA", (W, H + EXTRA), (0, 0, 0, 0))

back = карта(os.path.join(G, "Общие компоненты", "экспорт-рубашки", "арсенал.png"),
             "арсенал", ширина=арс[0][2])
face = карта(os.path.join(G, "Общие компоненты", "экспорт-арсенал", "арсенал-8.png"),
             "арсенал", ширина=арс[1][2])
cont = карта(os.path.join(G, "Общие компоненты", "экспорт-рубашки", "контейнеры.png"),
             "контейнер", ширина=конт[0][2])

# 1) ЗАКРЫТАЯ — лежит НИЖЕ планшета в своём пазу и видна целиком: планшет
#    заходит на неё только самым краем (поправка дизайнера 14.09.2026).
x1, y1 = арс[0][0], арс[0][1]
canvas.paste(back, (x1, y1), back)
# 2) УСТАНОВЛЕННАЯ — выдвинута ровно настолько, чтобы планшет закрыл верхний
#    блок с утиль-эффектом.
x2 = пазы["arsenal_open"][1][0]
y2 = пазы["arsenal_open"][1][1] - ОТКРЫТО_СКРЫТО
canvas.paste(face, (x2, y2), face)
# 3) ДВА КОНТЕЙНЕРА — в третьем пазу, тоже видны целиком.
x3a, x3b = конт[4][0], конт[5][0]
y3 = конт[4][1]
canvas.paste(cont, (x3a, y3), cont)
canvas.paste(cont, (x3b, y3), cont)

canvas.paste(board, (0, 0), board)
CROP = 380
canvas = canvas.crop((0, CROP, W, H + EXTRA))
png = os.path.join(D, "_ячейки-планшета.png")
canvas.save(png)
vw, vh = canvas.size
print("canvas", canvas.size, "закрытая y", y1, "открытая y", y2)

c1 = x1 + back.width // 2
c2 = x2 + face.width // 2
c3 = (x3a + x3b + cont.width) // 2
низ_знач = vh + 150
marks = [
    ("А", c1, y1 + 300 - CROP, c1, низ_знач),
    ("Б", c2, y2 + 300 - CROP, c2, низ_знач),
    ("Д", 2110, 782 - CROP, 2110, низ_знач),
    ("В", x3b + cont.width // 2, y3 + 240 - CROP, x3b + cont.width // 2, низ_знач),
    ("Г", 2980, 816 - CROP, 3120, низ_знач),
]
figure("ячейки-планшета", png, 1800, vw, vh, marks, css_w="100%", pad="6mm 0 13mm")
print("ok")
