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
ЭКСПОРТ = os.environ.get('KELIUM_EXPORT') or os.path.join(
    os.path.expanduser('~'), 'Yandex.Disk', 'Forged in Kelium')
ПАПКА = os.path.join(ЭКСПОРТ, 'Общие компоненты', 'экспорт-иконки')
КУДА = os.path.join(ROOT, 'data', 'textures', 'icons')
РАЗМЕР = 128

# номер файла → имя иконки
ТАБЛИЦА = {
    # выгрузка 27.09.2026 — дизайнер пересобрал все иконки, нумерация новая
    1: 'coin', 2: 'ammo_box', 3: 'ammo', 4: 'kelium_raw', 5: 'kelium',
    6: 'energy_battery', 7: 'energy', 8: 'gear', 9: 'module_cube',
    10: 'trophy_kelium', 11: 'any_cube', 12: 'infinity_cube', 13: 'container',
    14: 'arsenal', 15: 'super_arsenal', 16: 'objective', 17: 'super_objective',
    18: 'objective_discard', 19: 'container_discard', 20: 'arsenal_burn',
    21: 'vp', 22: 'hp', 23: 'damage', 24: 'action_ring', 25: 'spec',
    26: 'instant', 27: 'infinity', 28: 'energy_cell', 29: 'attack_row',
    30: 'energy_circle',
    31: 'action_build', 32: 'action_energy_swap', 33: 'action_assembly',
    34: 'action_mining', 35: 'action_movement', 36: 'action_combat',
    37: 'action_market', 38: 'action_science',
    39: 'spec_plate', 40: 'module_red', 41: 'module_blue', 42: 'storage',
    43: 'gild', 44: 'first_player', 45: 'science_vp', 46: 'hex_cubes',
    47: 'hex_rotate', 48: 'hex_split', 49: 'attack', 50: 'burn', 51: 'condition',
    52: 'module_move', 53: 'hex', 54: 'cell', 55: 'coin_big', 56: 'coin_5',
    57: 'unit_infantry', 58: 'unit_vehicle', 59: 'unit_aircraft', 60: 'unit_tower',
    61: 'redeploy', 62: 'plus_ammo', 63: 'hire', 64: 'punch',
    65: 'unit_infantry_w', 66: 'unit_vehicle_w', 67: 'unit_aircraft_w',
    68: 'unit_tower_w', 69: 'build_buildings', 70: 'recolor_buildings',
    71: 'player', 72: 'other_turn_energy', 73: 'container_other_turn',
    74: 'other_turn', 75: 'arrow',
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
