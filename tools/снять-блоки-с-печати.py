# -*- coding: utf-8 -*-
"""СНЯТЬ НАБОР БЛОКОВ С ПЕЧАТИ ДИЗАЙНЕРА (26.09.2026).

Раньше набор блоков (где на модулях поля энергия и контейнеры) собирал скрипт
tools/наборы-блоков-5.py, а дизайнер рисовал по нему. Теперь наоборот:
дизайнер нарисовал модули сам («Модули-поля-тесно-1.png» … «-20.png»: сначала
большие Б1–Б5, потом малые М1–М5, каждый стороной «а» и «б»), и набор
снимается с картинок.

Геометрия картонок (какие гексы у какого модуля) берётся из прежнего набора —
это физический картон, он не меняется. С картинки снимается только печать:
жёлтые кольца энергии и ящики контейнеров, на каком гексе и на какой стороне
гекса они стоят (ящик в центре гекса — ячейка авиации, сектор 6).

Запуск:  python tools/снять-блоки-с-печати.py . "<папка экспорта>" <новая версия> <префикс файлов>
Пишет:   data/blocks/blocks.<версия>.yaml
Текстуры потом раскладывает tools/gen_block_art.py с той же версией и префиксом.
"""
import io
import math
import os
import sys

import numpy as np
import yaml
from PIL import Image
from scipy import ndimage as nd

ROOT = sys.argv[1]
SRC = sys.argv[2]
НОВАЯ = sys.argv[3]
ПРЕФИКС = sys.argv[4]
ГЕОМЕТРИЯ = '5.0.0'   # картон тот же, что у эталонного набора
S3 = math.sqrt(3)
НАКЛОН = 30.0


def центр(q, r, size, origin):
    return (origin[0] + 1.5 * q * size, origin[1] + S3 * (r + q / 2.0) * size)


def повернуть(cells, n):
    out = list(cells)
    for _ in range(n):
        out = [(-r, q + r) for q, r in out]
    return out


def рамка_в_единицах(cells):
    xs = [центр(q, r, 1, (0, 0))[0] for q, r in cells]
    ys = [центр(q, r, 1, (0, 0))[1] for q, r in cells]
    return min(xs) - 1, min(ys) - S3 / 2, max(xs) + 1, max(ys) + S3 / 2


def подогнать(alpha, cells):
    ys, xs = np.nonzero(alpha > 128)
    x0, y0, x1, y1 = xs.min(), ys.min(), xs.max() + 1, ys.max() + 1
    W, H = x1 - x0, y1 - y0
    лучшее = None
    for n in range(6):
        cs = повернуть(cells, n)
        ux0, uy0, ux1, uy1 = рамка_в_единицах(cs)
        sx, sy = W / (ux1 - ux0), H / (uy1 - uy0)
        разброс = abs(sx - sy) / max(sx, sy)
        size = (sx + sy) / 2
        origin = (x0 - ux0 * size, y0 - uy0 * size)
        внутри = 0
        for q, r in cs:
            cx, cy = центр(q, r, size, origin)
            if 0 <= int(cy) < alpha.shape[0] and 0 <= int(cx) < alpha.shape[1] \
                    and alpha[int(cy), int(cx)] > 200:
                внутри += 1
        свои = set(cs)
        кольцо = {(q + dq, r + dr) for q, r in cs
                  for dq, dr in ((1, 0), (1, -1), (0, -1), (-1, 0), (-1, 1), (0, 1))
                  if (q + dq, r + dr) not in свои}
        снаружи = 0
        for q, r in кольцо:
            cx, cy = центр(q, r, size, origin)
            if not (0 <= int(cy) < alpha.shape[0] and 0 <= int(cx) < alpha.shape[1]) \
                    or alpha[int(cy), int(cx)] < 40:
                снаружи += 1
        оценка = (внутри == len(cs), снаружи == len(кольцо), -разброс)
        if лучшее is None or оценка > лучшее[0]:
            лучшее = (оценка, n, size, origin)
    return лучшее


def пятна(маска, минимум):
    lab, n = nd.label(маска)
    if n == 0:
        return []
    веса = nd.sum(np.ones_like(lab), lab, range(1, n + 1))
    центры = nd.center_of_mass(маска, lab, range(1, n + 1))
    return [(c[1], c[0]) for c, w in zip(центры, веса) if w >= минимум]


def сторона_по_углу(dx, dy):
    ang = math.degrees(math.atan2(dy, dx))
    лучшая, разница = -1, 999.0
    for s in range(6):
        d = abs((ang - (-60.0 * s + НАКЛОН) + 180) % 360 - 180)
        if d < разница:
            разница, лучшая = d, s
    return лучшая


def в_данные(s, n):
    """Сторона на картинке → сторона в координатах набора (обратно повороту)."""
    if s < 0 or s == 6:
        return s
    return (s + n) % 6


def main():
    d = yaml.safe_load(io.open(os.path.join(ROOT, 'data', 'blocks',
                                            'blocks.%s.yaml' % ГЕОМЕТРИЯ), encoding='utf-8'))
    по_id = {b['id']: b for b in d['blocks']}
    порядок = [('Б%d' % i, f) for i in range(1, 6) for f in ('A', 'B')] \
        + [('М%d' % i, f) for i in range(1, 6) for f in ('A', 'B')]
    беды = []
    новые = {}
    сводка = []
    for номер, (bid, face) in enumerate(порядок, start=1):
        путь = os.path.join(SRC, '%s-%d.png' % (ПРЕФИКС, номер))
        cells = [(c['q'], c['r']) for c in по_id[bid]['faces'][face]]
        im = Image.open(путь).convert('RGBA')
        alpha = np.asarray(im)[:, :, 3]
        оценка, n_rot, size, origin = подогнать(alpha, cells)
        if not (оценка[0] and оценка[1]):
            беды.append('%s: сетка не села' % os.path.basename(путь))
            continue
        a = np.asarray(im.convert('RGB')).astype(int)
        r, g, b = a[:, :, 0], a[:, :, 1], a[:, :, 2]
        жёлтый = (r > 190) & (g > 165) & (b < 150) & (r - b > 60) & (abs(r - g) < 45)
        коричневый = (r > 130) & (r < 228) & (r - g > 33) & (r - b > 72) & (g - b > 20)
        # грани ящика разделены тёмным контуром — без слияния один ящик
        # распадается на два-три пятна
        коричневый = nd.binary_dilation(коричневый, iterations=12)
        точки = [центр(q, rr, size, origin) for q, rr in повернуть(cells, n_rot)]

        def ближайший(px, py):
            return min(range(len(точки)),
                       key=lambda i: (точки[i][0] - px) ** 2 + (точки[i][1] - py) ** 2)

        энергия = {i: -1 for i in range(len(cells))}
        ящик = {i: -1 for i in range(len(cells))}
        for px, py in пятна(жёлтый, 2500):
            i = ближайший(px, py)
            if энергия[i] >= 0:
                беды.append('%s-%s гекс %d: два кольца энергии' % (bid, face, i))
            энергия[i] = в_данные(сторона_по_углу(px - точки[i][0], py - точки[i][1]), n_rot)
        for px, py in пятна(коричневый, 2500):
            i = ближайший(px, py)
            if ящик[i] >= 0:
                беды.append('%s-%s гекс %d: два ящика' % (bid, face, i))
            далеко = math.hypot(px - точки[i][0], py - точки[i][1]) / size
            ящик[i] = 6 if далеко < 0.34 else в_данные(
                сторона_по_углу(px - точки[i][0], py - точки[i][1]), n_rot)
        for i in range(len(cells)):
            if энергия[i] >= 0 and энергия[i] == ящик[i]:
                беды.append('%s-%s гекс %d: энергия и ящик на одной стороне %d'
                            % (bid, face, i, энергия[i]))
        новые[(bid, face)] = [dict(q=q, r=rr, cell=ящик[i], energy=энергия[i])
                              for i, (q, rr) in enumerate(cells)]
        сводка.append('%s-%s (%s): энергия %d из %d, контейнеры %d, из них в небе %d'
                      % (bid, face, os.path.basename(путь),
                         sum(1 for e in энергия.values() if e >= 0), len(cells),
                         sum(1 for c in ящик.values() if c >= 0),
                         sum(1 for c in ящик.values() if c == 6)))
    for с in сводка:
        print(с)
    if беды:
        print('НИЧЕГО НЕ ЗАПИСАНО:')
        for б in беды:
            print('  ' + б)
        return 1
    out = ['# CONTENT: blocks  version %s' % НОВАЯ,
           '# СНЯТ С ПЕЧАТИ ДИЗАЙНЕРА: экспорт «%s-1…20.png» (26.09.2026).' % ПРЕФИКС,
           '# Снял tools/снять-блоки-с-печати.py: кольца энергии и ящики контейнеров',
           '# найдены на картинках, их гекс и сторона записаны сюда. Геометрия картонок —',
           '# из набора %s (физический картон не менялся).' % ГЕОМЕТРИЯ,
           '# Вручную не править: перерисовали модули — снять заново.',
           '']
    doc = {'meta': {'id': НОВАЯ, 'type': 'blocks',
                    'описание': 'снят с печати дизайнера «%s»' % ПРЕФИКС,
                    'сгенерировано': 'tools/снять-блоки-с-печати.py'},
           'blocks': []}
    for b in d['blocks']:
        doc['blocks'].append({'id': b['id'], 'kind': b['kind'],
                              'faces': {f: новые[(b['id'], f)] for f in ('A', 'B')}})
    путь = os.path.join(ROOT, 'data', 'blocks', 'blocks.%s.yaml' % НОВАЯ)
    io.open(путь, 'w', encoding='utf-8').write(
        '\n'.join(out) + yaml.safe_dump(doc, allow_unicode=True, sort_keys=False))
    print('записано: %s' % путь)
    return 0


sys.exit(main())
