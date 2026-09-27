# -*- coding: utf-8 -*-
"""ВЫПУСК 27.09.2026 — последние версии карт под пять развилок.

Раскладывает по папкам дизайнера на Диске, в подпапку «выпуск 27.09.2026 —
пять развилок» внутри той же папки экспорта:
  * задания (11) и арсенал (7) — рисуются заново с пустых шаблонов
    (tools/gen_cards_from_blanks.py), имя файла — как у печатной карты,
    которую новая заменяет;
  * приказы (20 лиц и 4 рубашки) — из «приказы-шаблоны/черновик 27.09»
    и комплекта;
  * памятки (2) — из «памятки-шаблоны/черновик 27.09».
Те же лица заданий и арсенала кладёт в игру (data/textures/card, 600 px по
ширине) и в комплект (лица-крупно и лица на Диске).

    python tools/release_cards_2026_09_27.py
"""
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_cards_from_blanks as g  # noqa: E402
from PIL import Image  # noqa: E402

ВЫПУСК = 'выпуск 27.09.2026 — пять развилок'
ДИСК = g.ДИСК
ОБЩИЕ = g.ОБЩИЕ
ИГРОКА = os.path.join(ДИСК, 'Компоненты игрока')
КОМПЛЕКТ_ДИСК = os.path.join(ДИСК, 'комплект — пять развилок (27.09.2026)', 'лица')
КОМПЛЕКТ_РЕПО = os.path.join(g.ROOT, 'design-docs', 'комплект — пять развилок (27.09.2026)',
                             'лица-крупно')
ТЕКСТУРЫ = os.path.join(g.ROOT, 'data', 'textures', 'card')

ЦВЕТ = {'blue': 'синий', 'green': 'зелёный', 'red': 'красный', 'yellow': 'жёлтый'}
ПРИКАЗ = {'settle': 'ОСВОИТЬ', 'mobilize': 'МОБИЛИЗОВАТЬ', 'advance': 'НАСТУПАТЬ',
          'secure': 'КОНТРОЛИРОВАТЬ', 'research': 'ИССЛЕДОВАТЬ'}


def в_игру(im, вид, cid):
    w = 600
    мал = im.resize((w, round(im.height * w / im.width)), Image.LANCZOS)
    мал.save(os.path.join(ТЕКСТУРЫ, вид, cid + '.png'))
    for папка in (КОМПЛЕКТ_ДИСК, КОМПЛЕКТ_РЕПО):
        путь = os.path.join(папка, вид)
        if os.path.isdir(путь):
            (мал if папка == КОМПЛЕКТ_ДИСК else im).save(os.path.join(путь, cid + '.png'))


def main():
    # задания
    куда = os.path.join(ОБЩИЕ, 'экспорт-задания', 'награды-действия', ВЫПУСК)
    os.makedirs(куда, exist_ok=True)
    состав = ['Задания — нарисованы с пустых шаблонов. Имя файла = печатная карта, которую',
              'новая заменяет.', '']
    for к in g.ЗАДАНИЯ:
        im = g.задание(к)
        im.save(os.path.join(куда, 'задания-с-действи-%d.png' % к['печать']))
        в_игру(im, 'objective', к['id'])
        состав.append('задания-с-действи-%d.png — %s «%s», № %02d' % (
            к['печать'], к['id'], к['семейство'], к['номер']))
    open(os.path.join(куда, 'СОСТАВ.txt'), 'w', encoding='utf-8').write('\n'.join(состав) + '\n')

    # арсенал
    куда = os.path.join(ОБЩИЕ, 'экспорт-арсенал', ВЫПУСК)
    os.makedirs(куда, exist_ok=True)
    состав = ['Арсенал — нарисован с пустых шаблонов арсенал-1 (∞) и арсенал-2 (▶).',
              'Имя файла = печатная карта, которую новая заменяет.', '']
    for к in g.АРСЕНАЛ:
        im = g.арсенал(к)
        im.save(os.path.join(куда, 'арсенал-%d.png' % к['печать']))
        в_игру(im, 'arsenal', к['id'])
        состав.append('арсенал-%d.png — %s «%s», № %d' % (к['печать'], к['id'], к['имя'],
                                                          к['номер']))
    open(os.path.join(куда, 'СОСТАВ.txt'), 'w', encoding='utf-8').write('\n'.join(состав) + '\n')

    # приказы
    куда = os.path.join(ИГРОКА, 'экспорт-карты-приказов', ВЫПУСК)
    os.makedirs(куда, exist_ok=True)
    черновик = os.path.join(ИГРОКА, 'приказы-шаблоны', 'черновик 27.09')
    for цвет, ру in ЦВЕТ.items():
        for код, имя in ПРИКАЗ.items():
            shutil.copy2(os.path.join(черновик, '%s_%s.png' % (цвет, код)),
                         os.path.join(куда, '%s — %s.png' % (ру, имя)))
        shutil.copy2(os.path.join(ТЕКСТУРЫ, 'orders', 'back_%s.png' % цвет),
                     os.path.join(куда, 'рубашка — %s.png' % ру))

    # памятки
    куда = os.path.join(ИГРОКА, 'экспорт-памятки', ВЫПУСК)
    os.makedirs(куда, exist_ok=True)
    черновик = os.path.join(ОБЩИЕ, 'памятки-шаблоны', 'черновик 27.09')
    for f in os.listdir(черновик):
        if f.lower().endswith('.png'):
            shutil.copy2(os.path.join(черновик, f), os.path.join(куда, f))
    print('выпуск готов:', ВЫПУСК)


if __name__ == '__main__':
    main()
