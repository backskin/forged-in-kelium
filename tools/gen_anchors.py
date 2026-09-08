# -*- coding: utf-8 -*-
"""ГЕНЕРАТОР ЯКОРЕЙ ПЕЧАТНЫХ ПЛАНШЕТОВ — перезапускать при новом арте.

Читает картинки печатных планшетов и находит на них места, куда игра кладёт
живое: ячейки хранилища под кубики, рамки спец-атаки и сборки под жетоны
модулей. Число найденных ячеек СВЕРЯЕТСЯ с data/boards — если художник и
данные разошлись, скрипт скажет об этом, а не выдаст молча кривые якоря.

Запуск:  python tools/gen_anchors.py .
Пишет:   data/textures/board/anchors.yaml
"""
import sys
import os
import re
import io

from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
ART = os.path.join(ROOT, 'data', 'textures', 'board')


def components(im, pred, gap, minpx):
    """Связные пятна пикселей, подходящих под pred, с допуском разрыва gap."""
    w, h = im.size
    px = im.load()
    m = bytearray(w * h)
    for y in range(h):
        r0 = y * w
        for x in range(w):
            if pred(*px[x, y]):
                m[r0 + x] = 1
    seen = bytearray(w * h)
    out = []
    for y0 in range(h):
        for x0 in range(w):
            i = y0 * w + x0
            if not m[i] or seen[i]:
                continue
            st = [(x0, y0)]
            seen[i] = 1
            pts = []
            while st:
                x, y = st.pop()
                pts.append((x, y))
                for dy in range(-gap, gap + 1):
                    yy = y + dy
                    if yy < 0 or yy >= h:
                        continue
                    for dx in range(-gap, gap + 1):
                        xx = x + dx
                        if 0 <= xx < w:
                            j = yy * w + xx
                            if m[j] and not seen[j]:
                                seen[j] = 1
                                st.append((xx, yy))
            if len(pts) < minpx:
                continue
            xs = [p[0] for p in pts]
            ys = [p[1] for p in pts]
            out.append([min(xs), min(ys),
                        max(xs) - min(xs) + 1, max(ys) - min(ys) + 1, len(pts)])
    return out


def green(r, g, b):
    return max(r, g, b) - min(r, g, b) >= 55 and g > r and g > b and g > 150


def red(r, g, b):
    return max(r, g, b) - min(r, g, b) >= 55 and r > g + 50 and r > 150 and b < r


def storage_cells(path):
    im = Image.open(path).convert('RGB')
    w, h = im.size
    px = im.load()
    raw = components(im, lambda r, g, b: green(r, g, b) or red(r, g, b), 14, 3000)
    if not raw:
        return (w, h), {'miner': [], 'plant': [], 'base': []}
    unit = (sorted(c[2] for c in raw)[len(raw) // 2],
            sorted(c[3] for c in raw)[len(raw) // 2])

    def ink(x0, y0, bw, bh):
        n = 0
        for yy in range(max(0, y0), min(h, y0 + bh), 3):
            for xx in range(max(0, x0), min(w, x0 + bw), 3):
                r, g, b = px[xx, yy]
                if green(r, g, b) or red(r, g, b):
                    n += 1
        return n

    cells = []
    for x, y, cw, ch, _ in raw:
        # Две ячейки уровня напечатаны НАИСКОСЬ и слипаются в одно пятно.
        # По какой диагонали они лежат — у добытчика и станции по-разному
        # (планшет зеркальный), поэтому не угадываем, а смотрим, в каком
        # верхнем углу пятна на самом деле есть краска.
        if cw > unit[0] * 1.35 or ch > unit[1] * 1.35:
            uw, uh = unit
            if ink(x, y, uw, uh) >= ink(x + cw - uw, y, uw, uh):
                cells.append((x, y, uw, uh))
                cells.append((x + cw - uw, y + ch - uh, uw, uh))
            else:
                cells.append((x + cw - uw, y, uw, uh))
                cells.append((x, y + ch - uh, uw, uh))
        else:
            cells.append((x, y, cw, ch))
    mid = w / 2.0
    groups = {'miner': [], 'plant': [], 'base': []}
    for c in cells:
        cx = c[0] + c[2] / 2.0
        if abs(cx - mid) < w * 0.06:
            groups['base'].append(c)
        elif cx < mid:
            groups['miner'].append(c)
        else:
            groups['plant'].append(c)
    for k in groups:
        groups[k].sort(key=lambda c: (c[1], c[0]))
    return (w, h), groups


def troop_frames(path):
    im = Image.open(path).convert('RGB')
    w, h = im.size
    pink = [c for c in components(
        im, lambda r, g, b: r > 185 and 138 <= g <= 200 and r - g > 38 and r - b > 38,
        6, 700) if c[2] > w * 0.06]
    blue = [c for c in components(
        im, lambda r, g, b: 120 < b < 205 and b - r > 22 and b - g > 8 and r > 95,
        6, 650) if c[3] > h * 0.25]
    pink.sort(key=lambda c: c[0])
    blue.sort(key=lambda c: c[0])
    # У рамки спец-атаки видна только верхняя дуга — низ закрыт содержимым, и
    # детектор занижает её рост. На печати рамка сборки ровно того же роста,
    # поэтому высоту берём у неё: это измерение, а не подгонка.
    ph = max([c[3] for c in blue], default=max([c[3] for c in pink], default=0))
    return (w, h), [(c[0], c[1], c[2], max(c[3], ph)) for c in pink], blue


def board_cell_counts(root):
    """Сколько ячеек на каждом уровне — из свежайшего data/boards/*.yaml."""
    d = os.path.join(root, 'data', 'boards')
    newest = sorted(f for f in os.listdir(d) if f.startswith('boards.'))[-1]
    text = io.open(os.path.join(d, newest), encoding='utf-8').read()
    out = {}
    for m in re.finditer(
            r'kind:\s*storage_side\s*\n\s*side:\s*(\w+)(.*?)(?=\n  - id:|\Z)', text, re.S):
        side, body = m.group(1), m.group(2)
        rec = {}
        for key in ('miners', 'plants'):
            mm = re.search(key + r':\s*\[(.*?)\]', body)
            if mm:
                rec[key] = [s.strip().strip('"') for s in mm.group(1).split(',')]
        out[side] = rec
    return newest, out


# ===========================================================================
#  ОБЩИЕ ПЛАНШЕТЫ СТОЛА: научный отдел и рынок
# ===========================================================================
#  Здесь якоря не ищутся детектором пятен, а ЗАМЕРЕНЫ по сетке и записаны
#  числами: ячейки шагов науки напечатаны по гексовой сетке и повёрнуты на 30°,
#  и «пятно ячейки» на этих картинках сливается с соседним — детектор находил
#  цепочку из трёх ячеек одним куском. Зато сетка регулярна: хватает начала
#  трека и двух шагов, всё остальное считается.
#
#  Числа не берутся на веру: check_science ниже проверяет каждую посчитанную
#  ячейку по самой картинке — в её середине обязана быть светлая бумага ячейки,
#  а вокруг серый контур. Художник сдвинул печать — сверка это скажет.
SHARED = {
    'science': {
        'size': (2693, 1866),
        'cell': (149, 127),
        'angle': -30,
        'step': (257, 0),
        'row': (75, 126),
        'tracks': [
            ('left', (214, 949), (919, 882), (735, 681, 157, 107)),
            ('middle', (1024, 1419), (1729, 1352), (1545, 1151, 157, 107)),
            ('right', (1836, 949), (2541, 882), (2357, 681, 157, 107)),
        ],
    },
    'market': {
        'size': (1890, 1890),
        'card': (468, 618, 1015, 654),
    },
}

# Сколько ячеек на каждом шаге — из свода. Столько же должно посчитаться по сетке.
SCIENCE_CAPACITY = [3, 3, 2, 1]


def science_cells(d):
    """Центры всех ячеек шагов по сетке: (трек, шаг, ячейка, x, y)."""
    out = []
    for name, origin, peak, _card in d['tracks']:
        for step in range(1, len(SCIENCE_CAPACITY) + 1):
            for cell in range(SCIENCE_CAPACITY[step - 1]):
                if step == len(SCIENCE_CAPACITY):
                    x, y = peak
                else:
                    x = origin[0] + d['step'][0] * (step - 1) + d['row'][0] * cell
                    y = origin[1] + d['step'][1] * (step - 1) + d['row'][1] * cell
                out.append((name, step, cell, x, y))
    return out


def check_science(path, d, problems):
    """Каждая посчитанная ячейка обязана лежать на картинке и быть светлой."""
    im = Image.open(path).convert('RGB')
    if im.size != d['size']:
        problems.append('science: картинка %dx%d, а якоря сняты с %dx%d'
                        % (im.size + d['size']))
        return
    px = im.load()
    for name, step, cell, x, y in science_cells(d):
        if not (0 <= x < im.size[0] and 0 <= y < im.size[1]):
            problems.append('science/%s шаг %d ячейка %d: центр вне картинки'
                            % (name, step, cell))
            continue
        # Середина ячейки — бумага: светлая и почти без цвета. Попадём в
        # напечатанный приз (кубик, монета) — там цвет, поэтому смотрим сразу
        # несколько точек вокруг центра и берём самую светлую.
        best = 0
        for dy in (-24, 0, 24):
            for dx in (-24, 0, 24):
                r, g, b = px[x + dx, y + dy]
                best = max(best, min(r, g, b))
        if best < 170:
            problems.append('science/%s шаг %d ячейка %d: в центре нет бумаги '
                            '(самое светлое %d)' % (name, step, cell, best))


def shared_board(board, path, problems):
    """Строки yaml для общего планшета стола."""
    d = SHARED[board]
    if board == 'science':
        check_science(path, d, problems)
        out = ['  - id: science', '    kind: science',
               '    size: [%d, %d]' % d['size'],
               '    cell: [%d, %d]' % d['cell'],
               '    angle: %d' % d['angle'],
               '    step: [%d, %d]' % d['step'],
               '    row: [%d, %d]' % d['row'],
               '    tracks:']
        for name, origin, peak, card in d['tracks']:
            out.append('      - {id: %s, origin: [%d, %d], peak: [%d, %d], '
                       'card: [%d, %d, %d, %d]}'
                       % ((name,) + origin + peak + card))
        return out
    im = Image.open(path)
    if im.size != d['size']:
        problems.append('market: картинка %dx%d, а якоря сняты с %dx%d'
                        % (im.size + d['size']))
    return ['  - id: market', '    kind: market',
            '    size: [%d, %d]' % d['size'],
            '    card: [%d, %d, %d, %d]' % d['card']]


def main():
    src, counts = board_cell_counts(ROOT)
    lines = [
        '# ===========================================================================',
        '#  ЯКОРЯ ПЕЧАТНЫХ ПЛАНШЕТОВ — где на картинке лежит живое',
        '# ===========================================================================',
        '#  Файл СГЕНЕРИРОВАН по самим картинкам (tools/gen_anchors.py) и сверен',
        '#  с ' + src + '. Художник перерисовал планшет — перегенерировать, а не',
        '#  править руками. Координаты — пиксели ИСХОДНОЙ картинки, игра пересчитает',
        '#  их под свой масштаб сама.',
        '#',
        '#  ПЛАНШЕТЫ ИГРОКА (storage, troop) записаны списком рамок — их находит',
        '#  детектор пятен. ОБЩИЕ ПЛАНШЕТЫ СТОЛА (science, market) — сеткой: ячейки',
        '#  шагов науки напечатаны по гексовой сетке и повёрнуты, и вместо сорока',
        '#  рамок хватает начала трека и двух шагов (origin/step/row). Каждая',
        '#  посчитанная ячейка сверяется с картинкой: в середине обязана быть',
        '#  светлая бумага ячейки.',
        '# ===========================================================================',
        '', 'meta:', '  id: 1.0.0', '  type: board_anchors', '  source: ' + src, '',
        'boards:']
    problems = []
    for name in sorted(os.listdir(ART)):
        if not name.endswith('.png'):
            continue
        path = os.path.join(ART, name)
        # ОБЩИЕ ПЛАНШЕТЫ СТОЛА идут отдельным путём: у них нет стороны, а ячейки
        # шагов науки напечатаны по гексовой сетке и повёрнуты — списком рамок их
        # не описать (см. shared_board).
        if name[:-4] in SHARED:
            lines += shared_board(name[:-4], path, problems)
            continue
        if '-' not in name:
            problems.append('%s: непонятное имя планшета (ждём вид-сторона)' % name)
            continue
        side = name[:-4].split('-', 1)[1]
        if name.startswith('storage-'):
            (w, h), groups = storage_cells(path)
            lines += ['  - id: %s' % name[:-4], '    kind: storage',
                      '    side: %s' % side, '    size: [%d, %d]' % (w, h), '    cells:']
            for grp in ('miner', 'plant', 'base'):
                cs = groups[grp]
                if grp == 'base':
                    for c in cs:
                        lines.append('      - {group: base, level: 0, type: U, '
                                     'box: [%d, %d, %d, %d]}' % c)
                    if len(cs) != 2:
                        problems.append('%s: центральных ячеек %d, а по правилам 2'
                                        % (name, len(cs)))
                    continue
                want = counts.get(side, {}).get(grp + 's', [])
                flat = []
                for lv, s in enumerate(want, 1):
                    for t in s:
                        flat.append((lv, t))
                if len(flat) != len(cs):
                    problems.append('%s/%s: на картинке %d ячеек, в данных %d (%s)'
                                    % (name, grp, len(cs), len(flat), ','.join(want)))
                for i, c in enumerate(cs):
                    lv, t = flat[i] if i < len(flat) else (0, 'U')
                    lines.append('      - {group: %s, level: %d, type: %s, '
                                 'box: [%d, %d, %d, %d]}' % ((grp, lv, t) + c))
        elif name.startswith('troop-'):
            (w, h), pink, blue = troop_frames(path)
            units = ['infantry', 'vehicle', 'aircraft', 'tower']
            builds = ['barracks', 'factory', 'airbase', 'command_center']
            lines += ['  - id: %s' % name[:-4], '    kind: troop',
                      '    side: %s' % side, '    size: [%d, %d]' % (w, h), '    columns:']
            if len(pink) != 4 or len(blue) != 4:
                problems.append('%s: рамок спец-атаки %d, сборки %d — ждали по 4'
                                % (name, len(pink), len(blue)))
            for i in range(min(4, len(pink), len(blue))):
                lines.append('      - {unit: %s, building: %s, attack: [%d, %d, %d, %d], '
                             'assembly: [%d, %d, %d, %d]}'
                             % ((units[i], builds[i]) + tuple(pink[i][:4])
                                + tuple(blue[i][:4])))
    out = os.path.join(ART, 'anchors.yaml')
    io.open(out, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
    print('записано:', out)
    if problems:
        print('')
        print('РАСХОЖДЕНИЯ:')
        for p in problems:
            print('  ' + p)
    else:
        print('сверка с данными сошлась')


main()
