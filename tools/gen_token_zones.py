# -*- coding: utf-8 -*-
"""РАЗМЕТКА ЗОН НА ЖЕТОНАХ ЗДАНИЙ — перезапускать при новом арте жетонов.

Художник нарисовал на жетонах ВСЁ, что раньше приложение рисовало само: сердца
прочности, ячейки под кубики энергии, место, куда энергия приходит. Приложению
осталось знать, ГДЕ это на картинке, — и ставить туда свои кубики. Знание живёт
в маске рядом с картинкой (`<жетон>.zones.png`, читает kelium.report.Zones):

  * красный  #FF0000 — ЯЧЕЙКА ПОД КУБИК ЭНЕРГИИ, по квадрату на ячейку;
  * синий    #0080FF — МЕСТО, ГДЕ ЭНЕРГИЯ ПОЯВЛЯЕТСЯ (энергостанция и ЦУ);
  * розовый  #FF00FF — СЕРДЦА ПРОЧНОСТИ: сюда кладутся отметки урона.

Зелёного (место под подпись) больше нет: на жетоне с рисунком подписи не нужны
(решение дизайнера 08.09.2026 — «на жетонах где уже есть текстура НЕ НУЖНЫ
никакие буквы и обозначения, туда разве что кубики будут ставиться»).

СВЕРКА, А НЕ ДОВЕРИЕ. Найденное сверяется с data/boards: ячеек ровно столько,
сколько `energy_slots`, сердец — сколько `hp`, кубиков в месте появления —
сколько `energy_gives`. Не сошлось — скрипт скажет, на каком жетоне, и ничего не
запишет: маска, разошедшаяся с печатью, хуже отсутствующей.

Запуск:  python tools/gen_token_zones.py .
Пишет:   data/textures/token/<жетон>_p1..p4.zones.png
"""
import glob
import io
import os
import sys

import numpy as np
import yaml
from PIL import Image
from scipy import ndimage as nd

ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
TOK = os.path.join(ROOT, 'data', 'textures', 'token')

КРАСНЫЙ = (255, 0, 0)
СИНИЙ = (0, 128, 255)
РОЗОВЫЙ = (255, 0, 255)


def пятна(маска, минимум, окно=None, заполнение=None):
    """Связные пятна маски: (x, y, w, h, пикселей, заполненность)."""
    m = nd.binary_closing(маска, np.ones((9, 9)))
    lab, n = nd.label(m)
    out = []
    for i in range(1, n + 1):
        ys, xs = np.nonzero(lab == i)
        if len(xs) < минимум:
            continue
        x0, y0 = int(xs.min()), int(ys.min())
        w, h = int(xs.max() - x0 + 1), int(ys.max() - y0 + 1)
        зап = len(xs) / (w * h)
        if окно and not (окно[0] <= w <= окно[1] and окно[0] <= h <= окно[1]):
            continue
        if заполнение and not (заполнение[0] <= зап <= заполнение[1]):
            continue
        out.append((x0, y0, w, h, int(len(xs)), зап))
    out.sort(key=lambda t: (t[1], t[0]))
    return out


def слои(путь):
    im = Image.open(путь).convert('RGBA')
    a = np.asarray(im).astype(int)
    return im, a[:, :, 0], a[:, :, 1], a[:, :, 2], a[:, :, 3]


def сердца(r, g, b, al):
    """Сердца прочности — единственное ярко-красное на жетоне."""
    return пятна((al > 128) & (r > 170) & (g < 120) & (b < 120)
                 & (r - g > 70) & (r - b > 70), 600)


def ячейки_военных(r, g, b, al):
    """Ячейка энергии у казармы, завода, авиабазы и ЦУ — ЖЁЛТАЯ РАМКА."""
    mx = np.maximum(np.maximum(r, g), b)
    mn = np.minimum(np.minimum(r, g), b)
    m = ((al > 128) & (r > 165) & (g > 155) & (b < 145)
         & (r - b > 60) & (g - b > 45) & ((mx - mn) > 85))
    return пятна(m, 500, окно=(70, 145), заполнение=(0.05, 0.30))


def ячейки_добытчика(r, g, b, al):
    """Ячейка энергии у добытчика — ТЁМНО-ЗЕЛЁНЫЙ КВАДРАТ с молнией."""
    m = ((al > 128) & (r < 110) & (g > 60) & (g < 150) & (b < 110)
         & (g - r > 25) & (g - b > 25))
    return пятна(m, 1500, окно=(60, 100), заполнение=(0.55, 0.95))


def место_энергии(r, g, b, al):
    """Куда энергия ПРИХОДИТ: печатные жёлтые кубики (станция, крыло ЦУ)."""
    return пятна((al > 128) & (r > 225) & (g > 195) & (b < 110), 1200)


def набор_жетонов(root):
    """Прочность, ячейки и выдача энергии по зданиям — из свежайшего data/boards."""
    p = sorted(glob.glob(os.path.join(root, 'data', 'boards', 'boards.*.yaml')))[-1]
    d = yaml.safe_load(io.open(p, encoding='utf-8'))
    b = d['boards'][0]
    out = {}
    for имя, s in b['buildings'].items():
        out[имя] = dict(hp=s.get('hp', 0), slots=s.get('energy_slots', 0),
                        gives=s.get('energy_gives', 0))
    for s in b['miners']:
        out['miner_l%d' % s['level']] = dict(hp=s.get('hp', 0),
                                             slots=s.get('energy_slots', 0), gives=0)
    for s in b['power_plants']:
        out['power_plant_l%d' % s['level']] = dict(hp=s.get('hp', 0), slots=0,
                                                   gives=s.get('energy_gives', 0))
    out['tower'] = dict(hp=b['units']['tower']['hp'], slots=0, gives=0)
    return os.path.basename(p), out


def маска(имя, ожидаем, беды):
    """Собрать маску зон по картинке жетона. None — не сошлось."""
    путь = os.path.join(TOK, имя + '_p1.png')
    if not os.path.isfile(путь):
        беды.append('нет картинки %s' % os.path.basename(путь))
        return None
    im, r, g, b, al = слои(путь)
    out = Image.new('RGBA', im.size, (0, 0, 0, 0))
    d = __import__('PIL.ImageDraw', fromlist=['ImageDraw']).Draw(out)

    # ---- сердца ----
    hp = сердца(r, g, b, al)
    if ожидаем['hp'] > 0:
        if len(hp) != 1:
            беды.append('%s: сердец найдено пятнами %d, ждали одно' % (имя, len(hp)))
        else:
            x, y, w, h = hp[0][:4]
            d.rectangle([x, y, x + w - 1, y + h - 1], fill=РОЗОВЫЙ)

    # ---- ячейки энергии ----
    if ожидаем['slots'] > 0:
        найдено = (ячейки_добытчика(r, g, b, al) if имя.startswith('miner')
                   else ячейки_военных(r, g, b, al))
        if len(найдено) != ожидаем['slots']:
            беды.append('%s: ячеек энергии найдено %d, а в данных %d'
                        % (имя, len(найдено), ожидаем['slots']))
        else:
            for x, y, w, h, _, _ in найдено:
                # Квадрат ЧУТЬ МЕНЬШЕ найденной рамки: кубик должен лечь внутрь
                # ячейки, а не накрыть её вместе с рамкой.
                п = int(round(min(w, h) * 0.14))
                d.rectangle([x + п, y + п, x + w - 1 - п, y + h - 1 - п], fill=КРАСНЫЙ)

    # ---- место, где энергия появляется ----
    if ожидаем['gives'] > 0:
        найдено = место_энергии(r, g, b, al)
        if len(найдено) != 1:
            беды.append('%s: место появления энергии найдено пятнами %d, ждали одно'
                        % (имя, len(найдено)))
        else:
            x, y, w, h = найдено[0][:4]
            d.rectangle([x, y, x + w - 1, y + h - 1], fill=СИНИЙ)
    return out


def main():
    источник, ожидания = набор_жетонов(ROOT)
    беды = []
    готово = {}
    for имя, ож in ожидания.items():
        m = маска(имя, ож, беды)
        if m is not None:
            готово[имя] = m
    if беды:
        print('НИЧЕГО НЕ ЗАПИСАНО — сперва разберитесь:')
        for x in беды:
            print('  ' + x)
        return 1
    писано = 0
    for имя, m in готово.items():
        for p in range(1, 5):
            цель = os.path.join(TOK, '%s_p%d.zones.png' % (имя, p))
            if not os.path.isfile(os.path.join(TOK, '%s_p%d.png' % (имя, p))):
                continue
            m.save(цель)
            писано += 1
    print('масок записано: %d (по %s)' % (писано, источник))
    print('сверка с данными сошлась: сердца, ячейки энергии, место выдачи')
    return 0


sys.exit(main())
