# -*- coding: utf-8 -*-
"""Персонажи наций на белом фоне → PNG без фона.
Фон — связная от краёв область почти белых пикселей; на кромке (3 px)
белый снимается по цвету (color-to-alpha), чтобы не было белой бахромы."""
import os, sys
import numpy as np, cv2
from PIL import Image
P = os.path.expanduser("~/Yandex.Disk/Forged in Kelium/Иллюстрации/фоны и сцены/персонажи")
D = os.path.dirname(os.path.abspath(__file__))
# файлы назвал дизайнер 14.10.2026 (это и есть лица наций с планшетов)
ВЫБОР = {"красный": "seedream-5.0-pro_SINGLE_CHARACTER_PORTRAIT_ON_PURE_WHITE_cut_cleanly_just_below_thц345782_belt_filling_-0.jpg",
         "синий": "seedream-5.0-pro_SINGLE_CHARACTER_PORTRAIT_ON_PURE_WHITE_cut_cleanly_just_below_the_belt_filling_-0.jpg",
         "зелёный": "green.jpg", "жёлтый": "yellow.jpg"}
for имя, н in ВЫБОР.items():
    im = np.array(Image.open(os.path.join(P, н)).convert("RGB")).astype(np.float32)
    mn = im.min(axis=2)
    светлый = (mn > 232).astype(np.uint8)
    n, lab = cv2.connectedComponents(светлый, connectivity=4)
    края = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    фон = np.isin(lab, list(края)).astype(np.uint8)
    # ДЫРЫ между рукой и телом (дизайнер 14.10.2026): замкнутые чисто белые
    # области. Отличие от бликов на белом костюме синей: дыра толстая (в неё
    # влезает круг 31 px), почти без оттенка и лежит ниже плеч (блики — на
    # голове и плечах).
    sd = im.max(axis=2) - mn
    чисто = ((mn > 236) & (sd < 10)).astype(np.uint8)
    n2, lab2, st2, цт = cv2.connectedComponentsWithStats(чисто, connectivity=4)
    края2 = set(np.unique(np.concatenate([lab2[0], lab2[-1], lab2[:, 0], lab2[:, -1]])))
    ядро = cv2.erode(чисто, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (31, 31)))
    for k in range(1, n2):
        if k in края2 or st2[k, 4] < 400 or цт[k][1] < 0.42 * im.shape[0]:
            continue
        m = lab2 == k
        if mn[m].mean() >= 246 and ядро[m].any():
            фон[m] = 1
    фон = cv2.morphologyEx(фон, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    alpha = (1 - фон).astype(np.float32)
    кромка = (cv2.dilate(фон, np.ones((7, 7), np.uint8)) > 0) & (alpha > 0)
    a = np.clip((255 - mn) / (255 - 150), 0, 1)
    alpha[кромка] = a[кромка]
    alpha = cv2.GaussianBlur(alpha, (3, 3), 0.6) * (1 - фон)
    # снять белую примесь с цвета кромки
    aa = np.maximum(alpha, 1e-3)[..., None]
    rgb = np.where(кромка[..., None], np.clip((im - 255 * (1 - aa)) / aa, 0, 255), im)
    out = np.dstack([rgb, alpha * 255]).astype(np.uint8)
    # холст НЕ подрезаем: у всех четырёх он один (1152×1728), и при равной
    # высоте на заднике фигуры выходят одного масштаба
    Image.fromarray(out, "RGBA").save(os.path.join(D, f"{имя}.png"))
    print(имя, out.shape)
