# -*- coding: utf-8 -*-
"""Стрелка перехода «лицо → оборот», «до → после»: одна на все рисунки книги.

С 14.10.2026 — толстая иконка-стрелка из экспорта дизайнера (№ 81,
rules/иконки-экспорт/стрелка.png): серый градиент в чёрной обводке, тот же
технологичный стиль, что у всех значков игры. Дизайнер попросил стрелки
«толстенные» и одного стиля везде.
"""
import math
import os

from PIL import Image

ИКОНКА = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
                      "rules", "иконки-экспорт", "стрелка.png")


def между(холст, x0, y0, x1, y1, высота=None):
    """Стрелка от (x0, y0) к (x1, y1) в пропорциях иконки. Если поперёк она
    выше {@code высота}, стрелка уменьшается целиком (остаётся по центру)."""
    им = Image.open(ИКОНКА).convert("RGBA")
    длина = max(4, math.hypot(x1 - x0, y1 - y0))
    поперёк = длина * им.height / им.width
    if высота and поперёк > высота:
        длина, поперёк = длина * высота / поперёк, высота
    длина, поперёк = round(длина), round(поперёк)
    им = им.resize((длина, поперёк), Image.LANCZOS)
    угол = math.degrees(math.atan2(y1 - y0, x1 - x0))
    if abs(угол) > .5:
        им = им.rotate(-угол, resample=Image.BICUBIC, expand=True)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    холст.alpha_composite(им, (round(cx - им.width / 2), round(cy - им.height / 2)))


def нарисовать(холст, x0, x1, y, толщина=None):
    """Стрелка от x0 до x1 на высоте y (координаты холста)."""
    между(холст, x0, y, x1, y)


ЗАЛИВКА = (226, 229, 236, 255)     # светлый серо-стальной, как у иконки
ОБВОДКА = (32, 32, 36, 255)


def дуга(холст, точки, толщина, заливка=ЗАЛИВКА, обводка=ОБВОДКА):
    """СТРЕЛКА ДВИЖЕНИЯ — дуга по точкам [(x, y), …] одной фигурой: древко
    сужается к хвосту, остриё — острый клин с отогнутыми назад усами; всё
    в одной обводке (дизайнер 14.10.2026: «дугой с обводкой, технологичными,
    острыми»; на с. 38 остриё было отдельно от дуги)."""
    K = 3
    т = толщина * K
    pts = [(x * K, y * K) for x, y in точки]
    # длина пути и точка, где начинается остриё
    длины = [0.0]
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        длины.append(длины[-1] + math.hypot(x1 - x0, y1 - y0))
    L = длины[-1]
    нос = min(т * 3.2, L * .4)
    def в(s):
        j = max(0, min(len(pts) - 2, next((i for i in range(len(длины) - 1) if длины[i + 1] >= s), len(pts) - 2)))
        (x0, y0), (x1, y1) = pts[j], pts[j + 1]
        t = (s - длины[j]) / ((длины[j + 1] - длины[j]) or 1)
        return x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, math.atan2(y1 - y0, x1 - x0)
    n = 80
    лев, прав = [], []
    for i in range(n + 1):
        s = (L - нос) * i / n
        x, y, а = в(s)
        ш = т / 2 * (.45 + .55 * (i / n))           # хвост тоньше, к острию полная толщина
        лев.append((x + math.sin(а) * ш, y - math.cos(а) * ш))
        прав.append((x - math.sin(а) * ш, y + math.cos(а) * ш))
    xb, yb, аb = в(L - нос)
    xe, ye, _ = в(L)
    крыло = т * 1.25
    назад = нос * .28                                # усы отогнуты назад — острее
    ус_л = (xb + math.sin(аb) * крыло - math.cos(аb) * назад, yb - math.cos(аb) * крыло - math.sin(аb) * назад)
    ус_п = (xb - math.sin(аb) * крыло - math.cos(аb) * назад, yb + math.cos(аb) * крыло - math.sin(аb) * назад)
    фигура = лев + [ус_л, (xe, ye), ус_п] + прав[::-1]
    xs, ys = [p[0] for p in фигура], [p[1] for p in фигура]
    поле = т
    ox, oy = min(xs) - поле, min(ys) - поле
    слой = Image.new("RGBA", (round(max(xs) - ox + поле), round(max(ys) - oy + поле)), (0, 0, 0, 0))
    from PIL import ImageDraw
    d = ImageDraw.Draw(слой)
    фигура = [(x - ox, y - oy) for x, y in фигура]
    d.polygon(фигура, fill=заливка, outline=обводка, width=max(2, round(т * .13)))
    d.line(фигура + [фигура[0]], fill=обводка, width=max(2, round(т * .13)), joint="curve")
    мал = слой.resize((слой.width // K, слой.height // K), Image.LANCZOS)
    холст.alpha_composite(мал, (round(ox / K), round(oy / K)))
