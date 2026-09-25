# -*- coding: utf-8 -*-
"""РАСКЛАДКА ЖЕТОНОВ ВОЙСК И ЗДАНИЙ — перезапускать при новом экспорте.

Источник: «Компоненты игрока/экспорт-жетоны» дизайнера. Файлы там
пронумерованы по листу печати, а игра ищет их ПО ИМЕНИ
(kelium.report.Textures): ``<код>[_l<уровень>]_p<гнездо цвета>.png``,
обороты ``_trophy``, ``_trophy1/2``, супер-жетоны ``_super``.

ГНЁЗДА ЦВЕТА (FieldGeometry.SEAT_TOKEN): p1 синий, p2 красный, p3 зелёный,
p4 жёлтый. В экспорте цвет задаёт папка, а номер внутри цвета c (Красный 0,
Зеленый 1, Синий 2, Желтый 3) идёт так:

  «Жетоны войск-N»:  1+4c пехота, 2+4c техника, 3+4c авиация, 4+4c вышка
  «жетоны зданий-N»: добытчик ур.1..4 — 1+c, 5+c, 9+c, 13+c
                     энергостанция ур.1..4 — 17+c, 21+c, 25+c, 29+c
                     казармы 41+c, завод 45+c, авиабаза 49+c, ЦУ 53+c
  _Супер:    войск-17..20 — пехота, техника, авиация, вышка (фиолетовые)
  _оборот:   войск-21..28 — trophy1/trophy2 пехоты, техники, авиации, вышки
             (одна / две шестерни); зданий-33..36 добытчик ур.1..4,
             37..40 энергостанция ур.1..4, 57..60 казармы, завод, авиабаза, ЦУ
  _нейтральные: зданий-61 большая постройка, 62 её оборот, 63 малая, 64 её
             оборот (→ data/textures/field/neutral_*).

ЧТО ПРОЧИТАНО ПО КАРТИНКАМ (экспорт 25.09.2026, листы-коллажи):
  * цвет рамки и подкраски совпадает с папкой; жетоны войск и зданий у
    четырёх цветов теперь РАЗНЫЕ — число сердец и ячеек энергии своё у
    каждой фракции (см. ПЕЧАТЬ ниже, её же сверяет --сверить с boards);
  * добытчики и энергостанции у всех цветов одинаковые (на добытчике
    номер уровня «1lvl..4lvl», зелёные ячейки под кубики, у энергостанции
    жёлтые кубики выработки);
  * на оборотах добытчика и станции ур.1 и 3 кроме шестерён есть ящик
    арсенала, у ур.2 и 4 — только шестерни; у 57 (казармы) ящик и две
    шестерни, 58 (завод) три шестерни, 59 (авиабаза) ящик и три шестерни,
    60 (ЦУ) особый значок и одна шестерня;
  * 61/63 — лицевые нейтральные (у большой две сердца и пехота, у малой
    одно сердце), 62/64 — их обороты.

РАЗМЕР. Игра натягивает картинку на рамку силуэта, поэтому пропорции
обязаны быть те же, что у прежнего файла. Экспорт почти везде совпадает с
прежним пиксель в пиксель; обороты зданий и большая нейтральная выгружены на
пиксель больше (737×429 вместо 736×428) — такие ужимаются до прежнего
полотна. Силуэт (альфа) каждого нового файла сверяется с прежним: совпадение
ниже 0,93 — повод посмотреть глазами, скрипт об этом пишет.

Запуск:  python tools/gen_token_art.py [--проверить] [--сверить]
  --проверить  ничего не пишет, только сверяет силуэты и цвет рамки;
  --сверить    печатает таблицу ПЕЧАТЬ против data/boards/boards.3.0.0.yaml.
Пишет:   data/textures/token/*.png, data/textures/field/neutral_*.png
После:   python tools/gen_token_zones.py  (разметка зон по новым картинкам)
"""
import colorsys
import os
import sys

import numpy as np
from PIL import Image, ImageOps

КОРЕНЬ = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ТОКЕНЫ = os.path.join(КОРЕНЬ, "data", "textures", "token")
ПОЛЕ = os.path.join(КОРЕНЬ, "data", "textures", "field")
ИСТОЧНИК = os.path.join(os.path.expanduser("~"), "Yandex.Disk", "Forged in Kelium",
                        "Компоненты игрока", "экспорт-жетоны")

# папка экспорта → (номер цвета в нумерации листа, гнездо цвета в игре, цвет)
ЦВЕТА = [
    ("Красный", 0, 2, "red"),
    ("Зеленый", 1, 3, "green"),
    ("Синий",   2, 1, "blue"),
    ("Желтый",  3, 4, "yellow"),
]
# тон рамки (HSV hue, градусы) — чтобы не перепутать папку и гнездо
ТОН = {"red": 0, "green": 125, "blue": 220, "yellow": 52}

ВОЙСКА = ["infantry", "vehicle", "aircraft", "tower"]

# ПЕЧАТЬ — что нарисовано на лице жетона (посчитано глазами по листам):
# войска — сердец; здания — (ячеек энергии, сердец).
ПЕЧАТЬ = {
    "red":    {"units": {"infantry": 2, "vehicle": 2, "aircraft": 1, "tower": 2},
               "buildings": {"barracks": (1, 2), "factory": (2, 2),
                             "airbase": (1, 2), "command_center": (1, 3)}},
    "green":  {"units": {"infantry": 2, "vehicle": 2, "aircraft": 1, "tower": 2},
               "buildings": {"barracks": (1, 1), "factory": (1, 2),
                             "airbase": (2, 2), "command_center": (1, 3)}},
    "blue":   {"units": {"infantry": 1, "vehicle": 1, "aircraft": 2, "tower": 1},
               "buildings": {"barracks": (1, 2), "factory": (2, 2),
                             "airbase": (2, 3), "command_center": (1, 3)}},
    "yellow": {"units": {"infantry": 1, "vehicle": 1, "aircraft": 1, "tower": 1},
               "buildings": {"barracks": (2, 1), "factory": (1, 1),
                             "airbase": (1, 1), "command_center": (1, 3)}},
}
# одинаковые у всех цветов: уровень → (ячеек, сердец, кубиков выработки)
ПЕЧАТЬ_ДОБЫТЧИК = {1: (2, 1), 2: (1, 1), 3: (2, 2), 4: (1, 2)}
ПЕЧАТЬ_СТАНЦИЯ = {1: (1, 1), 2: (1, 2), 3: (2, 2), 4: (2, 3)}   # (сердец, кубиков)
# шестерни (очки трофея) на оборотах
ПЕЧАТЬ_ОБОРОТ = {"miner": [1, 1, 2, 2], "power_plant": [1, 1, 2, 2],
                 "barracks": 2, "factory": 3, "airbase": 3, "command_center": 1,
                 "infantry": (1, 2), "vehicle": (1, 2), "aircraft": (1, 2),
                 "tower": (1, 2)}


def раскладка():
    """Список (файл экспорта, куда положить)."""
    out = []
    for папка, c, p, _ in ЦВЕТА:
        for i, род in enumerate(ВОЙСКА):
            out.append((os.path.join(папка, "Жетоны войск-%d.png" % (1 + 4 * c + i)),
                        os.path.join(ТОКЕНЫ, "%s_p%d.png" % (род, p))))
        for ур in range(1, 5):
            out.append((os.path.join(папка, "жетоны зданий-%d.png" % (4 * (ур - 1) + 1 + c)),
                        os.path.join(ТОКЕНЫ, "miner_l%d_p%d.png" % (ур, p))))
            out.append((os.path.join(папка, "жетоны зданий-%d.png" % (16 + 4 * (ур - 1) + 1 + c)),
                        os.path.join(ТОКЕНЫ, "power_plant_l%d_p%d.png" % (ур, p))))
        for j, здание in enumerate(["barracks", "factory", "airbase", "command_center"]):
            out.append((os.path.join(папка, "жетоны зданий-%d.png" % (41 + 4 * j + c)),
                        os.path.join(ТОКЕНЫ, "%s_p%d.png" % (здание, p))))
    for i, род in enumerate(ВОЙСКА):
        out.append((os.path.join("_Супер", "Жетоны войск-%d.png" % (17 + i)),
                    os.path.join(ТОКЕНЫ, "%s_super.png" % род)))
        for k in (1, 2):
            out.append((os.path.join("_оборот", "Жетоны войск-%d.png" % (21 + 2 * i + k - 1)),
                        os.path.join(ТОКЕНЫ, "%s_trophy%d.png" % (род, k))))
    for ур in range(1, 5):
        out.append((os.path.join("_оборот", "жетоны зданий-%d.png" % (32 + ур)),
                    os.path.join(ТОКЕНЫ, "miner_l%d_trophy.png" % ур)))
        out.append((os.path.join("_оборот", "жетоны зданий-%d.png" % (36 + ур)),
                    os.path.join(ТОКЕНЫ, "power_plant_l%d_trophy.png" % ур)))
    for j, здание in enumerate(["barracks", "factory", "airbase", "command_center"]):
        out.append((os.path.join("_оборот", "жетоны зданий-%d.png" % (57 + j)),
                    os.path.join(ТОКЕНЫ, "%s_trophy.png" % здание)))
    for n, имя in ((61, "neutral_big"), (62, "neutral_big_trophy"),
                   (63, "neutral_small"), (64, "neutral_small_trophy")):
        out.append((os.path.join("_нейтральные", "жетоны зданий-%d.png" % n),
                    os.path.join(ПОЛЕ, имя + ".png")))
    return out


def силуэт(img):
    return np.array(img.convert("RGBA"))[..., 3] > 128


def совпадение(a, b):
    return (a & b).sum() / max(1, (a | b).sum())


def тон_рамки(img):
    """Средний тон насыщенных пикселей у края силуэта."""
    a = np.array(img.convert("RGBA")).astype(float)
    м = a[..., 3] > 128
    from scipy import ndimage
    кайма = м & ~ndimage.binary_erosion(м, np.ones((9, 9)))
    rgb = a[кайма][:, :3] / 255.0
    hs = [colorsys.rgb_to_hsv(*px) for px in rgb[:: max(1, len(rgb) // 3000)]]
    hs = [(h, s) for h, s, v in hs if s > 0.45 and v > 0.3]
    if not hs:
        return None
    x = np.mean([np.cos(2 * np.pi * h) for h, _ in hs])
    y = np.mean([np.sin(2 * np.pi * h) for h, _ in hs])
    return (np.degrees(np.arctan2(y, x)) + 360) % 360


def вписать(новый, прежний_размер):
    if прежний_размер is None or новый.size == прежний_размер:
        return новый, "как есть"
    W, H = прежний_размер
    w, h = новый.size
    if abs(w / h - W / H) > 0.01:
        raise SystemExit("пропорции разошлись: %s -> %s" % (новый.size, прежний_размер))
    return новый.resize((W, H), Image.LANCZOS), "%dx%d -> %dx%d" % (w, h, W, H)


def лицо_оборота(куда):
    """Для оборота здания — картинка его лица (синее гнездо), иначе None."""
    имя = os.path.basename(куда)[:-4]
    if not имя.endswith("_trophy"):
        return None
    база = имя[: -len("_trophy")]
    for кандидат in (os.path.join(os.path.dirname(куда), база + "_p1.png"),
                     os.path.join(os.path.dirname(куда), база + ".png")):
        if os.path.exists(кандидат):
            return Image.open(кандидат).convert("RGBA").resize(
                Image.open(куда).size if os.path.exists(куда) else Image.open(кандидат).size)
    return None


def сверить():
    """ПЕЧАТЬ против boards.3.0.0.yaml (данные не меняются — только таблица)."""
    import re
    текст = open(os.path.join(КОРЕНЬ, "data", "boards", "boards.3.0.0.yaml"),
                 encoding="utf-8").read()
    print("%-7s %-16s %-14s %-14s" % ("цвет", "жетон", "печать", "boards"))
    for цвет, печать in ПЕЧАТЬ.items():
        блок = текст.split("id: troop_%s" % цвет)[1].split("- id:")[0]
        for род, сердец in печать["units"].items():
            hp = int(re.search(r"%s: \{hp: (\d+)\}" % род, блок).group(1))
            знак = "" if hp == сердец else "  <-- РАСХОЖДЕНИЕ"
            print("%-7s %-16s ♥%-13d hp %-11d%s" % (цвет, род, сердец, hp, знак))
        for здание, (яч, сердец) in печать["buildings"].items():
            m = re.search(r"%s:\s+\{energy_slots: (\d+), hp: (\d+)\}" % здание, блок)
            e, hp = int(m.group(1)), int(m.group(2))
            знак = "" if (e, hp) == (яч, сердец) else "  <-- РАСХОЖДЕНИЕ"
            print("%-7s %-16s яч %d ♥%-8d яч %d hp %-6d%s" % (цвет, здание, яч, сердец,
                                                             e, hp, знак))


def главная():
    проверить = "--проверить" in sys.argv
    if "--сверить" in sys.argv:
        сверить()
        return 0
    плохих = 0
    for откуда, куда in раскладка():
        src = os.path.join(ИСТОЧНИК, откуда)
        if not os.path.exists(src):
            print("НЕТ ФАЙЛА:", откуда)
            плохих += 1
            continue
        новый = Image.open(src).convert("RGBA")
        прежний = Image.open(куда).convert("RGBA") if os.path.exists(куда) else None
        готовый, как = вписать(новый, прежний.size if прежний else None)
        сов = совпадение(силуэт(готовый), силуэт(прежний)) if прежний else float("nan")
        тон = тон_рамки(готовый)
        имя = os.path.basename(куда)[:-4]
        предупреждение = ""
        лицо = лицо_оборота(куда)
        if лицо is not None and not сов >= 0.93:
            # ОБОРОТ — ЗЕРКАЛО ЛИЦА. Экспорт 25.09 выгружает оборот так, как он
            # лежит на столе перевёрнутым, то есть отражённым. Прежние файлы
            # оборотов казарм, завода, ЦУ и большой нейтральной были нарисованы
            # другим силуэтом (совпадение с лицом 0,45-0,70), поэтому мерило
            # здесь — отражённое лицо того же жетона, а не прежний файл.
            сов = совпадение(силуэт(готовый), силуэт(ImageOps.mirror(лицо)))
            как += ", оборот = зеркало лица"
        if прежний is not None and сов < 0.93:
            предупреждение += "  <-- СИЛУЭТ"
            плохих += 1
        for цвет, t in ТОН.items():
            if имя.endswith("_p%d" % [p for _, _, p, cc in ЦВЕТА if cc == цвет][0]):
                if тон is None or min(abs(тон - t), 360 - abs(тон - t)) > 35:
                    предупреждение += "  <-- ЦВЕТ (тон %s, ждали %d)" % (
                        "нет" if тон is None else "%.0f" % тон, t)
                    плохих += 1
        print("%-26s <- %-36s %-18s силуэт %.3f%s" % (имя, откуда, как, сов, предупреждение))
        if not проверить:
            готовый.save(куда, optimize=True)
    print("готово%s, вопросов: %d" % (" (проверка, ничего не записано)" if проверить else "",
                                        плохих))
    return 1 if плохих else 0


if __name__ == "__main__":
    sys.exit(главная())
