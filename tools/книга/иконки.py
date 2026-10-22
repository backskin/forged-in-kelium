# -*- coding: utf-8 -*-
"""ИКОНКИ ИГРЫ — по одному файлу на иконку, из настоящих компонентов.

Список нужных иконок ведёт `tools/справочник.py` (68 штук). Рисовать их заново
незачем: почти всё уже нарисовано на картоне и на картах. Здесь каждая иконка
ВЫРЕЗАЕТСЯ из своего источника — набора текстур `data/textures`, экспорта карт
дизайнера или карт-памяток, — и кладётся в `rules/иконки/<ключ>.png`.

Чего вырезать не из чего, тот файл не создаётся: справочник напечатает на его
месте пустую клетку, и будет видно, что осталось нарисовать.

Запуск: python tools/книга/иконки.py
"""
import io
import os
import sys

from PIL import Image, ImageChops, ImageDraw

КОРЕНЬ = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                      "..", ".."))
sys.path.insert(0, os.path.join(КОРЕНЬ, "tools"))
os.chdir(КОРЕНЬ)

ТЕКСТУРЫ = os.path.join(КОРЕНЬ, "data", "textures")
ЯНДЕКС = r"C:\shared\Yandex.Disk\Forged in Kelium"
ВЫХОД = os.path.join(КОРЕНЬ, "rules", "иконки")

# ---------------------------------------------------------------- источники

# 1. Прямо файлом из набора текстур: ключ иконки → путь внутри data/textures.
ИЗ_ТЕКСТУР = {
    "пехота": "token/infantry_p1.png",
    "техника": "token/vehicle_p1.png",
    "авиация": "token/aircraft_p1.png",
    "вышка": "token/tower_p1.png",
    "цу": "token/command_center_p1.png",
    "казарма": "token/barracks_p1.png",
    "завод": "token/factory_p1.png",
    "авиабаза": "token/airbase_p1.png",
    "добытчик": "token/miner_l1_p1.png",
    "энергостанция": "token/power_plant_l1_p1.png",
    "трофейная-сторона": "token/infantry_trophy1.png",
    "зарождение-малое": "field/spawn_start.png",
    "зарождение-большое": "field/spawn.png",
    "недоступная-местность": "field/hex_forbidden.png",
    "нейтральная-постройка": "field/neutral_big.png",
    "печатный-контейнер": "field/container.png",
    "сектор-неба": "field/hex.png",
    "модуль-красный": "module/mod_red_infantry_vehicle.png",
    "модуль-синий": "module/mod_blue_a1u2_units.png",
    "позолота": "module/mod_red_infantry_vehicle_gold.png",
    "жетон-хранилища": "module/mod_store_energy.png",
    "ячейка-универсальная": "module/mod_store_cells.png",
    "жетон-уничтожения-цу": "module/mod_cu_infantry.png",
}

# 2. Переименование уже вырезанных с карт-памяток врезок (их сделал разбор
#    задней обложки): ключ иконки → имя файла в rules/иконки.
ПЕРЕИМЕНОВАТЬ = {
    "трофей": "куб-трофей",
    "по": "звезда",
    "фишка-первого": "первый-игрок",
    "снаряжение": "д-снаряжение",
    "добыча": "д-добыча",
    "стройка": "д-стройка",
    "смена-энергии": "д-смена-энергии",
    "манёвр": "д-манёвр",
    "бой": "д-бой",
    "рынок": "д-рынок",
    "наука": "д-наука",
    "иконка-трофея": "шестерня",
    "требование": "приказ",
}

# 3. Врезки с карт приказов: ключ → (файл карты, левый, верх, правый, низ).
#    Координаты — в пикселях карты 661×1028.
С_ПРИКАЗОВ = {
    "инфраструктура": ("карты-приказов-new-1.png", 40, 24, 625, 96),
    "разработка": ("карты-приказов-new-2.png", 40, 24, 625, 96),
    "приобретения": ("карты-приказов-new-3.png", 40, 24, 625, 96),
    "наступление": ("карты-приказов-new-4.png", 40, 24, 625, 96),
    "плашка-монета": ("карты-приказов-new-6.png", 300, 520, 470, 600),
}


def прозрачные_поля(im):
    """Обрезать по непрозрачному: иконка не должна нести чужой воздух."""
    im = im.convert("RGBA")
    bb = im.split()[3].point(lambda v: 255 if v > 8 else 0).getbbox()
    return im.crop(bb) if bb else im


def квадрат(im, поле=0.06):
    """Вписать в квадрат с полем — в справочнике иконки стоят в ряд."""
    im = прозрачные_поля(im)
    сторона = round(max(im.width, im.height) * (1 + поле * 2))
    холст = Image.new("RGBA", (сторона, сторона), (0, 0, 0, 0))
    холст.paste(im, ((сторона - im.width) // 2, (сторона - im.height) // 2), im)
    return холст


def скруглить(im, доля=0.10):
    im = im.convert("RGBA")
    r = round(min(im.width, im.height) * доля)
    маска = Image.new("L", im.size, 0)
    ImageDraw.Draw(маска).rounded_rectangle([0, 0, im.width - 1, im.height - 1],
                                            radius=r, fill=255)
    out = im.copy()
    out.putalpha(ImageChops.multiply(im.split()[3], маска))
    return out


def сохранить(ключ, im, сторона=220):
    im = квадрат(im)
    if im.width > сторона:
        im = im.resize((сторона, сторона), Image.LANCZOS)
    im.save(os.path.join(ВЫХОД, ключ + ".png"))
    return ключ


def main():
    os.makedirs(ВЫХОД, exist_ok=True)
    сделано, нет = [], []

    for ключ, путь in ИЗ_ТЕКСТУР.items():
        файл = os.path.join(ТЕКСТУРЫ, путь)
        if os.path.exists(файл):
            сделано.append(сохранить(ключ, Image.open(файл)))
        else:
            нет.append(ключ)

    for ключ, старое in ПЕРЕИМЕНОВАТЬ.items():
        файл = os.path.join(ВЫХОД, старое + ".png")
        if os.path.exists(файл):
            сделано.append(сохранить(ключ, Image.open(файл)))
        else:
            нет.append(ключ)

    приказы = os.path.join(ЯНДЕКС, "Компоненты игрока", "экспорт-карты-приказов")
    for ключ, (файл, x0, y0, x1, y1) in С_ПРИКАЗОВ.items():
        путь = os.path.join(приказы, файл)
        if os.path.exists(путь):
            кусок = скруглить(Image.open(путь).convert("RGBA").crop((x0, y0, x1, y1)),
                              доля=0.16)
            кусок = квадрат(кусок, поле=0.02)
            if кусок.width > 360:
                кусок = кусок.resize((360, 360), Image.LANCZOS)
            кусок.save(os.path.join(ВЫХОД, ключ + ".png"))
            сделано.append(ключ)
        else:
            нет.append(ключ)

    import справочник as данные
    нужно = [к for _, сп in данные.ИКОНКИ for к, _, _ in сп]
    есть = {f[:-4] for f in os.listdir(ВЫХОД) if f.endswith(".png")}
    осталось = [к for к in нужно if к not in есть]
    print("иконок нарезано:", len(set(сделано)))
    print("в наборе:", len([к for к in нужно if к in есть]), "из", len(нужно))
    if осталось:
        print("ещё не из чего вырезать:", ", ".join(осталось))


if __name__ == "__main__":
    main()
