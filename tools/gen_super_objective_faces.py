"""ЛИЦА СУПЕР-ЗАДАНИЙ для цифровой версии — пока печатных лиц нет.

Дизайнер нарисовал только рубашку супер-заданий («экспорт-рубашки/задания
супер.png»), лица карт набора super_objectives.8.0.0 не сверстаны. Без лица
программа рисовала на месте карты жёлтую плашку с названием — это было
замечено 25.09.2026 как уродство.

Лицо собирается из той же рубашки: золотая рамка и арт остаются, нижняя
половина уходит под тёмную плашку, на ней — название и обе категории счёта
словами из свода (label в data/cards/super_objectives.<версия>.yaml), номер
карты в углу. Шрифт — Tektur, как у интерфейса.

Когда дизайнер сверстает настоящие лица — они кладутся в
data/textures/card/objective_super/<id>.png поверх этих, скрипт больше не нужен.

    python tools/gen_super_objective_faces.py [версия]   # по умолчанию 8.0.0
"""
import os
import sys

import yaml
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ЭКСПОРТ = os.environ.get('KELIUM_EXPORT') or r'C:\shared\Yandex.Disk\Forged in Kelium'
РУБАШКА = os.path.join(ЭКСПОРТ, 'Общие компоненты', 'экспорт-рубашки', 'задания супер.png')
ШРИФТЫ = os.path.join(ROOT, 'gui', 'src', 'main', 'resources', 'fonts')
ШИРИНА = 600

ЗОЛОТО = (242, 201, 76)
ТЕКСТ = (246, 240, 222)
ТЕКСТ2 = (214, 204, 170)


def шрифт(имя, размер):
    return ImageFont.truetype(os.path.join(ШРИФТЫ, имя), размер)


def перенос(d, текст, f, ширина):
    строки, строка = [], ''
    for слово in текст.split():
        проба = (строка + ' ' + слово).strip()
        if d.textlength(проба, font=f) <= ширина or not строка:
            строка = проба
        else:
            строки.append(строка)
            строка = слово
    if строка:
        строки.append(строка)
    return строки


def лицо(база, номер, имя, категории):
    im = база.copy()
    w, h = im.size
    # рисуем на прозрачном слое и кладём его поверх: так полупрозрачные
    # заливки смешиваются с артом, а не затирают его
    слой = Image.new('RGBA', im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(слой)
    # тёмная плашка на нижние 58% внутри рамки
    x0, x1 = int(w * 0.075), int(w * 0.925)
    y0, y1 = int(h * 0.34), int(h * 0.955)
    d.rounded_rectangle((x0, y0, x1, y1), radius=int(w * 0.04),
                        fill=(22, 26, 20, 250), outline=ЗОЛОТО + (255,), width=3)
    # плашка ложится сразу; остальное — следующим слоем поверх неё
    im = Image.alpha_composite(im, слой)
    слой = Image.new('RGBA', im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(слой)
    # шапка
    f_cap = шрифт('TekturNarrow-Bold.ttf', int(w * 0.046))
    cap = 'СУПЕР-ЗАДАНИЕ'
    d.text(((w - d.textlength(cap, font=f_cap)) / 2, y0 + h * 0.018), cap,
           font=f_cap, fill=ЗОЛОТО)
    # название
    f_name = шрифт('Tektur-Bold.ttf', int(w * 0.082))
    y = y0 + h * 0.07
    for s in перенос(d, имя, f_name, x1 - x0 - w * 0.08):
        d.text(((w - d.textlength(s, font=f_name)) / 2, y), s, font=f_name, fill=ТЕКСТ)
        y += f_name.size * 1.12
    y += h * 0.012
    d.line((x0 + w * 0.08, y, x1 - w * 0.08, y), fill=ЗОЛОТО + (170,), width=2)
    y += h * 0.022
    # две категории счёта — каждая своей плашкой со звездой
    f_cat = шрифт('TekturNarrow-Medium.ttf', int(w * 0.05))
    для_текста = x1 - x0 - w * 0.2
    блоки = [перенос(d, c, f_cat, для_текста) for c in категории]
    свободно = (y1 - h * 0.05) - y
    высоты = [len(b) * f_cat.size * 1.2 + h * 0.03 for b in блоки]
    зазор = max(h * 0.01, (свободно - sum(высоты)) / (len(блоки) + 1))
    y += зазор
    for b, bh in zip(блоки, высоты):
        bx0, bx1 = x0 + w * 0.04, x1 - w * 0.04
        d.rounded_rectangle((bx0, y, bx1, y + bh), radius=int(w * 0.03),
                            fill=(255, 255, 255, 30), outline=ЗОЛОТО + (120,), width=2)
        # звезда победного очка
        cx, cy, r = bx0 + w * 0.06, y + bh / 2, w * 0.035
        import math
        pts = []
        for k in range(10):
            rr = r if k % 2 == 0 else r * 0.45
            a = -math.pi / 2 + k * math.pi / 5
            pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
        d.polygon(pts, fill=ЗОЛОТО, outline=(120, 90, 10))
        ty = y + (bh - len(b) * f_cat.size * 1.2) / 2
        for s in b:
            d.text((bx0 + w * 0.12, ty), s, font=f_cat, fill=ТЕКСТ)
            ty += f_cat.size * 1.2
        y += bh + зазор
    # номер карты, как на печатных заданиях
    f_no = шрифт('Tektur-Bold.ttf', int(w * 0.05))
    no = '%02d' % номер
    d.text((x1 - w * 0.03 - d.textlength(no, font=f_no), y1 - h * 0.045), no,
           font=f_no, fill=ТЕКСТ2)
    return Image.alpha_composite(im, слой)


def main():
    версия = sys.argv[1] if len(sys.argv) > 1 else '8.0.0'
    данные = yaml.safe_load(open(os.path.join(ROOT, 'data', 'cards',
                                             'super_objectives.%s.yaml' % версия),
                                encoding='utf-8'))
    база = Image.open(РУБАШКА).convert('RGBA')
    куда = os.path.join(ROOT, 'data', 'textures', 'card', 'objective_super')
    os.makedirs(куда, exist_ok=True)
    for i, карта in enumerate(данные['super_objectives'], 1):
        кат = [данные['categories'][c] for c in карта['categories']]
        im = лицо(база, i, карта['name'], кат)
        im = im.resize((ШИРИНА, round(im.height * ШИРИНА / im.width)), Image.LANCZOS)
        im.save(os.path.join(куда, карта['id'] + '.png'), optimize=True)
    print('супер-заданий:', len(данные['super_objectives']), '->', куда)


if __name__ == '__main__':
    main()
