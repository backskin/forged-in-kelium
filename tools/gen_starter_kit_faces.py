"""ЛИЦА НАЧАЛЬНОГО АРСЕНАЛА 7.2.0 — СТАРТОВЫЙ НАБОР ВМЕСТО УТИЛЯ.

Решение дизайнера 26.09.2026: верх начальной карты арсенала больше не утиль
(«▶ + рваная карта : эффект»), а стартовый набор — что игрок получает сразу
при установке. Разрыва карты нет, справа — иконка «арсенал» (карту ставят).
Пока дизайнер не перерисовал карты, лицо собирается из печатного лица 7.1.0:
верхняя полоса закрашивается и получает новый текст.

    python tools/gen_starter_kit_faces.py
"""
import os

import yaml
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ШРИФТЫ = os.path.join(ROOT, 'gui', 'src', 'main', 'resources', 'fonts')
КАРТЫ = os.path.join(ROOT, 'data', 'textures', 'card', 'arsenal_start')
ИКОНКИ = os.path.join(ROOT, 'data', 'textures', 'icons')

ТЕКСТ = (122, 36, 44)
ОБВОДКА = (255, 246, 240)


def шрифт(имя, размер):
    return ImageFont.truetype(os.path.join(ШРИФТЫ, имя), размер)


def лицо(база, подпись):
    im = база.convert('RGBA')
    w, h = im.size
    полоса = int(h * 0.245)
    слой = Image.new('RGBA', im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(слой)
    # плашка набора: светлая, с тонкой кромкой цвета начального арсенала
    for y in range(полоса):
        t = y / max(1, полоса - 1)
        c = (int(250 - 10 * t), int(238 - 12 * t), int(230 - 12 * t), 255)
        d.line((0, y, w, y), fill=c)
    d.line((0, полоса, w, полоса), fill=(92, 150, 96, 255), width=3)
    im = Image.alpha_composite(im, слой)
    d = ImageDraw.Draw(im)
    # «ПОЛУЧИ ПРИ УСТАНОВКЕ»
    f1 = шрифт('TekturNarrow-Bold.ttf', int(h * 0.058))
    d.text((int(w * 0.04), int(h * 0.025)), 'ПОЛУЧИ ПРИ УСТАНОВКЕ', font=f1,
           fill=(92, 150, 96))
    # сам набор — крупно
    размер = int(h * 0.1)
    f2 = шрифт('Tektur-Bold.ttf', размер)
    ширина = w * 0.74
    while d.textlength(подпись, font=f2) > ширина and размер > 14:
        размер -= 1
        f2 = шрифт('Tektur-Bold.ttf', размер)
    y = int(h * 0.095)
    d.text((int(w * 0.04), y), подпись, font=f2, fill=ТЕКСТ,
           stroke_width=3, stroke_fill=ОБВОДКА)
    # справа — иконка «арсенал»: карту ставят, а не сжигают
    знак = os.path.join(ИКОНКИ, 'arsenal.png')
    if os.path.isfile(знак):
        ic = Image.open(знак).convert('RGBA')
        s = int(полоса * 0.86)
        ic = ic.resize((s, s), Image.LANCZOS)
        im.alpha_composite(ic, (w - s - int(w * 0.03), (полоса - s) // 2))
    return im


def main():
    данные = yaml.safe_load(open(os.path.join(ROOT, 'data', 'cards', 'arsenal.7.2.0.yaml'),
                                encoding='utf-8'))
    ключ = [k for k in данные if isinstance(данные[k], list)][0]
    n = 0
    for карта in данные[ключ]:
        cid = карта['id']
        if not cid.startswith('bs72_'):
            continue
        старое = os.path.join(КАРТЫ, cid.replace('bs72_', 'bs7_') + '.png')
        if not os.path.isfile(старое):
            print('нет лица', старое)
            continue
        im = лицо(Image.open(старое), карта['top']['label'])
        im.save(os.path.join(КАРТЫ, cid + '.png'), optimize=True)
        n += 1
    print('лиц:', n)


if __name__ == '__main__':
    main()
