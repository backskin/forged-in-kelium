# -*- coding: utf-8 -*-
"""СМЕНА НАЗВАНИЯ НА ГОТОВЫХ ОБЛОЖКАХ (04.10.2026): «С небес на землю» →
«Кристалл Раздора».

Чистого арта без надписи в репозитории нет (он на Диске дизайнера), поэтому
старая надпись стирается с готовой обложки: её маска известна точно — это
альфа «надпись названия.png», поставленная туда же, куда её ставил
make_cover.py, — и место заливается заплаткой (OpenCV inpaint). Поверх
кладётся новая надпись теми же кристаллическими буквами make_cover.py.

    python tools/обложка/сменить_название.py

Запускается ОДИН раз, по обложкам со старой надписью (уже выполнен 04.10.2026):
на обложке с новой надписью маска старой не совпадёт.
"""
import os

import cv2
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ПАПКА = os.path.join(ROOT, "design-docs", "обложка")
НОВОЕ = ["КРИСТАЛЛ", "РАЗДОРА"]

# функции make_cover.py без его main()
ns = {"__file__": os.path.join(ROOT, "tools", "обложка", "make_cover.py")}
код = open(ns["__file__"], encoding="utf-8").read()
exec(код[:код.rindex("\nmain()")], ns)
кристалл = ns["кристалл"]


def надпись(строки, ширина):
    """Обе строки одним кеглем: «РАЗДОРА» почти во всю ширину «КРИСТАЛЛ»
    и накрывает место прежней второй строки целиком."""
    ks = [кристалл(s, 400) for s in строки]
    k = ширина / max(x.width for x in ks)
    ks = [x.resize((round(x.width * k), round(x.height * k)), Image.LANCZOS) for x in ks]
    зазор = round(ks[0].height * -0.10)
    out = Image.new("RGBA", (max(x.width for x in ks), sum(x.height for x in ks) + зазор), (0, 0, 0, 0))
    y = 0
    for x in ks:
        out.alpha_composite(x, ((out.width - x.width) // 2, y))
        y += x.height + зазор
    return out
старая = Image.open(os.path.join(ПАПКА, "надпись названия.png")).convert("RGBA")


def переписать(имя, доля, y):
    im = Image.open(os.path.join(ПАПКА, имя)).convert("RGBA")
    S = im.width
    ш = round(S * доля)
    м = старая.resize((ш, round(старая.height * ш / старая.width)), Image.LANCZOS)
    x = (S - ш) // 2
    маска = np.zeros((im.height, S), np.uint8)
    а = (np.asarray(м.getchannel("A")) > 10).astype(np.uint8) * 255
    маска[y:y + а.shape[0], x:x + а.shape[1]] = а
    маска = cv2.dilate(маска, np.ones((11, 11), np.uint8))
    rgb = cv2.cvtColor(np.asarray(im.convert("RGB")), cv2.COLOR_RGB2BGR)
    # inpaint на уменьшенной копии — быстрее и ровнее на большой площади
    k = 2
    мал = cv2.resize(rgb, (S // k, im.height // k), interpolation=cv2.INTER_AREA)
    мм = cv2.resize(маска, (S // k, im.height // k), interpolation=cv2.INTER_NEAREST)
    зал = cv2.inpaint(мал, мм, 9, cv2.INPAINT_TELEA)
    зал = cv2.resize(зал, (S, im.height), interpolation=cv2.INTER_CUBIC)
    m3 = (cv2.GaussianBlur(маска, (9, 9), 0)[..., None] / 255.0)
    rgb = (rgb * (1 - m3) + зал * m3).astype(np.uint8)
    out = Image.fromarray(cv2.cvtColor(rgb, cv2.COLOR_BGR2RGB)).convert("RGBA")
    # новая надпись чуть шире старой: накрывает заплатку целиком
    н = надпись(НОВОЕ, round(ш * 1.04))
    # чуть ниже середины старой надписи: низ новой накрывает заплатку над кристаллом
    out.alpha_composite(н, ((S - н.width) // 2, y + (а.shape[0] - н.height) // 2 + round(S * 0.022)))
    return out


for имя, доля, y in (("обложка книги правил.png", 0.64, 140),
                     ("обложка коробки — лицо.png", 0.62, 240)):
    п = os.path.join(ПАПКА, имя)
    переписать(имя, доля, y).convert("RGB").save(п, optimize=True)
    print(п)
надпись(НОВОЕ, 2400).save(os.path.join(ПАПКА, "надпись названия.png"))


def справочник():
    """Обложка справочника: старый логотип «Келемий / Кристалл Раздора» —
    под полупрозрачную полосу в стиле нижней «Справочник», поверх — новая
    надпись кристаллическими буквами."""
    from PIL import ImageDraw
    п = os.path.join(ROOT, "tools", "книга", "обложки-справочника", "справочник-обложка.jpg")
    im = Image.open(п).convert("RGBA")
    S = im.width
    y0, y1 = round(S * 0.045), round(S * 0.385)
    слой = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(слой).rectangle((0, y0, S, y1), fill=(48, 78, 64, 235))
    im.alpha_composite(слой)
    н = надпись(НОВОЕ, round(S * 0.70))
    im.alpha_composite(н, ((S - н.width) // 2, (y0 + y1 - н.height) // 2))
    im.convert("RGB").save(п, quality=92)
    print(п)


справочник()
