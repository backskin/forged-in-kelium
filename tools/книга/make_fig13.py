# -*- coding: utf-8 -*-
"""Рисунок главы 4: ячейки под планшетом войск — установленная карта
арсенала, два контейнера и свободная ячейка.

ЗАКРЫТЫХ КАРТ АРСЕНАЛА НА РИСУНКЕ НЕТ: в ячейки их не кладут, они лежат в
запасе игрока в любом числе (дизайнер 28.09.2026; в своде это ключ
containers_storage.closed_arsenal_in_cells: false). Ячейки занимают только
установленные карты и контейнеры.

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

доска = os.path.join(G, "Компоненты игрока", "экспорт-планшеты", "планшет-войск-new-2.png")
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

face = карта(os.path.join(G, "Общие компоненты", "экспорт-арсенал", "арсенал-8.png"),
             "арсенал", ширина=арс[0][2])
cont = карта(os.path.join(G, "Общие компоненты", "экспорт-рубашки", "контейнеры.png"),
             "контейнер", ширина=конт[0][2])

# 1) УСТАНОВЛЕННАЯ — в первой ячейке, выдвинута ровно настолько, чтобы планшет
#    закрыл верхний блок карты.
x1 = пазы["arsenal_open"][0][0]
y1 = пазы["arsenal_open"][0][1] - ОТКРЫТО_СКРЫТО
canvas.paste(face, (x1, y1), face)
# 2) ДВА КОНТЕЙНЕРА — во второй ячейке, видны целиком.
x2a, x2b = конт[2][0], конт[3][0]
y2 = конт[2][1]
canvas.paste(cont, (x2a, y2), cont)
canvas.paste(cont, (x2b, y2), cont)
# 3) ТРЕТЬЯ ЯЧЕЙКА ПУСТАЯ — видно, как выглядит свободное место и его значок.

canvas.paste(board, (0, 0), board)
# РЕЗАТЬ НИЖЕ СТРОКИ ПОДПИСЕЙ РОДОВ ВОЙСК: на 380 картинка рассекала их
# пополам, и это читалось как брак кадрирования, а не как приём.
CROP = 610
canvas = canvas.crop((0, CROP, W, H + EXTRA))
# снизу — ровно до края контейнеров и карты, без пустой полосы
canvas = canvas.crop((0, 0, W, canvas.getchannel("A").getbbox()[3] + 12))
png = os.path.join(D, "_ячейки-планшета.png")
canvas.save(png)
vw, vh = canvas.size
print("canvas", canvas.size)

c1 = x1 + face.width // 2
c3 = арс[2][0] + арс[2][2] // 2
низ_знач = vh + 150
marks = [
    ("А", c1, y1 + 300 - CROP, c1, низ_знач),
    ("Б", x2b + cont.width // 2, y2 + 240 - CROP, x2b + cont.width // 2, низ_знач),
    ("В", c3, 850 - CROP, c3, низ_знач),
    ("Г", 2264, 782 - CROP, 2264 - 150, низ_знач),
]
figure("ячейки-планшета", png, 1800, vw, vh, marks, css_w="100%", pad="1.0mm 0 1.6mm")
print("ok")
