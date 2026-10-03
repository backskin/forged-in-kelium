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

# номер файла → имя иконки (или несколько имён — одна картинка под разными
# именами, по которым её ищет программа)
ТАБЛИЦА = {
    # выгрузка 03.10.2026 — дизайнер сдвинул нумерацию и подправил обводки и
    # цвета; новые 86–102 (утили арсенала, карты рынка, ремонт, здания)
    1: 'coin', 2: 'ammo_box', 3: 'ammo', 4: 'kelium_raw', 5: 'kelium',
    6: 'energy_battery', 7: 'energy', 8: 'gear', 9: 'module_cube',
    10: 'trophy_kelium', 11: 'any_cube', 12: 'infinity_cube', 13: 'container',
    14: 'container_light', 15: 'arsenal', 16: 'super_arsenal', 17: 'objective',
    18: 'super_objective', 19: 'objective_discard', 20: 'container_discard',
    21: 'arsenal_burn', 22: 'vp', 23: 'hp', 24: 'damage', 25: 'action_ring',
    26: 'spec', 27: 'instant', 28: 'infinity', 29: 'energy_cell',
    30: ('military_building', 'attack_row'), 31: 'energy_circle',
    32: 'build_mode',                                  # знак стройки (кран в треугольнике)
    33: 'action_build', 34: ('action_energy_swap', 'action_power'),
    35: ('action_assembly', 'action_supply'), 36: ('action_mining', 'action_extract'),
    37: 'action_command', 38: 'action_movement', 39: 'action_combat',
    40: 'action_develop', 41: 'action_market', 42: 'action_science',
    43: 'science_board', 44: 'spec_plate', 45: 'module_red', 46: 'module_blue',
    47: 'storage', 48: 'gild', 49: 'first_player', 50: 'science_vp',
    51: 'hex_cubes', 52: 'hex_rotate', 53: 'hex_split', 54: 'attack', 55: 'burn',
    56: 'condition', 57: 'module_move', 58: 'hex', 59: 'cell', 60: 'coin_big',
    61: 'coin_5', 62: 'unit_infantry', 63: 'unit_vehicle', 64: 'unit_aircraft',
    65: 'unit_tower', 66: 'redeploy', 67: 'step', 68: 'plus_ammo', 69: 'hire',
    70: 'punch', 71: 'unit_infantry_w', 72: 'unit_vehicle_w', 73: 'unit_aircraft_w',
    74: 'unit_tower_w', 75: 'build_buildings', 76: 'recolor_buildings',
    77: 'player', 78: ('other_turn_energy', 'reaction'),   # реакция на чужое действие
    79: 'container_other_turn', 80: 'other_turn', 81: 'arrow',
    82: 'build_zones', 83: 'build_own_hex', 84: 'build_behind_wall',
    85: 'container_closed', 86: 'grab_first_player', 87: 'kelium_and_mining',
    88: 'hire_two_kinds', 89: 'spec_three', 90: 'build_for_coin',
    91: 'steal_arsenal', 92: 'order_arrow', 93: 'discard_enemy_arsenal',
    94: 'steal_objective', 95: 'market_card', 96: 'market_card_for_one',
    97: 'hex_plus', 98: 'heal', 99: 'hex_cross', 100: 'hp_cross',
    101: 'building_miner', 102: 'building_plant',
}


def main():
    os.makedirs(КУДА, exist_ok=True)
    нет = []
    for n, имена in ТАБЛИЦА.items():
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
        for имя in (имена if isinstance(имена, tuple) else (имена,)):
            sq.save(os.path.join(КУДА, имя + '.png'), optimize=True)
    print('иконок:', len(ТАБЛИЦА) - len(нет), '->', КУДА)
    for p in нет:
        print('  НЕТ ФАЙЛА:', p)


if __name__ == '__main__':
    main()
