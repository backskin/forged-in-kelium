# -*- coding: utf-8 -*-
"""ПЕРЕБОР СВЯЗОК: одиночки, пары, тройки карт в 14 положениях.

Запуск: python перебор.py [пары|тройки]
Пишет tools/комбо/итог_*.json и печатает лучшие связки.
"""
import itertools
import json
import os
import sys
import time

import модель_хода as M
import карты_пул  # noqa: F401  (регистрирует карты)

ЗДЕСЬ = os.path.dirname(os.path.abspath(__file__))


def форсаж(s):
    k = s['charge']
    n = dict(s, charge=0)
    новых = 0
    for что in ('mil', 'min'):
        if что == 'mil':
            d = min(k, n['mil'] - n['milp']); n['milp'] += d
        else:
            d = min(k, n['miners'] - n['minp']); n['minp'] += d
        k -= d
        новых += d
    n['cubes'] += k
    for _ in range(новых):
        n = M.событие(n, 'запитано')
    return n


M.КАРТЫ['A_форсаж']['сразу'] = форсаж

# события, отложенные «сразу»-эффектом, разыгрываются после установки
_старые_ходы = M.ходы


def ходы(s):
    out = []
    for n in _старые_ходы(s):
        for e in n.get('_события', ()):
            n = M.событие(dict(n, _события=()), e)
        out.append(dict(n, _события=()))
    return out


M.ходы = ходы

БАЗА = {}


def с_рукой(pos, карты, уст=()):
    s = dict(M.ПОЛОЖЕНИЯ[pos])
    s['inst'] = s['inst'] | frozenset(уст)
    ho = frozenset(c for c in карты if c.startswith('O_'))
    ha = frozenset(c for c in карты if c.startswith('A_'))
    s['ho'], s['ha'] = ho, ha
    s['hobj'] += len(ho)
    s['hars'] += len(ha)
    return s


def база(pos, nO, nA):
    k = (pos, nO, nA)
    if k not in БАЗА:
        s = dict(M.ПОЛОЖЕНИЯ[pos])
        s['hobj'] += nO
        s['hars'] += nA
        БАЗА[k] = M.лучший(s)
    return БАЗА[k]


def выигрыш(карты, уст=()):
    nO = sum(c.startswith('O_') for c in карты)
    nA = len(карты) - nO
    out = {}
    for pos in M.ПОЛОЖЕНИЯ:
        v = M.лучший(с_рукой(pos, карты, уст)) - база(pos, nO, nA)
        if уст:
            v -= M.ЦЕНА['ninst'] * len(уст)
        out[pos] = round(v, 2)
    return out


def сводка(g):
    бед = [v for k, v in g.items() if k.endswith('−')]
    бог = [v for k, v in g.items() if k.endswith('+')]
    return dict(среднее=round(sum(g.values()) / len(g), 2),
                бедным=round(sum(бед) / len(бед), 2), богатым=round(sum(бог) / len(бог), 2),
                пик=max(g.values()), где_пик=max(g, key=g.get))


def main():
    режим = sys.argv[1] if len(sys.argv) > 1 else 'пары'
    ids = sorted(M.КАРТЫ)
    t0 = time.time()
    один = {c: выигрыш((c,)) for c in ids}
    print('одиночки: %d карт, %.1f с' % (len(ids), time.time() - t0), flush=True)
    итог = {'одиночки': {c: dict(сводка(g), поПоложениям=g) for c, g in один.items()}}
    # открыто: карта арсенала уже установлена (видна всем) — сколько она даёт за ход
    итог['открыто'] = {c: сводка(выигрыш((), (c,))) for c in ids if c.startswith('A_')}
    # установлена X, в руке Y: связка, где одна часть на виду, другая — скрыта
    if режим in ('пары', 'тройки'):
        смесь = {}
        for x in ids:
            if not x.startswith('A_'):
                continue
            for y in ids:
                if y == x:
                    continue
                g = выигрыш((y,), (x,))
                отд = {p: итог['открыто'][x]['среднее'] for p in g}
                смесь[x + '>' + y] = dict(сводка(g),
                    синергия=round(сводка(g)['среднее'] - итог['открыто'][x]['среднее'] - сводка(один[y])['среднее'], 2))
        итог['смесь'] = смесь
        print('смесь: %d, %.0f с' % (len(смесь), time.time() - t0), flush=True)
    if режим in ('пары', 'тройки'):
        пары = {}
        всего = len(ids) * (len(ids) - 1) // 2
        for i, (a, b) in enumerate(itertools.combinations(ids, 2)):
            g = выигрыш((a, b))
            # синергия = сверх суммы одиночек
            син = {p: round(g[p] - один[a][p] - один[b][p], 2) for p in g}
            пары[a + '+' + b] = dict(сводка(g), синергия=сводка(син), поПоложениям=g)
            if (i + 1) % 200 == 0:
                print('  пары %d/%d, %.0f с' % (i + 1, всего, time.time() - t0), flush=True)
        итог['пары'] = пары
    if режим == 'тройки':
        лучшие = sorted(итог['пары'], key=lambda k: -итог['пары'][k]['синергия']['среднее'])[:40]
        кандидаты = sorted({c for k in лучшие for c in k.split('+')})
        тройки = {}
        for a, b, c in itertools.combinations(кандидаты, 3):
            g = выигрыш((a, b, c))
            суммапар = {p: max(итог['пары'].get('+'.join(sorted(x)), {'поПоложениям': {p: 0}})
                               ['поПоложениям'][p] for x in ((a, b), (a, c), (b, c))) for p in g}
            син = {p: round(g[p] - суммапар[p] - один[sorted((a, b, c), key=lambda x: один[x][p])[0]][p], 2)
                   for p in g}
            тройки['+'.join((a, b, c))] = dict(сводка(g), синергия=сводка(син), поПоложениям=g)
        итог['тройки'] = тройки
        print('тройки: %d' % len(тройки), flush=True)
    with open(os.path.join(ЗДЕСЬ, 'итог_%s.json' % режим), 'w', encoding='utf-8') as f:
        json.dump(итог, f, ensure_ascii=False, indent=0)
    print('время %.0f с' % (time.time() - t0))


if __name__ == '__main__':
    main()
