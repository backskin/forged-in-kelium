# -*- coding: utf-8 -*-
"""ЧЕРНОВИК: 20 КАРТ ПРИКАЗОВ ПОД ПЯТЬ ДЕЙСТВИЙ-РАЗВИЛОК (27.09.2026).

Основа — пустые шаблоны дизайнера по цветам («приказы-шаблоны»,
карты-приказов-<цвет>.png, 661×1028): заголовок, строка правила совпадения,
два верхних кольца, полоса нижнего приказа, два нижних кольца, «1●» внизу.
Наполнение: название приказа, иконки действий в кольцах, подписи, спец-плашка
фракции (четыре прежние плашки цвета — на четыре карты, на НАСТУПАТЬ плашки
нет), низ — приказ через одну и его два действия.

Иконки — выгрузка дизайнера «Общие компоненты/экспорт-иконки» (402×402).
У Командования и Развития своих иконок пока нет: в кольце две иконки веток.

    python tools/gen_orders_5.py [папка вывода]
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ДИСК = os.environ.get('KELIUM_DISK', os.path.join(os.path.expanduser('~'), 'Yandex.Disk',
                                                  'Forged in Kelium'))
ШАБЛОНЫ = os.path.join(ДИСК, 'Компоненты игрока', 'приказы-шаблоны')
ИКОНКИ = os.path.join(ДИСК, 'Общие компоненты', 'экспорт-иконки')
ШРИФТЫ = os.path.join(ROOT, 'gui', 'src', 'main', 'resources', 'fonts')
ВЫВОД = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    ROOT, 'design-docs', 'карты', 'приказы 5.0 (черновик 27.09)')

K = 2                                   # рисуем вдвое крупнее, потом уменьшаем
ПОДПИСЬ = (47, 58, 52)                  # тёмный сланец подписей, как на печатных
ЗАГ_ЗАЛИВКА = (246, 243, 238)
ЗАГ_ОБВОДКА = (78, 52, 42)

# номер иконки в выгрузке «все иконки-N.png»
ИК = {'extract': 36, 'power': 34, 'supply': 35, 'move': 38, 'combat': 39,
      'market': 41, 'science': 42, 'spec': 26, 'coin': 1, 'ammo': 3,
      'objective': 17, 'move_arrow': 66, 'unit': 54}
ДЕЙСТВИЯ = {
    'extract': ('добыча', ['extract']),
    'power': ('питание', ['power']),
    'supply': ('снабжение', ['supply']),
    'command': ('командование', ['command']),
    'develop': ('развитие', ['develop']),
}
# приказ: (название, верх, низ-приказ)
ПРИКАЗЫ = [
    ('settle', 'ОСВОИТЬ', ('extract', 'power'), 'advance'),
    ('mobilize', 'МОБИЛИЗОВАТЬ', ('power', 'supply'), 'secure'),
    ('advance', 'НАСТУПАТЬ', ('supply', 'command'), 'research'),
    ('secure', 'КОНТРОЛИРОВАТЬ', ('command', 'develop'), 'settle'),
    ('research', 'ИССЛЕДОВАТЬ', ('develop', 'extract'), 'mobilize'),
]
ИМЯ = {p[0]: p[1] for p in ПРИКАЗЫ}
ВЕРХ = {p[0]: p[2] for p in ПРИКАЗЫ}
# плашки прежних приказов по цветам (orders 4.0.0): place→ОСВОИТЬ,
# acquire→МОБИЛИЗОВАТЬ, control→КОНТРОЛИРОВАТЬ, explore→ИССЛЕДОВАТЬ
ПЛАШКИ = {
    'синий': {'settle': 'coin', 'mobilize': 'ammo', 'secure': 'movement', 'research': 'objective'},
    'красный': {'settle': 'movement', 'mobilize': 'coin', 'secure': 'ammo', 'research': 'objective'},
    'зеленый': {'settle': 'objective', 'mobilize': 'movement', 'secure': 'coin', 'research': 'ammo'},
    'желтый': {'settle': 'ammo', 'mobilize': 'objective', 'secure': 'movement', 'research': 'coin'},
}
ЦВЕТ_ID = {'синий': 'blue', 'красный': 'red', 'зеленый': 'green', 'желтый': 'yellow'}

ВЕРХ_КОЛЬЦА = [(168, 339), (491, 340)]
НИЗ_КОЛЬЦА = [(177, 803), (482, 803)]
R_ВЕРХ, R_НИЗ = 78, 61

_кэш = {}


# новые иконки-черновики поверх выгрузки (лежат рядом с картами)
ЗАМЕНА = {'supply': os.path.join(ВЫВОД, 'иконки', 'снабжение — ящики (черновик).png'),
          'command': os.path.join(ВЫВОД, 'иконки', 'командование (вырезано).png'),
          'develop': os.path.join(ВЫВОД, 'иконки', 'развитие (вырезано).png')}


def икона(имя):
    if имя not in _кэш:
        путь = ЗАМЕНА.get(имя)
        if not путь or not os.path.exists(путь):
            путь = os.path.join(ИКОНКИ, 'все иконки-%d.png' % ИК[имя])
        im = Image.open(путь).convert('RGBA')
        _кэш[имя] = im.crop(im.getbbox())
    return _кэш[имя]


def шрифт(имя, размер):
    return ImageFont.truetype(os.path.join(ШРИФТЫ, имя), размер)


def вписать(im, w, h):
    k = min(w / im.width, h / im.height)
    return im.resize((max(1, round(im.width * k)), max(1, round(im.height * k))), Image.LANCZOS)


def вставить(холст, im, cx, cy):
    холст.alpha_composite(im, (round(cx - im.width / 2), round(cy - im.height / 2)))


def кольцо(холст, действие, cx, cy, r):
    """Иконка действия в кольце: одна — крупно, две ветки — по диагонали."""
    ветки = ДЕЙСТВИЯ[действие][1]
    cx, cy, r = cx * K, cy * K, r * K
    if len(ветки) == 1:
        вставить(холст, вписать(икона(ветки[0]), r * 2.05, r * 2.05), cx, cy)
    else:
        a = вписать(икона(ветки[0]), r * 1.25, r * 1.25)
        b = вписать(икона(ветки[1]), r * 1.25, r * 1.25)
        вставить(холст, a, cx - r * 0.42, cy - r * 0.40)
        вставить(холст, b, cx + r * 0.42, cy + r * 0.38)


def подпись(d, текст, cx, y, макс_ш, размер):
    р = размер * K
    while True:
        f = шрифт('TekturNarrow-Regular.ttf', р)
        ш = d.textlength(текст, font=f)
        if ш <= макс_ш * K or р < 20:
            break
        р -= 2
    d.text((cx * K, y * K), текст, font=f, fill=ПОДПИСЬ, anchor='mm')


def заголовок(d, текст, cx, cy, макс_ш, размер, обводка=False):
    """Название приказа, Tektur Narrow Bold: сверху — светлое без обводки,
    внизу — светлое с толстой коричневой обводкой (как на печатных картах)."""
    р = размер * K
    while True:
        f = шрифт('TekturNarrow-Bold.ttf', р)
        if d.textlength(текст, font=f) <= макс_ш * K or р < 24:
            break
        р -= 2
    if обводка:
        # по буквам, с шагом на 15% шире (просьба дизайнера 27.09): обводка
        # не должна заливать просветы букв («В») и склеивать соседние буквы
        шаг = 1.15
        ширины = [d.textlength(ч, font=f) * шаг for ч in текст]
        x = cx * K - sum(ширины) / 2
        for ч, ш in zip(текст, ширины):
            d.text((x + ш / 2, cy * K), ч, font=f, fill=(244, 243, 241), anchor='mm',
                   stroke_width=max(2, р // 14), stroke_fill=ЗАГ_ОБВОДКА)
            x += ш
    else:
        d.text((cx * K, cy * K), текст, font=f, fill=ЗАГ_ЗАЛИВКА, anchor='mm')


def плашка(холст, вид, cx, cy):
    """Спец-плашка: ▶ [−монета] : результат — на светлой полупрозрачной подложке."""
    h = 70 * K
    части = [('spec', 1.0)]
    if вид != 'coin':
        части.append(('coin_minus', 0.78))
    части.append(('colon', 0.4))
    if вид == 'coin':
        части.append(('coins2', 1.35))
    elif вид == 'ammo':
        части.append(('ammo', 1.0))
    elif вид == 'objective':
        части.append(('objective', 0.95))
    else:
        части.append(('movement', 2.1))
    ширины = [h * k for _, k in части]
    зазор = 10 * K
    всего = sum(ширины) + зазор * (len(части) - 1)
    x = cx * K - всего / 2
    подл = Image.new('RGBA', холст.size, (0, 0, 0, 0))
    ImageDraw.Draw(подл).rounded_rectangle(
        (x - 24 * K, cy * K - h / 2 - 6 * K, x + всего + 24 * K, cy * K + h / 2 + 6 * K),
        radius=h / 2, fill=(255, 255, 255, 120))
    холст.alpha_composite(подл)
    d = ImageDraw.Draw(холст)
    for (имя, _), ш in zip(части, ширины):
        mx = x + ш / 2
        if имя == 'spec':
            вставить(холст, вписать(икона('spec'), h, h), mx, cy * K)
        elif имя == 'coin_minus':
            c = вписать(икона('coin'), h * 0.78, h * 0.78)
            вставить(холст, c, mx, cy * K)
            d.rounded_rectangle((mx - c.width * 0.62, cy * K - c.height * 0.46,
                                 mx - c.width * 0.05, cy * K - c.height * 0.30),
                                radius=3 * K, fill=(214, 40, 40), outline=(60, 20, 20), width=K)
        elif имя == 'colon':
            for dy in (-0.16, 0.16):
                d.rounded_rectangle((mx - 5 * K, cy * K + h * dy - 5 * K,
                                     mx + 5 * K, cy * K + h * dy + 5 * K),
                                    radius=2 * K, fill=(140, 72, 72))
        elif имя == 'coins2':
            c = вписать(икона('coin'), h * 0.95, h * 0.95)
            вставить(холст, c, mx - h * 0.2, cy * K)
            вставить(холст, c, mx + h * 0.2, cy * K)
        elif имя in ('ammo', 'objective'):
            вставить(холст, вписать(икона(имя), h * 1.0, h * 1.0), mx, cy * K)
        else:  # манёвр одним жетоном: стрелка, «1», жетон войска
            вставить(холст, вписать(икона('move_arrow'), h * 0.9, h * 0.9), x + h * 0.45, cy * K)
            d.text((x + h * 1.12, cy * K), '1', font=шрифт('Tektur-Bold.ttf', int(h * 0.62)),
                   fill=(70, 70, 70), anchor='mm')
            вставить(холст, вписать(икона('unit'), h * 0.8, h * 0.8), x + h * 1.65, cy * K)
        x += ш + зазор


def карта(цвет, pid):
    осн = Image.open(os.path.join(ШАБЛОНЫ, 'карты-приказов-%s.png' % цвет)).convert('RGBA')
    W, H = осн.size
    холст = осн.resize((W * K, H * K), Image.LANCZOS)
    d = ImageDraw.Draw(холст)
    заголовок(d, ИМЯ[pid], 340, 68, 520, 52)
    верх = ВЕРХ[pid]
    for (cx, cy), дей in zip(ВЕРХ_КОЛЬЦА, верх):
        кольцо(холст, дей, cx, cy, R_ВЕРХ)
    d = ImageDraw.Draw(холст)
    for (cx, cy), дей in zip(ВЕРХ_КОЛЬЦА, верх):
        подпись(d, ДЕЙСТВИЯ[дей][0], cx, cy + 112, 300, 46)
    вид = ПЛАШКИ[цвет].get(pid)
    if вид:
        плашка(холст, вид, 330, 556)
    низ = НИЗ_ЗА[pid]
    d = ImageDraw.Draw(холст)
    заголовок(d, ИМЯ[низ], 400, 680, 430, 50, обводка=True)
    for (cx, cy), дей in zip(НИЗ_КОЛЬЦА, ВЕРХ[низ]):
        кольцо(холст, дей, cx, cy, R_НИЗ)
    d = ImageDraw.Draw(холст)
    for (cx, cy), дей in zip(НИЗ_КОЛЬЦА, ВЕРХ[низ]):
        подпись(d, ДЕЙСТВИЯ[дей][0], cx, cy + 90, 290, 44)
    return холст.resize((W, H), Image.LANCZOS).convert('RGB')


НИЗ_ЗА = {p[0]: p[3] for p in ПРИКАЗЫ}


def main():
    os.makedirs(ВЫВОД, exist_ok=True)
    листы = []
    for цвет in ('красный', 'синий', 'зеленый', 'желтый'):
        for pid, *_ in ПРИКАЗЫ:
            im = карта(цвет, pid)
            путь = os.path.join(ВЫВОД, '%s_%s.png' % (ЦВЕТ_ID[цвет], pid))
            im.save(путь)
            листы.append(im)
    # лист всех 20 карт: ряд — цвет
    w, h = 331, 514
    лист = Image.new('RGB', (w * 5, h * 4), 'white')
    for i, im in enumerate(листы):
        лист.paste(im.resize((w, h), Image.LANCZOS), ((i % 5) * w, (i // 5) * h))
    лист.save(os.path.join(ВЫВОД, '_лист — все 20 карт.png'))
    print('готово:', ВЫВОД)


if __name__ == '__main__':
    main()
