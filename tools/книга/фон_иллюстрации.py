# -*- coding: utf-8 -*-
"""Иллюстрации под полосами книги правил (дизайнер 14.10.2026).

У каждой внутренней полосы своя картинка из «Иллюстрации/фоны и сцены».
Она лежит под мозаикой и блоками: у краёв листа — в полный цвет, к полю
текста почти пропадает (маска из двух градиентов — по горизонтали и по
вертикали, сложенных вместе, получается «рамка»). На первой полосе главы
картинка видна в полный цвет и сверху, под заголовком главы — как в книге
правил «Загадочных существ».

Картинки копируются в «вёрстка/илл» уменьшенными, имя — по хешу. Запуск
после restyle.py (так делает собрать_книгу.py): python фон_иллюстрации.py
"""
import glob
import hashlib
import io
import os
import re

from PIL import Image

D = os.path.dirname(os.path.abspath(__file__))
КНИГА = glob.glob(os.path.join(os.path.dirname(os.path.dirname(D)), "rules", "Книга правил*", "вёрстка", "Книга правил.html"))[0]
ИЛЛ = os.path.join(os.path.dirname(КНИГА), "илл")
ПАПКА = os.path.join(os.path.expanduser("~"), "Yandex.Disk", "Forged in Kelium", "Иллюстрации", "фоны и сцены")

# полоса → начало имени файла в папке (подобрано по смыслу глав)
КАРТИНКИ = {
    3: "seedream-5.0-pro_Horizontal_composition_camera_LOW_at_knee_height",
    4: "seedream-5.0-pro_Wide_low_three-quarter_shot_evening_deep_space",
    5: "seedream-5.0-pro_Wide_horizontal_composition_in_landscape_format",
    6: "seedream-5.0-pro_Straight-down_close-up_shot_camera_directly_above_a_cluttered_desk",
    7: "seedream-5.0-pro_Horizontal_composition_a_large_FACTION_RESOURCE_WAREHOUSE_INTERIOR",
    8: "seedream-5.0-pro_High_three-quarter_camera_looking_down_across_a_large_rectangular_holographic",
    9: "seedream-5.0-pro_Wide_horizontal_composition_camera_at_chest_height_close_enough_that_the_people_-0 (3)",
    10: "seedream-5.0-pro_Top-down_three-quarter_cinematic_illustration_an_open_ridge_line",
    11: "seedream-5.0-pro_Low_three-quarter_shot_ochre_wasteland_under_a_low_grey_dust-choked_sky._Dominat",
    12: "seedream-5.0-pro_Low_dramatic_three-quarter_angle_looking_down_a_wide_valley",
    13: "seedream-5.0-pro_Wide_interior_view_of_a_fortified_barracks_muster_hall",
    14: "seedream-5.0-pro_Interior_of_a_supply_depot_under_a_corrugated_composite_roof",
    15: "seedream-5.0-pro_Wide_interior_view_of_a_vehicle_maintenance_bay",
    16: "seedream-5.0-pro_Low_three-quarter_shot_tight_framing_ochre_wasteland_under_a_low_grey_dust-choke-0.",
    17: "seedream-5.0-pro_Vertical_composition_camera_looking_straight_down_onto_a_drafting_table",
    18: "seedream-5.0-pro_Wide_interior_view_of_a_design_hall_in_a_military_research_facility",
    19: "seedream-5.0-pro_Wide_horizontal_composition_camera_at_chest_height_close_enough_that_the_people_-0 (1)",
    20: "seedream-5.0-pro_Wide_horizontal_composition_camera_at_chest_height_close_enough_that_the_people_-0 (2)",
    21: "seedream-5.0-pro_Wide_horizontal_composition_camera_at_chest_height_close_enough_that_the_people_-0 (4)",
    22: "seedream-5.0-pro_Wide_horizontal_composition_camera_at_chest_height_close_enough_that_the_people_-0 (5)",
    23: "seedream-5.0-pro_Extreme_foreground_mid-ground_and_far_distance_stacked_in_one_frame",
    24: "seedream-5.0-pro_Low_camera_from_street_level_looking_steeply_up",
    25: "seedream-5.0-pro_Low_three-quarter_shot_tight_framing_ochre_wasteland_under_a_low_grey_dust-choke-0 (1)",
    26: "seedream-5.0-pro_Horizontal_landscape_composition_low_three-quarter_camera_looking_slightly_up_th-0.",
    27: "seedream-5.0-pro_Horizontal_landscape_composition_low_three-quarter_camera_looking_slightly_up_th-0 (2)",
    28: "seedream-5.0-pro_Wide_low_three-quarter_interior_shot_tight_framing_a_vast_industrial_assembly",
    29: "seedream-5.0-pro_Horizontal_composition_camera_LOW_at_the_height_of_the_track_guard",
    30: "seedream-5.0-pro_Low_three-quarter_shot_tight_framing_strong_diagonal._Foreground_a_hunched",
    31: "seedream-5.0-pro_Sweeping_atmospheric_battle_scene",
    32: "seedream-5.0-pro_Low_dynamic_three-quarter_shot_camera_near_ground_level",
    33: "seedream-5.0-pro_Low_three-quarter_shot_a_fenced-off_corner_of_the_market",
    34: "seedream-5.0-pro_Wide_interior_view_of_a_clean_analytical_laboratory",
    35: "seedream-5.0-pro_Wide_interior_view_of_a_prototype_assembly_bay",
    36: "seedream-5.0-pro_Wide_interior_view_of_an_observation_gallery",
    37: "seedream-5.0-pro_Wide_interior_view_of_an_underground_air_operations_deck",
    38: "seedream-5.0-pro_Wide_high_three-quarter_vantage_inside_a_tall_pale_industrial_hall",
    39: "seedream-5.0-pro_Wide_three-quarter_shot_camera_low_near_ground_level_a_column_of_three_heavy",
}

CSS = """
  /* ---- иллюстрации под полосами (фон_иллюстрации.py, 14.10.2026) ---- */
  .илл-фон { position: absolute; inset: 0; z-index: -2; pointer-events: none;
    background: center / cover no-repeat;
    -webkit-mask-image: linear-gradient(to right, #000 0, #000 4mm, rgba(0,0,0,.05) 24mm, rgba(0,0,0,.05) calc(100% - 24mm), #000 calc(100% - 4mm), #000 100%),
                        linear-gradient(to bottom, #000 0, #000 4mm, rgba(0,0,0,.05) 24mm, rgba(0,0,0,.05) calc(100% - 24mm), #000 calc(100% - 4mm), #000 100%);
    mask-image: linear-gradient(to right, #000 0, #000 4mm, rgba(0,0,0,.05) 24mm, rgba(0,0,0,.05) calc(100% - 24mm), #000 calc(100% - 4mm), #000 100%),
                linear-gradient(to bottom, #000 0, #000 4mm, rgba(0,0,0,.05) 24mm, rgba(0,0,0,.05) calc(100% - 24mm), #000 calc(100% - 4mm), #000 100%); }
  /* первая полоса главы: сверху — картинка в полный цвет под заголовком */
  .илл-фон.глава-илл {
    -webkit-mask-image: linear-gradient(to bottom, #000 0, #000 30mm, rgba(0,0,0,.05) 62mm),
                        linear-gradient(to right, #000 0, #000 4mm, rgba(0,0,0,.05) 24mm, rgba(0,0,0,.05) calc(100% - 24mm), #000 calc(100% - 4mm), #000 100%),
                        linear-gradient(to bottom, rgba(0,0,0,0) 0, rgba(0,0,0,0) calc(100% - 24mm), #000 calc(100% - 4mm), #000 100%);
    mask-image: linear-gradient(to bottom, #000 0, #000 30mm, rgba(0,0,0,.05) 62mm),
                linear-gradient(to right, #000 0, #000 4mm, rgba(0,0,0,.05) 24mm, rgba(0,0,0,.05) calc(100% - 24mm), #000 calc(100% - 4mm), #000 100%),
                linear-gradient(to bottom, rgba(0,0,0,0) 0, rgba(0,0,0,0) calc(100% - 24mm), #000 calc(100% - 4mm), #000 100%); }
  /* вводный абзац главы — на светлой подложке, чтобы читался на картинке */
  .стр:has(.глава-илл) .вводка { background: rgba(247,241,225,.9); padding: 2mm 3mm; margin-bottom: 4mm;
    outline: .18mm solid var(--кант); outline-offset: -.18mm; }
  .стр:has(.глава-илл) .с-заглавной > .блок { background: rgba(247,241,225,.92); }
  /* заголовок главы на картинке — на светлой подложке, чтобы читался */
  .стр:has(.глава-илл) .глава .гл-т { background: rgba(247,241,225,.88); padding: .6mm 2.4mm .4mm;
    box-shadow: 0 0 3mm 1.5mm rgba(247,241,225,.88); }
"""


def файл(начало):
    for f in sorted(os.listdir(ПАПКА)):
        if f.startswith(начало) and f.lower().endswith(".jpg"):
            return os.path.join(ПАПКА, f)
    raise SystemExit("нет картинки: " + начало)


def в_илл(путь):
    данные = open(путь, "rb").read()
    имя = hashlib.sha1(данные).hexdigest()[:16] + ".jpg"
    куда = os.path.join(ИЛЛ, имя)
    if not os.path.exists(куда):
        im = Image.open(io.BytesIO(данные)).convert("RGB")
        im.thumbnail((2600, 2600), Image.LANCZOS)
        im.save(куда, "JPEG", quality=84, optimize=True)
    return "илл/" + имя


def main():
    t = io.open(КНИГА, encoding="utf-8").read()
    t = re.sub(r'<div class="илл-фон[^"]*" style="[^"]*"></div>', "", t)
    a = t.find("  /* ---- иллюстрации под полосами")
    if a >= 0:
        b = t.index("</style>", a)
        t = t[:a] + t[b:]
    t = t.replace("</style>", CSS + "</style>", 1)
    части, n, поз = [], 0, 0
    for m in re.finditer(r'<div class="стр( [^"]*)?">', t):
        n += 1
        if n not in КАРТИНКИ:
            continue
        # первая полоса главы — та, где стоит заголовок главы
        конец = t.find('<div class="стр', m.end())
        глава = '<div class="глава"' in t[m.end():конец if конец > 0 else len(t)]
        вставка = '<div class="илл-фон%s" style="background-image:url(&quot;%s&quot;)"></div>' % (
            " глава-илл" if глава else "", в_илл(файл(КАРТИНКИ[n])))
        части.append(t[поз:m.end()] + вставка)
        поз = m.end()
    t = "".join(части) + t[поз:]
    io.open(КНИГА, "w", encoding="utf-8").write(t)
    print("иллюстрации под полосами:", len(части))


if __name__ == "__main__":
    main()
