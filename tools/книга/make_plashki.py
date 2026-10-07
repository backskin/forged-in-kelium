# -*- coding: utf-8 -*-
"""Спец-плашки карт приказов для полосы 21 — нарисованы с нуля (дизайнер
05.10.2026: вырезы с карт выходили с кривым обрезом).

Плашка: светлая скруглённая подложка с тонкой рамкой, как на карте; внутри
знак спец-действия, двоеточие, цена (монета) и то, что вы получаете.
Значки — из rules/иконки-экспорт. Пишутся в rules/иконки/плашка-*.png,
откуда их берёт разметка «:плитки:» главы 5.
"""
import os

from PIL import Image, ImageDraw, ImageFont

D = os.path.dirname(os.path.abspath(__file__))
КОРЕНЬ = os.path.dirname(os.path.dirname(D))
ИК = os.path.join(КОРЕНЬ, "rules", "иконки-экспорт")
ВЫХОД = os.path.join(КОРЕНЬ, "rules", "иконки")
шр = ImageFont.truetype(os.path.expanduser("~/.fonts/TekturNarrow-Bold.ttf"), 120)

Н = 150          # высота значка
ПОЛЕ = 34        # поля подложки
ЗАЗОР = 14


def зн(имя, h=Н):
    im = Image.open(os.path.join(ИК, имя + ".png")).convert("RGBA")
    bb = im.getbbox()
    im = im.crop(bb) if bb else im
    return im.resize((round(im.width * h / im.height), h), Image.LANCZOS)


def плашка(файл, левое, правое):
    """левое — цена (до двоеточия, после спец-действия), правое — получаете."""
    куски = [зн("спец-действие")] + [зн(x) for x in левое] + [":"] + [зн(x) for x in правое]
    мерка = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
    ширины = [int(мерка.textlength(":", font=шр)) if k == ":" else k.width for k in куски]
    W = sum(ширины) + ЗАЗОР * (len(куски) - 1) + ПОЛЕ * 2
    H = Н + ПОЛЕ * 2
    холст = Image.new("RGBA", (W + 12, H + 12), (0, 0, 0, 0))
    d = ImageDraw.Draw(холст)
    # мягкая тень, подложка, рамка
    d.rounded_rectangle((8, 10, W + 8, H + 10), radius=H // 2 - 6, fill=(40, 60, 80, 60))
    d.rounded_rectangle((2, 2, W, H), radius=H // 2 - 6, fill=(232, 241, 248, 255),
                        outline=(120, 150, 175, 255), width=7)
    x = 2 + ПОЛЕ
    for k, w in zip(куски, ширины):
        if k == ":":
            d.text((x + w / 2, 2 + H / 2), ":", font=шр, fill=(40, 50, 60, 255), anchor="mm")
        else:
            холст.alpha_composite(k, (x, 2 + (H - k.height) // 2))
        x += w + ЗАЗОР
    холст.save(os.path.join(ВЫХОД, файл + ".png"))
    print("ok", файл, холст.size)


плашка("плашка-монеты", [], ["монета", "монета"])
плашка("плашка-боеприпас", ["монета"], ["боеприпас"])
плашка("плашка-задание", ["монета"], ["карта-задания"])
плашка("плашка-движение", ["монета"], ["манёвр"])
