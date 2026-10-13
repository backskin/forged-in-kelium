# -*- coding: utf-8 -*-
"""МОДЕЛЬ ОДНОГО ХОДА — стенд связок карт (13.10.2026).

Зачем. Новых механик (заряды энергии, телепортация, разряд станций,
установка «сразу») в движке ещё нет, а проверить сотни связок карт нужно
до того, как писать их в движок. Модель упрощает ход до сути: действия
приказа (у каждого два варианта выполнения), одно спец-действие, ресурсы
и жетоны — числами. Карты — правила, которые меняют ход.

Что считаем. Перебор ВСЕХ порядков розыгрыша хода (поиск в глубину с
памятью) — сколько «стоимости» игрок получает за ход. Разворот карты =
лучший ход с картами в руке минус лучший ход без них. Считаем в 14
положениях: 7 стратегий × богатое и бедное. Карта разворота сильнее
помогает бедному, чем богатому, — иначе это снежный ком лидеру.

Стоимость — грубая шкала «сколько это приближает к победе»; её цель —
сравнивать карты между собой, а не мерить точные очки.
"""
import itertools
import json
import sys
from functools import lru_cache

# ---------------------------------------------------------------- стоимость
ЦЕНА = dict(troph=2.0, steps=3.0, kel=1.0, coins=0.35, ammo=0.4, dmg=1.2,
            units=0.6, adj=0.4, miners=1.5, minp=1.0, milp=1.0, mil=1.2,
            objdone=2.0, ninst=1.5, vp=1.0, proj=0.4, charge=0.3, cont=0.6,
            hobj=0.5, hars=0.8, cubes=0.0)

ПОЛЯ = ['coins', 'kel', 'ammo', 'troph', 'units', 'adj', 'mil', 'milp', 'miners',
        'minp', 'cubes', 'charge', 'proj', 'steps', 'dmg', 'objdone', 'vp', 'spec',
        'cont', 'journal', 'enemy_near', 'hobj', 'hars', 'objturn', 'inst_turn', 'spec_got']


def стоимость(s):
    v = sum(ЦЕНА.get(k, 0) * s[k] for k in ЦЕНА if k in s)
    v += ЦЕНА['ninst'] * len(s['inst'])
    return v


# ---------------------------------------------------------------- карты
КАРТЫ = {}


def карта(id, тип, имя, группа, стратегии, **kw):
    КАРТЫ[id] = dict(id=id, тип=тип, имя=имя, группа=группа, стратегии=стратегии, **kw)


def ст(s, **d):
    n = dict(s)
    for k, v in d.items():
        n[k] = n.get(k, 0) + v
    return n


def has(s, cid):
    return cid in s['inst']


# ---------------------------------------------------------------- действия
def выход_добычи(s):
    k = s['minp'] * 2 + (s['miners'] - s['minp'])
    if has(s, 'A_перегрузка') and s['charge'] >= 1 and s['minp'] >= 1:
        k += 2
    return k


def событие(s, ev, глубина=0):
    """Срабатывания установленных карт на событие (каждая — раз за ход)."""
    if глубина > 6:
        return s
    for cid in sorted(s['inst']):
        c = КАРТЫ[cid]
        h = c.get('на', {}).get(ev)
        if h and (cid + ':' + ev) not in s['used']:
            s = dict(s, used=s['used'] | {cid + ':' + ev})
            s = h(s)
            for ev2 in s.get('_события', ()):
                s = событие(dict(s, _события=()), ev2, глубина + 1)
            s = dict(s, _события=())
    return s


def варианты(s, act):
    """Варианты выполнения действия → список новых состояний."""
    out = []
    if act == 'extract':
        out.append(событие(ст(s, kel=выход_добычи(s)), 'добыча'))
        if s['coins'] >= 2:
            out.append(ст(s, coins=-2 + (1 if has(s, 'A_монтаж') and s['charge'] else 0), miners=1))
    elif act == 'power':
        # переложить энергию: свободные кубики — в незапитанные здания
        free = s['cubes']
        if free > 0:
            # сначала военные, потом добытчики — и наоборот: два варианта
            for порядок in (('mil', 'min'), ('min', 'mil')):
                n = dict(s)
                f = free
                новых = 0
                for что in порядок:
                    if что == 'mil':
                        k = min(f, n['mil'] - n['milp'])
                        n['milp'] += k
                    else:
                        k = min(f, n['miners'] - n['minp'])
                        n['minp'] += k
                    f -= k
                    новых += k
                n['cubes'] = f
                for _ in range(новых):
                    n = событие(n, 'запитано')
                out.append(событие(n, 'питание'))
        # перезарядка: кубики со зданий обратно на станцию (заряд)
        if s['minp'] + s['milp'] > 0:
            k = s['minp'] + s['milp']
            out.append(событие(dict(s, minp=0, milp=0, charge=s['charge'] + k), 'питание'))
        if s['coins'] >= 3:
            out.append(ст(s, coins=-3, cubes=2))
    elif act == 'supply':
        hire = s['milp']
        ammo = 2 * s['milp'] + (s['mil'] - s['milp'])
        out.append(событие(ст(s, units=hire, ammo=ammo), 'снабжение'))
        if s['coins'] >= 3:
            out.append(ст(s, coins=-3, mil=1))
    elif act == 'command':
        подвод = min(s['units'] - s['adj'], 2)
        if has(s, 'A_маяки') and s['charge'] >= 1:
            подвод = s['units'] - s['adj']
        if has(s, 'A_мост'):
            подвод = min(s['units'] - s['adj'], подвод + 1)
        if подвод > 0:
            out.append(событие(ст(s, adj=подвод), 'манёвр'))
        if s['adj'] > 0 and s['enemy_near']:
            патроны = s['ammo'] + (s['charge'] if has(s, 'A_ранцы') else 0)
            n_att = min(s['adj'], патроны)
            if n_att > 0:
                из_заряда = max(0, n_att - s['ammo'])
                n = ст(s, ammo=-(n_att - из_заряда), charge=-из_заряда, dmg=n_att,
                       troph=n_att // 2)
                out.append(событие(dict(n, attacked=True), 'бой'))
    elif act == 'develop':
        if s['kel'] >= 1:
            k = min(s['kel'], 2)
            out.append(событие(ст(s, kel=-k, coins=3 * k), 'рынок'))
        if s['troph'] >= 1:
            out.append(событие(ст(s, troph=-1, steps=1), 'наука'))
            out.append(событие(ст(s, troph=-1, hars=1), 'наука'))
    return out


# ---------------------------------------------------------------- поиск
def ключ(s):
    return tuple(s[k] for k in ПОЛЯ) + (s['acts'], s['inst'], s['ho'], s['ha'],
                                        s['used'], s.get('attacked', False))


def ходы(s):
    out = []
    for i, a in enumerate(s['acts']):
        if a in s['acts'][:i]:
            continue
        rest = s['acts'][:i] + s['acts'][i + 1:]
        for n in варианты(dict(s, acts=rest), a):
            out.append(n)
    # бесплатные (∞) и спец-возможности
    for cid in sorted(s['ha']):
        c = КАРТЫ[cid]
        if s['spec'] >= 1 or (has(s, 'A_тайник') and 'тайник' not in s['used']):
            n = dict(s, ha=s['ha'] - {cid}, inst=s['inst'] | {cid}, hars=s['hars'] - 1)
            if s['spec'] >= 1:
                n['spec'] -= 1
            else:
                n['used'] = n['used'] | {'тайник'}
            n['inst_turn'] = n['inst_turn'] + 1
            if c.get('сразу'):
                n = c['сразу'](n)
            n = событие(n, 'установка')
            out.append(n)
    for cid in sorted(s['ho']):
        c = КАРТЫ[cid]
        if not c['треб'](s):
            continue
        бесплатно = has(s, 'A_цепная') and s.get('objturn', 0) >= 1 and 'цепная' not in s['used']
        if s['spec'] < 1 and not бесплатно:
            continue
        n = dict(s, ho=s['ho'] - {cid}, hobj=s['hobj'] - 1)
        if бесплатно:
            n['used'] = n['used'] | {'цепная'}
        else:
            n['spec'] -= 1
        if c.get('плата'):
            n = c['плата'](n)
        n = c['награда'](n)
        if c.get('усил') and c['усил'](s):
            n = c['доп'](n)
        n = dict(n, objdone=n['objdone'] + 1, objturn=n.get('objturn', 0) + 1)
        n = событие(n, 'задание')
        out.append(n)
    for cid in sorted(s['inst']):
        c = КАРТЫ[cid]
        if c.get('спец') and s['spec'] >= 1 and (cid + ':спец') not in s['used']:
            r = c['спец'](dict(s, spec=s['spec'] - 1, used=s['used'] | {cid + ':спец'}))
            if r is not None:
                out.append(r)
    return out


def _предел(s):
    if s.get('spec', 0) > 1 and s.get('spec_got', 0) >= 1:
        s = dict(s, spec=1)
    return s


def лучший(s0):
    memo = {}

    def rec(s):
        k = ключ(s)
        if k in memo:
            return memo[k]
        best = стоимость(s)
        for n in ходы(s):
            if n['spec'] > s['spec']:
                n = dict(n, spec=min(n['spec'], s['spec'] + max(0, 1 - s.get('spec_got', 0))),
                         spec_got=s.get('spec_got', 0) + 1)
            best = max(best, rec(n))
        memo[k] = best
        return best

    return rec(s0)


# ---------------------------------------------------------------- положения
def положение(**kw):
    s = dict(coins=0, kel=0, ammo=0, troph=0, units=0, adj=0, mil=0, milp=0, miners=0,
             minp=0, cubes=0, charge=0, proj=0, steps=0, dmg=0, objdone=0, vp=0, spec=1,
             cont=0, journal=0, enemy_near=1, hobj=0, hars=0, objturn=0, inst_turn=0, spec_got=0)
    s.update(kw)
    s.setdefault('acts', ())
    s['acts'] = tuple(s['acts'])
    s['inst'] = frozenset(s.get('inst', ()))
    s['ho'] = frozenset()
    s['ha'] = frozenset()
    s['used'] = frozenset()
    s['_события'] = ()
    return s


ПОЛОЖЕНИЯ = {
    'Фермер+': положение(acts=('extract', 'power', 'develop'), coins=5, kel=4, miners=3, minp=2,
                         cubes=1, charge=2, mil=1, units=1, troph=1, proj=2),
    'Фермер−': положение(acts=('extract', 'power', 'develop'), coins=1, kel=0, miners=2, minp=0,
                         cubes=1, mil=0, units=0),
    'Лазутчик+': положение(acts=('supply', 'command', 'develop'), coins=3, ammo=3, units=3, adj=1,
                           mil=1, milp=1, cubes=0, charge=2, cont=2, miners=1, minp=1),
    'Лазутчик−': положение(acts=('supply', 'command', 'develop'), coins=1, ammo=0, units=1, adj=0,
                           mil=1, milp=0, cubes=0, charge=1, cont=1),
    'Защитник+': положение(acts=('supply', 'power', 'command'), coins=3, ammo=4, units=4, adj=2,
                           mil=2, milp=2, cubes=0, charge=2, troph=2, miners=1, minp=1),
    'Защитник−': положение(acts=('supply', 'power', 'command'), coins=0, ammo=1, units=1, adj=1,
                           mil=1, milp=0, cubes=1, charge=0, miners=1),
    'Танк+': положение(acts=('supply', 'command', 'power'), coins=4, ammo=6, units=5, adj=3,
                       mil=2, milp=2, cubes=0, charge=1, troph=2, miners=1, minp=1),
    'Танк−': положение(acts=('supply', 'command', 'power'), coins=1, ammo=1, units=2, adj=0,
                       mil=2, milp=0, cubes=1, charge=0),
    'Комбо+': положение(acts=('develop', 'extract', 'power'), coins=4, kel=2, miners=2, minp=2,
                        cubes=1, mil=1, milp=0, units=2, adj=1, troph=1, journal=2),
    'Комбо−': положение(acts=('develop', 'extract', 'power'), coins=1, kel=0, miners=1, minp=0,
                        cubes=1, mil=1, units=1),
    'Эксплорер+': положение(acts=('develop', 'extract', 'command'), coins=4, kel=2, troph=2, miners=2,
                            minp=1, units=2, adj=1, ammo=2, mil=1, milp=1, cont=2),
    'Эксплорер−': положение(acts=('develop', 'extract', 'command'), coins=1, kel=1, troph=0,
                            miners=1, units=1, cont=1),
    'Владелец+': положение(acts=('supply', 'command', 'extract'), coins=4, units=5, adj=2, mil=2,
                           milp=2, ammo=2, miners=2, minp=1, cubes=0, charge=1),
    'Владелец−': положение(acts=('supply', 'command', 'extract'), coins=1, units=2, adj=0, mil=1,
                           milp=0, ammo=0, miners=1, cubes=1),
}
