"""ИКОНКИ ИГРЫ из экспорта дизайнера — «Общие компоненты / экспорт-иконки».

Дизайнер 26.09.2026: «у тебя есть все иконки, и действий, и кружочки
действий, и спец-действие, и ресурсы, и жетон первого игрока — всё есть».
Файлы «все иконки-N.png» (402×402) раскладываются в data/textures/icons/
под именами, по которым их ищет программа (kelium.gui.GameIcons).

При перевыгрузке с другой нумерацией поправить таблицу ниже — номер файла
смотрится по листу: python tools/gen_icons.py --sheet out.png

    python tools/gen_icons.py
"""
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ЭКСПОРТ = os.environ.get('KELIUM_EXPORT') or r'C:\shared\Yandex.Disk\Forged in Kelium'
ПАПКА = os.path.join(ЭКСПОРТ, 'Общие компоненты', 'экспорт-иконки')
КУДА = os.path.join(ROOT, 'data', 'textures', 'icons')
РАЗМЕР = 128

# номер файла → имя иконки
ТАБЛИЦА = {
    1: 'coin', 2: 'ammo', 3: 'kelium', 4: 'energy', 5: 'module_cube',
    6: 'trophy', 7: 'gear', 8: 'container', 9: 'arsenal', 10: 'super_arsenal',
    11: 'objective', 12: 'super_objective', 13: 'vp', 14: 'hp', 15: 'damage',
    16: 'action_ring', 17: 'spec', 18: 'energy_cell', 19: 'attack_row',
    20: 'energy_circle',
    21: 'action_build', 22: 'action_energy_swap', 23: 'action_assembly',
    24: 'action_mining', 25: 'action_movement', 26: 'action_combat',
    27: 'action_market', 28: 'action_science',
    29: 'spec_plate', 30: 'module_red', 31: 'module_blue', 32: 'storage',
    33: 'gild', 34: 'first_player', 35: 'attack', 36: 'burn', 37: 'condition',
    38: 'module_move', 39: 'hex', 40: 'cell',
    41: 'coin_big', 42: 'coin_5',
    43: 'unit_infantry', 44: 'unit_vehicle', 45: 'unit_aircraft', 46: 'unit_tower',
    47: 'redeploy', 48: 'plus_ammo', 49: 'hire', 50: 'punch',
    51: 'unit_infantry_w', 52: 'unit_vehicle_w', 53: 'unit_aircraft_w',
    54: 'unit_tower_w', 55: 'build_buildings',
}


def main():
    os.makedirs(КУДА, exist_ok=True)
    нет = []
    for n, имя in ТАБЛИЦА.items():
        путь = os.path.join(ПАПКА, 'все иконки-%d.png' % n)
        if not os.path.isfile(путь):
            нет.append(путь)
            continue
        im = Image.open(путь).convert('RGBA')
        # поля вокруг рисунка срезаем, чтобы значок занимал свой квадрат
        box = im.getbbox()
        if box:
            im = im.crop(box)
        w, h = im.size
        s = max(w, h)
        sq = Image.new('RGBA', (s, s), (0, 0, 0, 0))
        sq.paste(im, ((s - w) // 2, (s - h) // 2))
        sq = sq.resize((РАЗМЕР, РАЗМЕР), Image.LANCZOS)
        sq.save(os.path.join(КУДА, имя + '.png'), optimize=True)
    print('иконок:', len(ТАБЛИЦА) - len(нет), '->', КУДА)
    for p in нет:
        print('  НЕТ ФАЙЛА:', p)


if __name__ == '__main__':
    main()
