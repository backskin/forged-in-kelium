# -*- coding: utf-8 -*-
"""LEX TALIONIS — сборка финальных файлов логотипа (12.10.2026).

Берёт рендеры Leonardo (illustrations/Логотип — LEX TALIONIS (рендеры)):
  логотип  — вырезает с белого фона, кладёт под ним русский подзаголовок
             «ЗАКОН ВОЗМЕЗДИЯ» настоящим шрифтом Tektur Narrow Bold (кириллицу
             Leonardo портит) и сохраняет PNG с прозрачным фоном;
  значок   — вырезает LT;
  боковина — обрезает полосу и поворачивает на 90° (оба направления);
  обложка  — примерка на обложку книги правил.

    python tools/логотип/лекс_сборка.py [номер_рендера_логотипа (1|2)]
→ design-docs/логотип-лекс/*.png
"""
import importlib.util
import os
import sys

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
РЕНД = r"C:\Users\backskin\forged-in-kelium\illustrations\Логотип — LEX TALIONIS (рендеры)"
ВЫХОД = os.path.join(ROOT, "design-docs", "логотип-лекс")
spec = importlib.util.spec_from_file_location("п", os.path.join(ROOT, "tools", "обложка", "примерка_названий.py"))
п = importlib.util.module_from_spec(spec)
spec.loader.exec_module(п)

ПОДЗАГОЛОВОК = "ЗАКОН ВОЗМЕЗДИЯ"
Ш = r"C:\Windows\Fonts\TekturNarrow-Bold.ttf"


def обрезать(im):
    return im.crop(im.getbbox())


def дыры(im, порог=240, площадь=250):
    """Белые пятна внутри (просветы букв, пробоины), не связанные с краем, — прозрачные."""
    a = np.asarray(im).copy()
    белое = ((a[..., :3].min(axis=2) > порог) & (a[..., 3] > 0)).astype(np.uint8)
    n, lab, st, _ = cv2.connectedComponentsWithStats(белое, connectivity=4)
    for i in range(1, n):
        if st[i, cv2.CC_STAT_AREA] >= площадь:
            a[lab == i, 3] = 0
    return Image.fromarray(a, "RGBA")


def подзаголовок(ширина):
    """Подзаголовок шириной около ширины логотипа: светлый, тёмная обводка, планки по бокам."""
    кегль = 90
    f = ImageFont.truetype(Ш, кегль)
    m = п.строка(ПОДЗАГОЛОВОК, f, 0.32)
    ц = ширина * 0.56
    к = ц / m.width
    m = m.resize((round(m.width * к), round(m.height * к)), Image.LANCZOS)
    h = m.height
    W = ширина
    out = Image.new("RGBA", (W, h + 70), (0, 0, 0, 0))
    слой = Image.new("L", out.size, 0)
    слой.paste(m, ((W - m.width) // 2, 35))
    d = ImageDraw.Draw(слой)
    y = 35 + h // 2
    пол = round(h * 0.10)
    отст = round(h * 0.9)
    x0, x1 = (W - m.width) // 2 - отст, (W + m.width) // 2 + отст
    d.rectangle([round(W * 0.03), y - пол, x0, y + пол], fill=255)
    d.rectangle([x1, y - пол, round(W * 0.97), y + пол], fill=255)
    обвод = слой.filter(ImageFilter.MaxFilter(9))
    тень = обвод.filter(ImageFilter.GaussianBlur(7))
    out.paste(Image.new("RGBA", out.size, (0, 0, 0, 150)), (4, 6), тень)
    out.paste(Image.new("RGBA", out.size, (22, 26, 30, 255)), (0, 0), обвод)
    out.paste(Image.new("RGBA", out.size, (240, 240, 232, 255)), (0, 0), слой)
    return out


def логотип(номер):
    р = обрезать(дыры(п.вырезать(os.path.join(РЕНД, "ЛЕКС — 2 шестигранник, рендер-%d.jpg" % номер))))
    п_ = подзаголовок(р.width)
    c = Image.new("RGBA", (р.width, р.height + 30 + п_.height), (0, 0, 0, 0))
    c.alpha_composite(р, (0, 0))
    c.alpha_composite(п_, (0, р.height + 30))
    return р, c


def боковина():
    out = {}
    for n in (1, 2):
        р = обрезать(дыры(п.вырезать(os.path.join(РЕНД, "ЛЕКС — боковина, рендер-%d.jpg" % n)), площадь=60))
        out[n] = р
    return out


if __name__ == "__main__":
    os.makedirs(ВЫХОД, exist_ok=True)
    номер = int(sys.argv[1]) if len(sys.argv) > 1 else 1
    р, c = логотип(номер)
    р.save(os.path.join(ВЫХОД, "LEX TALIONIS — логотип (без подзаголовка).png"))
    c.save(os.path.join(ВЫХОД, "LEX TALIONIS — логотип.png"))
    знач = обрезать(дыры(п.вырезать(os.path.join(РЕНД, "ЛЕКС — значок LT, рендер-2.jpg"))))
    знач.save(os.path.join(ВЫХОД, "LEX TALIONIS — значок LT.png"))
    for n, б in боковина().items():
        б.save(os.path.join(ВЫХОД, "LEX TALIONIS — боковина-%d (горизонтально).png" % n))
        б.rotate(-90, expand=True).save(os.path.join(ВЫХОД, "LEX TALIONIS — боковина-%d (сверху вниз).png" % n))
    print("ok", c.size, знач.size)
