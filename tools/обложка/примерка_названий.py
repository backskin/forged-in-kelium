# -*- coding: utf-8 -*-
"""ПРИМЕРКА НАЗВАНИЙ НА ОБЛОЖКУ КНИГИ ПРАВИЛ (07.10.2026).

Чистый арт дизайнера («обложка рулбука.png» на Диске) → квадрат 1500 точек,
сверху название, под ним русский перевод, внизу плашка «Правила игры» — как
в make_cover.py. Два вида надписи:
  «шрифт»  — буквы Tektur Black, белые с тёмной обводкой и тенью;
  «рендер» — рендер Leonardo, вырезанный с белого фона (если есть).
Плюс миниатюра 200 точек — как карточка в магазине.

    python tools/обложка/примерка_названий.py "FRACTURES|OF HUMANITY|Разломы человечества" ...
    (без аргументов — список по умолчанию)
→ design-docs/обложка/примерка/<ИМЯ> — <вид>.jpg и лист «_примерка — лист.jpg»
"""
import os
import sys

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
АРТ = os.path.join(os.path.expanduser("~"), "Yandex.Disk", "Forged in Kelium", "Обложка", "обложка рулбука.png")
РЕНДЕРЫ = r"C:\Users\backskin\forged-in-kelium\illustrations\Логотип — рендеры (07.10.2026)"
ВЫВОД = os.path.join(ROOT, "design-docs", "обложка", "примерка")
Ш = r"C:\Windows\Fonts"
S = 1500


def шрифт(имя, кегль):
    return ImageFont.truetype(os.path.join(Ш, имя), кегль)


def арт():
    a = Image.open(АРТ).convert("RGBA")
    s = min(a.size)
    x = (a.width - s) // 2
    return a.crop((x, 0, x + s, s)).resize((S, S), Image.LANCZOS)


def строка(текст, f, разрядка=0.04):
    d = ImageDraw.Draw(Image.new("L", (1, 1)))
    ширины = [d.textlength(б, font=f) for б in текст]
    к = f.size
    w = round(sum(ширины) + к * разрядка * (len(текст) - 1))
    asc, desc = f.getmetrics()
    сл = Image.new("L", (w + 8, asc + desc), 0)
    dd = ImageDraw.Draw(сл)
    x = 0
    for б, шб in zip(текст, ширины):
        dd.text((x, 0), б, font=f, fill=255)
        x += шб + к * разрядка
    return сл.crop(сл.getbbox())


def надпись_шрифтом(ряды, ширина):
    """Белые буквы Tektur Black, тёмная обводка и мягкая тень; ряды одного кегля."""
    кегль = 200
    for _ in range(20):
        маски = [строка(р, шрифт("Tektur-Black.ttf", кегль)) for р in ряды]
        к = min(ширина / max(m.width for m in маски), (S * 0.13) / max(m.height for m in маски))
        if abs(к - 1) < 0.01:
            break
        кегль = max(10, round(кегль * к))
    зазор = round(маски[0].height * 0.22)
    W = max(m.width for m in маски) + 60
    H = sum(m.height for m in маски) + зазор * (len(маски) - 1) + 60
    маска = Image.new("L", (W, H), 0)
    y = 30
    for m in маски:
        маска.paste(m, ((W - m.width) // 2, y))
        y += m.height + зазор
    обвод = маска.filter(ImageFilter.MaxFilter(15))
    тень = обвод.filter(ImageFilter.GaussianBlur(14))
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    out.paste(Image.new("RGBA", (W - 8, H - 10), (0, 0, 0, 170)), (8, 10), тень.crop((0, 0, W - 8, H - 10)))
    out.paste(Image.new("RGBA", (W, H), (22, 26, 30, 255)), (0, 0), обвод)
    out.paste(Image.new("RGBA", (W, H), (240, 240, 232, 255)), (0, 0), маска)
    return out


def вырезать(путь):
    """Рендер на белом → прозрачный фон: белое, связанное с краем кадра, уходит."""
    im = np.asarray(Image.open(путь).convert("RGB"))
    светлое = (im.min(axis=2) > 232).astype(np.uint8)
    h, w = светлое.shape
    фон = np.zeros((h + 2, w + 2), np.uint8)
    поле = светлое.copy()
    for x, y in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1), (w // 2, 0), (w // 2, h - 1), (0, h // 2), (w - 1, h // 2)]:
        if поле[y, x]:
            cv2.floodFill(поле, фон, (x, y), 2)
    альфа = np.where(поле == 2, 0, 255).astype(np.uint8)
    альфа = cv2.GaussianBlur(cv2.erode(альфа, np.ones((3, 3), np.uint8)), (5, 5), 0)
    rgba = np.dstack([im, альфа])
    out = Image.fromarray(rgba, "RGBA")
    return out.crop(out.getbbox())


def надпись_рендером(имя, ширина):
    путь = os.path.join(РЕНДЕРЫ, "рендер — %s, леонардо-1.jpg" % имя)
    if not os.path.exists(путь):
        return None
    р = вырезать(путь)
    к = min(ширина / р.width, (S * 0.30) / р.height)
    р = р.resize((round(р.width * к), round(р.height * к)), Image.LANCZOS)
    тень = р.getchannel("A").filter(ImageFilter.GaussianBlur(16))
    out = Image.new("RGBA", (р.width + 40, р.height + 40), (0, 0, 0, 0))
    out.paste(Image.new("RGBA", р.size, (0, 0, 0, 150)), (14, 18), тень)
    out.alpha_composite(р, (0, 0))
    return out


def текст_с_обводкой(d, xy, текст, f, цвет, обвод, толщина):
    d.text(xy, текст, font=f, fill=цвет, anchor="mm", stroke_width=толщина, stroke_fill=обвод)


def обложка(надпись, перевод):
    im = арт()
    y = 60
    im.alpha_composite(надпись, ((S - надпись.width) // 2, y))
    d = ImageDraw.Draw(im)
    if перевод:
        f = шрифт("TekturNarrow-Bold.ttf", 66)
        текст_с_обводкой(d, (S // 2, y + надпись.height + 40), перевод.upper(), f, (236, 255, 170), (22, 40, 18), 7)
    y0, y1 = round(S * 0.80), round(S * 0.92)
    слой = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(слой).rectangle((0, y0, S, y1), fill=(160, 200, 120, 150))
    im.alpha_composite(слой)
    текст_с_обводкой(ImageDraw.Draw(im), (S // 2, (y0 + y1) // 2), "Правила игры", шрифт("Tektur-Bold.ttf", 110),
                     (236, 255, 170), (22, 40, 18), 7)
    return im.convert("RGB")


ПО_УМОЛЧАНИЮ = [
    "FRACTURES|OF HUMANITY|Разломы человечества",
    "HORRIDA|BELLA|Войны, ужасные войны",
    "IUS SANGUINIS|Право крови",
]


def разбить(имя):
    """Длинное имя — на две строки, ровнее по длине; служебное слово не висит в конце."""
    слова = имя.upper().split()
    if len(слова) < 2 or len(имя) <= 10:
        return [имя.upper()]
    служебные = {"THE", "OF", "AND", "ET", "TO", "AD", "IN", "&", "EX", "ON", "THAN"}
    варианты = [(" ".join(слова[:i]), " ".join(слова[i:]), слова[i - 1] in служебные)
                for i in range(1, len(слова))]
    лучший = min(варианты, key=lambda п: (п[2], max(len(п[0]), len(п[1]))))
    return [лучший[0], лучший[1]]


def main(аргументы):
    os.makedirs(ВЫВОД, exist_ok=True)
    готовые = []
    for арг in аргументы or ПО_УМОЛЧАНИЮ:
        *ряды, перевод = арг.split("|")
        if len(ряды) == 1:
            ряды = разбить(ряды[0])
        имя = " ".join(ряды)
        for вид, н in (("шрифт", надпись_шрифтом(ряды, S * 0.78)), ("рендер", надпись_рендером(имя, S * 0.86))):
            if н is None:
                continue
            ок = обложка(н, перевод)
            ок.save(os.path.join(ВЫВОД, "%s — %s.jpg" % (имя, вид)), quality=88)
            готовые.append((имя, вид, ок))
    # лист: обложки по 4 в ряд, под каждой — миниатюра 120 точек
    к = 360
    ряд = 4
    строк = (len(готовые) + ряд - 1) // ряд
    лист = Image.new("RGB", (ряд * (к + 20) + 20, строк * (к + 170) + 20), (36, 36, 36))
    d = ImageDraw.Draw(лист)
    f = шрифт("TekturNarrow-Bold.ttf", 22)
    for i, (имя, вид, ок) in enumerate(готовые):
        x, y = 20 + (i % ряд) * (к + 20), 20 + (i // ряд) * (к + 170)
        лист.paste(ок.resize((к, к), Image.LANCZOS), (x, y))
        лист.paste(ок.resize((120, 120), Image.LANCZOS), (x, y + к + 10))
        d.text((x + 135, y + к + 20), имя, font=f, fill=(230, 230, 230))
        d.text((x + 135, y + к + 50), вид, font=f, fill=(160, 160, 160))
    лист.save(os.path.join(ВЫВОД, "_примерка — лист.jpg"), quality=88)
    print("ok", len(готовые), ВЫВОД)


if __name__ == "__main__":
    main(sys.argv[1:])
