# -*- coding: utf-8 -*-
"""ОБЛОЖКИ В ПЕЧАТНЫЙ РАЗМЕР.

Арт приходит от дизайнера квадратом 4000+ пикселей и весом под пятнадцать
мегабайт на файл. В книгу такой класть незачем: страница 210 мм, и при 300 dpi
это 2480 пикселей — всё, что сверх, в печати не видно, а вес документа растёт
вдвое от каждой обложки.

Скрипт кладёт рядом с исходником печатную версию .jpg. Исходные .png остаются
у дизайнера нетронутыми: пережимаем копию, а не оригинал.

Запуск: python tools/обложки-в-печать.py
"""
import io
import os
import sys

from PIL import Image

ПАПКА = 'docs/обложки'
СТОРОНА_ПИКСЕЛЕЙ = 2480        # 210 мм при 300 dpi
КАЧЕСТВО = 90


def main():
    sys.stdout.reconfigure(encoding='utf-8')
    сделано = 0
    for имя in sorted(os.listdir(ПАПКА)):
        if not имя.lower().endswith('.png'):
            continue
        путь = os.path.join(ПАПКА, имя)
        цель = os.path.splitext(путь)[0] + '.jpg'
        im = Image.open(путь).convert('RGB')
        было = im.size
        if im.size != (СТОРОНА_ПИКСЕЛЕЙ, СТОРОНА_ПИКСЕЛЕЙ):
            im = im.resize((СТОРОНА_ПИКСЕЛЕЙ, СТОРОНА_ПИКСЕЛЕЙ), Image.LANCZOS)
        im.save(цель, quality=КАЧЕСТВО, optimize=True)
        print('%-28s %sx%s -> %s   %.1f МБ -> %.1f МБ'
              % (имя, было[0], было[1], СТОРОНА_ПИКСЕЛЕЙ,
                 os.path.getsize(путь) / 1e6, os.path.getsize(цель) / 1e6))
        сделано += 1
    if not сделано:
        print('в %s нет ни одного .png' % ПАПКА)


if __name__ == '__main__':
    main()
