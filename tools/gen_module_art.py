# -*- coding: utf-8 -*-
"""РАСКЛАДКА ЖЕТОНОВ МОДУЛЕЙ — перезапускать при новом экспорте.

Дизайнер отдаёт их одной папкой, пронумерованными. Здесь они получают имена ПО
СОДЕРЖАНИЮ, а не по номеру файла: жетон опознаётся тем, что на нём напечатано,
и тогда любой набор модулей находит свою картинку сам.

  КРАСНЫЕ (прокачка атаки) — пара целей и сторона:
      mod_red_<цель>_<цель>.png        обычная сторона
      mod_red_<цель>_<цель>_gold.png   позолоченная
  СИНИЕ (прокачка найма) — цена сборки, выход и что растит золото:
      mod_blue_a<БПР>u<войск>_<что растит>.png  и  ..._gold.png

Порядок файлов прочитан по самим картинкам (см. таблицу ниже) и сверен с
data/modules: у красных ровно шесть пар целей, у синих — четыре сочетания
{2 БПР/1 войско, 1 БПР/2 войска} x {золото растит войска | боеприпас}.

ЦЕЛИ В ИМЕНИ ИДУТ В ОДНОМ И ТОМ ЖЕ ПОРЯДКЕ: пехота, техника, авиация,
здания-вышки. На картинке они могут стоять как угодно, но имя обязано быть
предсказуемым — иначе игра не соберёт его по паре целей жетона.

ЖЕТОНЫ ЦУ И ХРАНИЛИЩА. «Модули боя» 13-16 — лицо жетона уничтоженного ЦУ
(перечёркнутый боеприпас у рода войск), 17 — его оборот (три звезды над
руинами); «улучшение планшета хранилища» 1 — энергия, 2 — склад. Их имена
просит kelium.report.ModuleArt.cu/store.

Запуск:  python tools/gen_module_art.py . "<папка экспорта>"
Пишет:   data/textures/module/*.png
"""
import os
import sys

from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
SRC = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
    os.path.expanduser('~'), 'Yandex.Disk', 'Forged in Kelium',
    'Общие компоненты', 'экспорт-жетоны-модулей')
OUT = os.path.join(ROOT, 'data', 'textures', 'module')

# Жетон на экране не бывает шире полутора сотен пикселей; четыреста — запас
# втрое, и при уменьшении работают мипмапы.
ШИРИНА = 400

# Прочитано по картинкам: у каждого номера своя пара целей и своя сторона.
КРАСНЫЕ = [
    (1,  'infantry_aircraft',         False),
    (2,  'infantry_vehicle',          False),
    (3,  'infantry_buildings_towers', False),
    (4,  'vehicle_aircraft',          False),
    (5,  'vehicle_buildings_towers',  False),
    (6,  'aircraft_buildings_towers', False),
    (7,  'infantry_aircraft',         True),
    (8,  'infantry_vehicle',          True),
    (9,  'infantry_buildings_towers', True),
    (10, 'vehicle_aircraft',          True),
    (11, 'vehicle_buildings_towers',  True),
    (12, 'aircraft_buildings_towers', True),
]

# Верхний ряд — цена в боеприпасах, нижний — сколько войск, жёлтая стрелка
# показывает, что поднимает золото. Экспорт 23.09.2026 (набор C30 из
# modules.3.0.0): 2/2 золотом в войска, 2/2 золотом в боеприпас, 3/1 и 1/3.
# Золотые 5-8 прочитаны по картинке: 5 = 2/3 (золото 2/2 в войска), 6 = 3/2
# (2/2 в боеприпас), 7 = 3/2 (3/1 в войска), 8 = 2/3 (1/3 в боеприпас).
СИНИЕ = [
    (1, 'a2u2_units', False),
    (2, 'a2u2_ammo',  False),
    (3, 'a3u1_units', False),
    (4, 'a1u3_ammo',  False),
    (5, 'a2u2_units', True),
    (6, 'a2u2_ammo',  True),
    (7, 'a3u1_units', True),
    (8, 'a1u3_ammo',  True),
]

# Жетоны уничтоженного ЦУ (лицо — перечёркнутый боеприпас у рода войск, оборот
# один на всех — три звезды над руинами) и жетоны улучшения хранилища.
ПРОЧИЕ = [
    ('Жетон модулей боя-13.png', 'mod_cu_infantry'),
    ('Жетон модулей боя-14.png', 'mod_cu_vehicle'),
    ('Жетон модулей боя-15.png', 'mod_cu_aircraft'),
    ('Жетон модулей боя-16.png', 'mod_cu_tower'),
    ('Жетон модулей боя-17.png', 'mod_cu_trophy'),
    ('Жетон улучшения планшета хранилища-1.png', 'mod_store_energy'),
    ('Жетон улучшения планшета хранилища-2.png', 'mod_store_cells'),
]


def положить(файл, имя, беды):
    путь = os.path.join(SRC, файл)
    if not os.path.isfile(путь):
        беды.append('нет файла %s' % файл)
        return None
    im = Image.open(путь).convert('RGBA')
    k = ШИРИНА / im.width
    return имя, im.resize((ШИРИНА, max(1, int(round(im.height * k)))), Image.LANCZOS)


def main():
    беды = []
    готово = []
    for n, имя, gold in КРАСНЫЕ:
        r = положить('Жетон модулей боя-%d.png' % n,
                     'mod_red_%s%s' % (имя, '_gold' if gold else ''), беды)
        if r:
            готово.append(r)
    for n, имя, gold in СИНИЕ:
        r = положить('Жетоны модулей сборки-%d.png' % n,
                     'mod_blue_%s%s' % (имя, '_gold' if gold else ''), беды)
        if r:
            готово.append(r)
    for файл, имя in ПРОЧИЕ:
        r = положить(файл, имя, беды)
        if r:
            готово.append(r)
    if беды:
        print('НИЧЕГО НЕ ЗАПИСАНО:')
        for b in беды:
            print('  ' + b)
        return 1
    # Пары обязаны быть полными: у каждой обычной стороны есть золотая.
    имена = {имя for имя, _ in готово}
    for имя in sorted(имена):
        if имя.endswith('_gold') or имя.startswith(('mod_cu_', 'mod_store_')):
            continue
        if имя + '_gold' not in имена:
            print('ОСТОРОЖНО: у %s нет золотой стороны' % имя)
    os.makedirs(OUT, exist_ok=True)
    for имя, im in готово:
        im.save(os.path.join(OUT, имя + '.png'), optimize=True)
    print('записано жетонов: %d (красных 12, синих 8, ЦУ 5, хранилища 2)'
          % len(готово))
    return 0


sys.exit(main())
