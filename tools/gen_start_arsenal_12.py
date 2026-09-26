# -*- coding: utf-8 -*-
"""ЧЕРНОВИК: 12 КАРТ НАЧАЛЬНОГО АРСЕНАЛА НА ШАБЛОНЕ ДИЗАЙНЕРА (26.09.2026).

Идея дизайнера: начальная карта арсенала даёт весь старт — одно здание
(казарма, добытчик 1-го уровня или энергостанция 1-го уровня), монеты,
боеприпас или келемий — и свою способность. Карт 12, по четыре на здание.

Основа — пустые шаблоны дизайнера из экспорта (27.09): «арсенал-начальный-8»
со спец-действием (▶) и «-9» с постоянным эффектом (∞); молния вверху — то,
что игрок получает сразу. Цвета текста сняты с печатных карт.

    python tools/gen_start_arsenal_12.py [папка вывода]
"""
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ЭКСПОРТ = os.path.join(os.path.expanduser('~'), 'Yandex.Disk', 'Forged in Kelium',
                       'Общие компоненты', 'экспорт-арсенал-начальный')
ШРИФТЫ = os.path.join(ROOT, 'gui', 'src', 'main', 'resources', 'fonts')
ИКОНКИ = os.path.join(ROOT, 'data', 'textures', 'icons')
ВЫВОД = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    ROOT, 'design-docs', 'карты', 'начальный-арсенал-12 (черновик 26.09)')

K = 2                                  # рисуем вдвое крупнее печатного экспорта
ИМЯ = (62, 121, 50)
ТЕКСТ = (50, 100, 43)
ВЕРХ = (135, 75, 74)
НОМЕР = (76, 128, 54)
БЕЛЫЙ = (255, 255, 255)

# здание, ресурсы (порядок показа), вид способности, цена спец-действия, текст
КАРТЫ = [
    ('Мародёрка', 'Казарма', [('coin', 1), ('ammo', 1)], 'spec', 1,
     'Когда твои войска на гексе со зданием противника — забери у него '
     '1 боеприпас или 1 келемий.'),
    ('Премия за голову', 'Казарма', [('coin', 2)], 'post', 0,
     'В конце **Боя**, если уничтожил хотя бы один жетон, — получи 1 монету.'),
    ('Учебная рота', 'Казарма', [('coin', 1), ('kelium', 1)], 'post', 0,
     'Твоя казарма в **Снабжении** даёт на 1 боеприпас больше.'),
    ('Форсированный марш', 'Казарма', [('coin', 2), ('ammo', 1)], 'post', 0,
     'Твоя пехота перемещается на 1 гекс дальше.'),
    ('Аварийные щиты', 'Добытчик 1 ур.', [('coin', 2), ('ammo', 1)], 'post', 0,
     'Твои добытчики и энергостанции с прочностью 1 имеют прочность 2. '
     'Получив урон, такое здание сразу после боя возвращается тебе в запас.'),
    ('Сдача тары', 'Добытчик 1 ур.', [('coin', 1), ('kelium', 1)], 'post', 0,
     'Вскрывая контейнер, получи ещё 1 монету.'),
    ('Разведка недр', 'Добытчик 1 ур.', [('coin', 3)], 'spec', 1,
     'Твой добытчик, стоящий у тайла зарождения, добывает 1 келемий.'),
    ('Склад у дороги', 'Добытчик 1 ур.', [('coin', 1), ('ammo', 1)], 'post', 0,
     'На этой карте одна ячейка хранилища: кубик на ней лежит как в хранилище.'),
    ('Полевой генератор', 'Энергостанция 1 ур.', [('coin', 1), ('kelium', 1)], 'post', 0,
     'Считай эту карту отдельным источником энергии с одним кубиком. '
     'В фазу **Обновление** заплати 1 монету, иначе удали карту из игры.'),
    ('Перегрузка', 'Энергостанция 1 ур.', [('coin', 2)], 'spec', 0,
     'Переложи 1 кубик энергии с любого своего источника в любую свою '
     'ячейку энергии.'),
    ('Кабельная команда', 'Энергостанция 1 ур.', [('coin', 2), ('kelium', 1)], 'post', 0,
     'Энергостанцию можно ставить на любой гекс, где стоит твой жетон войск.'),
    ('Переформирование', 'Энергостанция 1 ур.', [('coin', 3)], 'spec', 2,
     'Замени на поле свой жетон наземного войска на другой наземный жетон '
     'из запаса.'),
]


def шрифт(имя, размер):
    return ImageFont.truetype(os.path.join(ШРИФТЫ, имя), int(размер * K))


def икон(имя, сторона):
    return Image.open(os.path.join(ИКОНКИ, имя + '.png')).convert('RGBA').resize(
        (int(сторона * K), int(сторона * K)), Image.LANCZOS)


def вырезка(номер, box):
    im = Image.open(os.path.join(ЭКСПОРТ, 'арсенал-начальный-%d.png' % номер)).convert('RGBA')
    im = im.resize((im.width * K, im.height * K), Image.LANCZOS)
    return im.crop(tuple(v * K for v in box))


def слова(текст):
    """Разбить на слова с пометкой «жирное» (**…**)."""
    out = []
    for часть in re.split(r'(\*\*[^*]+\*\*)', текст):
        жир = часть.startswith('**')
        for w in часть.strip('*').split():
            out.append((w, жир))
    return out


def строки(d, текст, обычный, жирный, ширина):
    res, cur = [], []
    for w, жир in слова(текст):
        проба = cur + [(w, жир)]
        if cur and длина(d, проба, обычный, жирный) > ширина:
            res.append(cur)
            cur = [(w, жир)]
        else:
            cur = проба
    if cur:
        res.append(cur)
    return res


def длина(d, строка, обычный, жирный):
    пробел = d.textlength(' ', font=обычный)
    return sum(d.textlength(w, font=жирный if ж else обычный) for w, ж in строка) \
        + пробел * (len(строка) - 1)


def карта(номер, имя, здание, ресурсы, вид, цена, текст, шаблоны):
    im = шаблоны[вид].copy()
    d = ImageDraw.Draw(im)
    # --- верх: что игрок получает при установке ---
    f_верх = шрифт('Tektur-Bold.ttf', 34)
    x, y = 128 * K, 48 * K
    while True:
        ширина = d.textlength(здание, font=f_верх) + len(ресурсы) * 78 * K
        if ширина <= 640 * K or f_верх.size <= 20 * K:
            break
        f_верх = ImageFont.truetype(os.path.join(ШРИФТЫ, 'Tektur-Bold.ttf'), f_верх.size - 2)
    d.text((x, y), здание, font=f_верх, fill=ВЕРХ, stroke_width=3 * K, stroke_fill=БЕЛЫЙ)
    x += int(d.textlength(здание, font=f_верх)) + 16 * K
    for рес, n in ресурсы:
        if n > 1:
            d.text((x, y), str(n), font=f_верх, fill=ВЕРХ, stroke_width=3 * K,
                   stroke_fill=БЕЛЫЙ)
            x += int(d.textlength(str(n), font=f_верх)) + 2 * K
        ic = икон(рес, 50)
        im.alpha_composite(ic, (x, y - 4 * K))
        x += ic.width + 12 * K
    # --- название ---
    f_имя = шрифт('Tektur-Bold.ttf', 40)
    while d.textlength(имя, font=f_имя) > 470 * K:
        f_имя = ImageFont.truetype(os.path.join(ШРИФТЫ, 'Tektur-Bold.ttf'), f_имя.size - 2)
    d.text((745 * K, 175 * K), имя, font=f_имя, fill=ИМЯ, anchor='rm',
           stroke_width=3 * K, stroke_fill=БЕЛЫЙ)
    # --- слева: значок вида уже на шаблоне (∞ или ▶); цена спец-действия под ▶ ---
    if вид == 'spec' and цена:
        f_цена = шрифт('Tektur-Bold.ttf', 32)
        d.text((22 * K, 345 * K), '−%d' % цена, font=f_цена, fill=(40, 40, 40),
               anchor='lm', stroke_width=2 * K, stroke_fill=БЕЛЫЙ)
        im.alpha_composite(икон('coin', 38), (58 * K, 326 * K))
    # --- картинка способности там, где она видна на столе ---
    if номер == 9:
        # рисунок кубика энергии с печатной карты — с мягким краем, чтобы
        # вырезка не оставила шва на свечении
        кусок = вырезка(2, (115, 175, 290, 375))
        маска = Image.new('L', кусок.size, 0)
        ImageDraw.Draw(маска).ellipse((8 * K, 8 * K, кусок.width - 8 * K,
                                       кусок.height - 8 * K), fill=255)
        from PIL import ImageFilter
        маска = маска.filter(ImageFilter.GaussianBlur(10 * K))
        кусок.putalpha(Image.composite(кусок.getchannel('A'), Image.new('L', кусок.size, 0), маска))
        im.alpha_composite(кусок, (115 * K, 175 * K))
    if номер == 8:
        im.alpha_composite(икон('cell', 120), (140 * K, 230 * K))
    # --- текст способности, по правому краю, как на печати ---
    обычный = шрифт('TekturNarrow-Regular.ttf', 33)
    жирный = шрифт('TekturNarrow-Bold.ttf', 33)
    левый = 290 if номер in (8, 9) else 160
    ряд = строки(d, текст, обычный, жирный, (745 - левый) * K)
    шаг = 47 * K
    yy = 237 * K
    while len(ряд) * шаг > (470 - 237) * K and обычный.size > 22 * K:
        обычный = ImageFont.truetype(os.path.join(ШРИФТЫ, 'TekturNarrow-Regular.ttf'),
                                     обычный.size - 2)
        жирный = ImageFont.truetype(os.path.join(ШРИФТЫ, 'TekturNarrow-Bold.ttf'),
                                    жирный.size - 2)
        шаг = int(обычный.size * 1.4)
        ряд = строки(d, текст, обычный, жирный, (745 - левый) * K)
    for строка in ряд:
        xx = 745 * K - длина(d, строка, обычный, жирный)
        for w, ж in строка:
            f = жирный if ж else обычный
            d.text((xx, yy), w, font=f, fill=ТЕКСТ, anchor='lm',
                   stroke_width=2 * K, stroke_fill=БЕЛЫЙ)
            xx += d.textlength(w + ' ', font=f)
        yy += шаг
    # --- номер ---
    d.text((775 * K, 485 * K), str(номер), font=шрифт('Tektur-Bold.ttf', 28),
           fill=НОМЕР, anchor='rm')
    return im


def main():
    os.makedirs(ВЫВОД, exist_ok=True)
    # шаблоны дизайнера 27.09: -8 — со спец-действием (▶), -9 — с постоянным эффектом (∞)
    шаблоны = {}
    for вид, n in (('spec', 8), ('post', 9)):
        t = Image.open(os.path.join(ЭКСПОРТ, 'арсенал-начальный-%d.png' % n)).convert('RGBA')
        шаблоны[вид] = t.resize((t.width * K, t.height * K), Image.LANCZOS)
    for i, (имя, здание, рес, вид, цена, текст) in enumerate(КАРТЫ, start=1):
        im = карта(i, имя, здание, рес, вид, цена, текст, шаблоны)
        путь = os.path.join(ВЫВОД, 'начальный-арсенал-%02d — %s.jpg' % (i, имя))
        im.convert('RGB').save(путь, quality=92)
    print('готово: %d карт в %s' % (len(КАРТЫ), ВЫВОД))


main()
