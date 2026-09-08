# -*- coding: utf-8 -*-
"""РАСКЛАДКА ПЕЧАТНЫХ ГЕКСОВ — тайлы зарождения и запретные гексы.

Дизайнер отдаёт их одной папкой: «Жетон гекса зарождения-1…4» и «запретные/
Жетон запретного гекса-1…9». Здесь они получают имена, по которым их ищет игра
({@code kelium.report.Textures.field}), и размер под экран.

ПОРЯДОК ТАЙЛОВ ЗАРОЖДЕНИЯ — по печатному келемию, и он сверяется с движком
(Scenario: обычный тайл 4 на лице и 3 на обороте, стартовый — 3 и 2):

  1 → spawn                 лицо обычного тайла, 4 келемия
  2 → spawn_flipped         его оборот, 3
  3 → spawn_start           лицо стартового, 3
  4 → spawn_start_flipped   его оборот, 2

ЗАПРЕТНЫХ ДЕВЯТЬ РАЗНЫХ, и это не прихоть: на поле их лежит по нескольку, и
одинаковые читаются как узор. Игра берёт вариант по координатам гекса — на одном
поле они не повторяются подряд.

Запуск:  python tools/gen_hex_art.py . "<папка экспорта>"
Пишет:   data/textures/field/spawn*.png и hex_forbidden*.png
"""
import os
import sys

from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
SRC = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
    os.path.expanduser('~'), 'Yandex.Disk', 'Forged in Kelium',
    'Общие компоненты', 'экспорт-гексы')
OUT = os.path.join(ROOT, 'data', 'textures', 'field')

# ШИРИНА ТЕКСТУРЫ. Гекс на экране бывает от двадцати пикселей до двухсот;
# шестьсот сорок — запас втрое к самому крупному показу. Мельче исходника в
# полтора раза, а разницы не видно: при уменьшении работают мипмапы.
ШИРИНА = 640

ЗАРОЖДЕНИЕ = [
    ('Жетон гекса зарождения-1.png', 'spawn'),
    ('Жетон гекса зарождения-2.png', 'spawn_flipped'),
    ('Жетон гекса зарождения-3.png', 'spawn_start'),
    ('Жетон гекса зарождения-4.png', 'spawn_start_flipped'),
]


def положить(путь, имя, беды):
    if not os.path.isfile(путь):
        беды.append('нет файла %s' % os.path.basename(путь))
        return None
    im = Image.open(путь).convert('RGBA')
    k = ШИРИНА / im.width
    return имя, im.resize((ШИРИНА, int(round(im.height * k))), Image.LANCZOS)


def main():
    беды = []
    готово = []
    for файл, имя in ЗАРОЖДЕНИЕ:
        r = положить(os.path.join(SRC, файл), имя, беды)
        if r:
            готово.append(r)

    запретные = sorted(
        f for f in os.listdir(os.path.join(SRC, 'запретные'))
        if f.lower().endswith('.png'))
    if not запретные:
        беды.append('в папке «запретные» нет картинок')
    for i, файл in enumerate(запретные, start=1):
        r = положить(os.path.join(SRC, 'запретные', файл), 'hex_forbidden_%d' % i, беды)
        if r:
            готово.append(r)
    if запретные:
        # Общее имя — на случай, если игра спросит запретный гекс без варианта.
        r = положить(os.path.join(SRC, 'запретные', запретные[0]), 'hex_forbidden', беды)
        if r:
            готово.append(r)

    if беды:
        print('НИЧЕГО НЕ ЗАПИСАНО:')
        for b in беды:
            print('  ' + b)
        return 1
    os.makedirs(OUT, exist_ok=True)
    for имя, im in готово:
        im.save(os.path.join(OUT, имя + '.png'), optimize=True)
    print('записано: %d (тайлов зарождения %d, запретных %d + общий)'
          % (len(готово), len(ЗАРОЖДЕНИЕ), len(запретные)))
    return 0


sys.exit(main())
