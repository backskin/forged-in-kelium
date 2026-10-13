# -*- coding: utf-8 -*-
"""КУЧА ЖЕТОНОВ для задника коробки (дизайнер 14.10.2026, по образцу
«Звёздные войны: Восстание» — «Более 150 пластиковых фигурок!»).

Настоящие текстуры жетонов (data/textures/token, data/textures/module)
кладутся на стол под углом: поворот в плоскости стола, сжатие по высоте
(взгляд сверху под углом), толщина — торец цвета каймы жетона, светлая
фаска по верхнему краю, мягкая тень. Стопки, россыпь и жетоны, прислонённые
к стопкам; спереди — объёмные кубики и монеты (illustrations/компоненты-3д).

Запуск: python куча_жетонов.py → tools/книга/куча-жетонов.png (прозрачный фон)
"""
import os
import random

import cv2
import numpy as np
from PIL import Image

D = os.path.dirname(os.path.abspath(__file__))
КОРЕНЬ = os.path.dirname(os.path.dirname(D))
Т = os.path.join(КОРЕНЬ, "data", "textures")
ОБЪЁМ = os.path.join(КОРЕНЬ, "illustrations", "компоненты-3д")
ВЫХОД = os.path.join(D, "куча-жетонов.png")

W, H = 2600, 1500
НАКЛОН = 0.56            # сжатие по высоте: стол виден сверху под углом ~56°
ТОЛЩ = 0.075             # толщина жетона в долях его ширины (как на печати)
random.seed(7)


def загрузить(путь, ширина):
    im = Image.open(путь).convert("RGBA")
    k = ширина / im.width
    return np.array(im.resize((round(im.width * k), round(im.height * k)), Image.LANCZOS)).astype(np.float32)


def цвет_каймы(rgba):
    a = rgba[..., 3] > 200
    край = a & ~cv2.erode(a.astype(np.uint8), np.ones((9, 9), np.uint8)).astype(bool)
    return rgba[край][:, :3].mean(axis=0) if край.any() else np.array([90, 90, 90.])


def жетон(rgba, угол, наклон=НАКЛОН, наклон_доп=0.0):
    """Плоская текстура → объёмный жетон под углом. Возвращает RGBA-массив."""
    h, w = rgba.shape[:2]
    толщ = max(4, int(w * ТОЛЩ * (1 - наклон * 0.35)))
    c, s = np.cos(np.radians(угол)), np.sin(np.radians(угол))
    sy = наклон
    # поворот в плоскости стола, потом сжатие по высоте (+ лёгкий сдвиг для прислонённых)
    M = np.array([[c, -s, 0], [s * sy + наклон_доп * c, c * sy + наклон_доп * -s, 0]], np.float32)
    углы = np.array([[0, 0], [w, 0], [0, h], [w, h]], np.float32) - [w / 2, h / 2]
    п = углы @ M[:, :2].T
    x0, y0 = п.min(axis=0)
    x1, y1 = п.max(axis=0)
    ow, oh = int(x1 - x0) + 4, int(y1 - y0) + толщ + 6
    M[:, 2] = [-x0 + 2 - (c * w / 2 - s * h / 2) + (c * w / 2 - s * h / 2), 0]
    M[0, 2] = -x0 + 2 - (M[0, 0] * w / 2 + M[0, 1] * h / 2)
    M[1, 2] = -y0 + 2 - (M[1, 0] * w / 2 + M[1, 1] * h / 2)
    верх = cv2.warpAffine(rgba, M, (ow, oh), flags=cv2.INTER_LINEAR, borderValue=(0, 0, 0, 0))
    маска = верх[..., 3] / 255.0
    кайма = цвет_каймы(rgba)
    out = np.zeros((oh, ow, 4), np.float32)
    # торец: силуэт, протянутый вниз на толщину; темнее книзу
    for k in range(толщ, 0, -1):
        тень = 0.42 + 0.22 * (1 - k / толщ)
        сл = np.zeros_like(маска)
        сл[k:] = маска[:-k]
        цв = np.clip(кайма * тень, 0, 255)
        a = сл[..., None]
        out[..., :3] = out[..., :3] * (1 - a) + цв * a
        out[..., 3] = np.maximum(out[..., 3], сл * 255)
    # тонкая тёмная линия стыка торца и лица
    стык = np.zeros_like(маска)
    стык[1:] = маска[:-1]
    a = стык[..., None] * 0.9
    out[..., :3] = out[..., :3] * (1 - a) + кайма * 0.25 * a
    # лицо
    a = маска[..., None]
    out[..., :3] = out[..., :3] * (1 - a) + верх[..., :3] * a
    out[..., 3] = np.maximum(out[..., 3], маска * 255)
    # фаска: светлая кромка по верхнему краю лица
    m8 = (маска > 0.5).astype(np.uint8)
    кромка = m8 - cv2.erode(m8, np.ones((3, 3), np.uint8))
    сверху = np.zeros_like(m8)
    сверху[:-2] = m8[2:]
    фаска = (кромка & (1 - сверху[:, :])).astype(np.float32)
    фаска = cv2.GaussianBlur(фаска, (3, 3), 0.8)[..., None] * 0.55
    out[..., :3] = out[..., :3] * (1 - фаска) + 255 * фаска
    return out


class Сцена:
    def __init__(self):
        self.холст = np.zeros((H, W, 4), np.float32)
        self.очередь = []          # (низ по y, x, y, rgba) — рисуем от дальних к ближним

    def положить(self, rgba, cx, cy, основание=None, z=0.0):
        """cx, cy — где рисовать низ предмета; основание — y точки на столе
        (по нему порядок «дальний раньше»), z — номер в стопке."""
        основание = cy if основание is None else основание
        self.очередь.append((основание + z * 0.01, cx, cy, rgba))

    def тень(self, rgba, x, y):
        a = rgba[..., 3] / 255.0
        h, w = a.shape
        pad = 40
        big = np.zeros((h + pad * 2, w + pad * 2), np.float32)
        big[pad:pad + h, pad:pad + w] = a
        big = cv2.GaussianBlur(big, (0, 0), 14) * 0.55
        self._слой(np.dstack([np.zeros((*big.shape, 3), np.float32), big[..., None] * 255]),
                   x - pad + 18, y - pad + 26)

    def _слой(self, rgba, x, y):
        h, w = rgba.shape[:2]
        x, y = int(x), int(y)
        X0, Y0, X1, Y1 = max(0, x), max(0, y), min(W, x + w), min(H, y + h)
        if X0 >= X1 or Y0 >= Y1:
            return
        src = rgba[Y0 - y:Y1 - y, X0 - x:X1 - x]
        dst = self.холст[Y0:Y1, X0:X1]
        a = src[..., 3:4] / 255.0
        dst[..., :3] = dst[..., :3] * (1 - a) + src[..., :3] * a
        dst[..., 3:4] = dst[..., 3:4] + (255 - dst[..., 3:4]) * a

    def собрать(self):
        for _, cx, cy, rgba in sorted(self.очередь, key=lambda t: t[0]):
            h, w = rgba.shape[:2]
            x, y = cx - w / 2, cy - h
            self.тень(rgba, x, y)
            self._слой(rgba, x, y)
        return Image.fromarray(np.clip(self.холст, 0, 255).astype(np.uint8), "RGBA")


ЦВЕТА = ["p1", "p2", "p3", "p4"]
РОДЫ = ["infantry", "vehicle", "aircraft", "tower"]


def стопка(сц, путь, ширина, cx, cy, n, угол=None):
    tex = загрузить(путь, ширина)
    угол = random.uniform(-40, 40) if угол is None else угол
    for i in range(n):
        ж = жетон(tex, угол + random.uniform(-6, 6))
        шаг = ширина * ТОЛЩ * (1 - НАКЛОН * 0.35) + 1
        сц.положить(ж, cx + random.uniform(-5, 5), cy - i * шаг, основание=cy, z=i)
        # стопка — один предмет по глубине: подъём учитывается через z


def россыпь(сц, путь, ширина, cx, cy, наклон=НАКЛОН, доп=0.0, угол=None):
    tex = загрузить(путь, ширина)
    ж = жетон(tex, random.uniform(0, 360) if угол is None else угол, наклон, доп)
    сц.положить(ж, cx, cy)


def объёмный(сц, имя, ширина, cx, cy):
    п = os.path.join(ОБЪЁМ, имя + ".png")
    if not os.path.exists(п):
        return
    сц.положить(загрузить(п, ширина), cx, cy)


def main():
    сц = Сцена()
    tok = lambda f: os.path.join(Т, "token", f + ".png")
    # ЗАДНИЙ РЯД — высокие стопки войск четырёх цветов
    # горка: в середине стопки выше и дальше, к краям — ниже и ближе
    x = 470
    for i, цв in enumerate(ЦВЕТА):
        for j, род in enumerate(РОДЫ[:2] if i % 2 == 0 else РОДЫ[2:]):
            к_центру = 1 - abs(x - 1300) / 900
            стопка(сц, tok(f"{род}_{цв}"), 210, x + random.uniform(-30, 30),
                   720 - 90 * к_центру + random.uniform(-40, 40), int(4 + 7 * к_центру + random.randint(0, 2)))
            x += 225
    # СРЕДНИЙ РЯД — здания (большие плоские) и супер-войска
    for i, (f, ш, cx, cy) in enumerate([
            ("barracks_p1", 430, 520, 860), ("factory_p2", 430, 930, 900),
            ("airbase_p3", 430, 1380, 880), ("barracks_p4", 430, 1800, 850),
            ("miner_l1_p2", 300, 2200, 820), ("power_plant_l1_p1", 300, 330, 760)]):
        стопка(сц, tok(f), ш, cx, cy, random.randint(2, 3))
    for f, cx, cy in [("infantry_super", 1150, 760), ("aircraft_super", 1620, 740), ("vehicle_super", 2050, 700)]:
        стопка(сц, tok(f), 230, cx, cy, 2)
    # ПРИСЛОНЁННЫЕ И РОССЫПЬ — войска вперемешку
    for k in range(26):
        род, цв = random.choice(РОДЫ), random.choice(ЦВЕТА)
        россыпь(сц, tok(f"{род}_{цв}"), random.uniform(190, 220),
                random.uniform(300, 2300), random.uniform(950, 1180),
                наклон=random.uniform(0.45, 0.8), доп=random.uniform(-0.25, 0.25))
    # модули
    mods = sorted(f for f in os.listdir(os.path.join(Т, "module")) if f.endswith(".png") and "gold" not in f)
    for k in range(8):
        россыпь(сц, os.path.join(Т, "module", random.choice(mods)), 200,
                random.uniform(350, 2250), random.uniform(1000, 1200))
    # ПЕРЕДНИЙ РЯД — объёмные кубики и монеты
    for имя, ш, cx, cy in [("кубики-келемий", 420, 560, 1330), ("монеты", 340, 980, 1440),
                           ("кубики-энергия", 330, 1330, 1250), ("кубики-боеприпасы", 340, 1720, 1420),
                           ("кубики-трофеи", 360, 2120, 1300), ("кубики-келемий", 300, 1500, 1460)]:
        объёмный(сц, имя, ш, cx, cy)
    сц.собрать().save(ВЫХОД)
    print("готово:", ВЫХОД)


if __name__ == "__main__":
    main()
