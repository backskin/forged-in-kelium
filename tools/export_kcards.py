# -*- coding: utf-8 -*-
"""Карты выпуска 27.09 → файлы мастерской карт (.kcard): открываются в
«Мастерской карт» и правятся там.

    python tools/export_kcards.py [папка]
"""
import os
import sys

import yaml

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_cards_from_blanks as g  # noqa: E402

ПАПКА = sys.argv[1] if len(sys.argv) > 1 else os.path.join(g.ОБЩИЕ, 'мастерская карт',
                                                           'выпуск 27.09.2026')
НАГРАДА = {'extract': '34', 'power': '32', 'supply': 'действие — снабжение',
           'command': 'действие — командование', 'develop': 'действие — развитие',
           'mining': '34', 'combat': '36', 'energy_swap': '32', 'assembly': '33',
           'movement': '35', 'market': '37', 'science': '38'}
ТЕКСТ = {'ammo': '3', 'troops': '49', 'building': '69', 'kel': '5', 'coin': '1', 'box': '13',
         'heart': '22', 'spec': '25', 'gear': '8', 'cell': '28'}


def ключ(v):
    v = g.И.get(v, g.ИА.get(v, v)) if isinstance(v, str) else v
    return str(v)


def иконки(t):
    for a, n in ТЕКСТ.items():
        t = t.replace('{%s}' % a, '{%s}' % n)
    return t


def текст(v):
    if v is None:
        return ''
    return иконки('\n'.join(v) if isinstance(v, list) else str(v))


def задание(к):
    ш = к['шаблон']
    f = {'тип': 'Задание', 'рисунок': ш // 2 if ш % 2 == 0 else (ш - 1) // 2,
         'слот': '▶' if к['слот'] == 'spec' else '∞'}
    if к.get('реакция'):
        f['верх_вид'] = 'реакция'
        f['заголовок_верха'] = к['реакция']
    elif к.get('иконка_верха') == 'move2max':
        f['верх_вид'] = '2 max'
    else:
        f['верх_вид'] = 'иконка'
        f['иконка_верха'] = '{%s}' % ключ(к['иконка_верха'])
    f['верх'] = текст(к['верх'])
    f['имя'] = к.get('имя') or к['семейство']
    f['условие'] = текст(к['условие'])
    f['награда'] = '{25} {25} {25}' if к['награда'] == ['spec3'] else \
        ' '.join('{%s}' % НАГРАДА[x] for x in к['награда'])
    f['дополнительно'] = текст(к.get('дополнительно'))
    f['доп_награда'] = ' '.join('{%s}' % ключ(в) for в, n in к.get('доп_награда', [])
                                for _ in range(n))
    f['номер'] = к['номер']
    return f


def арсенал(к):
    f = {'тип': 'Арсенал', 'спец': bool(к.get('спец'))}
    f['верх'] = текст(к['верх'])
    f['верх_по_центру'] = bool(к.get('верх_по_центру'))
    x0 = к.get('верх_x', 258)
    фишки = [(cx, '{%s}' % ключ(n)) for n, cx, cy, s in к.get('верх_иконки', [])]
    фишки += [(cx, t) for t, cx, cy in к.get('верх_знаки', [])]
    фишки.sort()
    f['верх_слева'] = ' '.join(t for cx, t in фишки if cx <= x0 + 60)
    f['верх_справа'] = ' '.join(t for cx, t in фишки if cx > x0 + 60)
    f['имя'] = к['имя']
    f['низ'] = текст(к.get('низ'))
    ряд = []
    for вид, з, cx, cy, s in к.get('ряд', []):
        ряд.append('{%s}' % з if вид == 'и' else з if вид == 'т' else '{/}')
    f['ряд'] = ' '.join(ряд)
    f['звезда'] = bool(к.get('звезда'))
    с = к.get('спец')
    if с:
        f['цена'] = str(с['цена'])
        f['цена_иконка'] = '{1}'
        f['спец_иконка'] = '{%s}' % ключ(с['иконка'])
        f['спец_знак'] = с.get('знак', '')
        f['спец_текст'] = '\n'.join(с['текст'])
        f['контейнер'] = True
    f['номер'] = к['номер']
    return f


def main():
    os.makedirs(ПАПКА, exist_ok=True)
    n = 0
    for к in g.все_задания():
        f = задание(к)
        имя = 'Задание %02d — %s.kcard' % (к['номер'], f['имя'])
        yaml.safe_dump(f, open(os.path.join(ПАПКА, имя), 'w', encoding='utf-8'),
                       allow_unicode=True, sort_keys=False, width=200)
        n += 1
    for к in g.АРСЕНАЛ:
        f = арсенал(к)
        имя = 'Арсенал %d — %s.kcard' % (к['номер'], f['имя'])
        yaml.safe_dump(f, open(os.path.join(ПАПКА, имя), 'w', encoding='utf-8'),
                       allow_unicode=True, sort_keys=False, width=200)
        n += 1
    print('карт:', n, ПАПКА)


if __name__ == '__main__':
    main()
