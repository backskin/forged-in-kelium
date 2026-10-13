# -*- coding: utf-8 -*-
"""Иконка «при установке» (№27, молния в круге) — перерисовка под карту
арсенала и жирный плюс для «получи» (дизайнер 13.10.2026).

Исходник №27 — тонкая серая молния в контурном круге: на голубом фоне карты
арсенала её не прочесть. Здесь та же форма, но:
- круг-подложка залит приглушённым бирюзовым цветом карты арсенала, светлое
  кольцо и чёрная обводка — как у значков ▶ и ∞;
- молния жирная, белая, с тёмной тенью и обводкой — читается и в строке
  текста.

Плюс — остроугольный, жирный, тёмный с белой обводкой (как «-1», «-2» на
картах), ставится слева от того, что игрок получает.

Запуск: python при_установке.py <папка вывода>
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

K = 4                      # рисуем в 4 раза крупнее и уменьшаем — ровные края
N = 402                    # размер как у файлов экспорта
ЧЕРН = (28, 22, 20, 255)
БИРЮЗА = (64, 128, 144)
БИРЮЗА_ТЁМН = (34, 78, 92)
БИРЮЗА_СВЕТЛ = (118, 172, 186)


def радиальная(size, c_in, c_out, cx, cy, r):
    n = 64
    im = Image.new('RGBA', (n, n))
    px = im.load()
    k = size / n
    for y in range(n):
        for x in range(n):
            t = min(1.0, math.hypot(x * k - cx, y * k - cy) / r)
            px[x, y] = tuple(int(c_in[i] * (1 - t) + c_out[i] * t) for i in range(3)) + (255,)
    return im.resize((size, size), Image.BICUBIC)


def круг(size, cx, cy, r):
    m = Image.new('L', (size, size), 0)
    ImageDraw.Draw(m).ellipse((cx - r, cy - r, cx + r, cy + r), fill=255)
    return m


def молния(cx, cy, s):
    """Молния исходника №27: верхний косой брусок, ступенька, остриё вниз."""
    p = [(-0.12, -0.50), (0.30, -0.50), (0.12, -0.10), (0.36, -0.10),
         (-0.16, 0.56), (-0.02, 0.06), (-0.30, 0.06)]
    return [(cx + x * s, cy + y * s) for x, y in p]


def при_установке():
    W = N * K
    im = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    c = W // 2
    R = int(W * 0.47)
    # тень под кругом
    sh = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse((c - R, c - R + 3 * K, c + R, c + R + 3 * K), fill=(0, 0, 0, 90))
    im.alpha_composite(sh.filter(ImageFilter.GaussianBlur(4 * K)))
    g = ImageDraw.Draw(im)
    g.ellipse((c - R, c - R, c + R, c + R), fill=ЧЕРН)                       # обводка
    g.ellipse((c - R + 5 * K, c - R + 5 * K, c + R - 5 * K, c + R - 5 * K),
              fill=(236, 242, 244, 255))                                      # светлое кольцо
    r2 = R - 22 * K
    g.ellipse((c - r2 - 4 * K, c - r2 - 4 * K, c + r2 + 4 * K, c + r2 + 4 * K), fill=ЧЕРН)
    поле = радиальная(W, БИРЮЗА_СВЕТЛ, БИРЮЗА_ТЁМН, c - r2 * 0.4, c - r2 * 0.5, r2 * 1.6)
    im.paste(поле, (0, 0), круг(W, c, c, r2))
    s = r2 * 1.55
    # тень молнии (вправо-вниз), обводка, белая заливка
    тень = молния(c + 9 * K, c + 11 * K, s)
    g.polygon(тень, fill=(20, 40, 48, 255))
    контур = Image.new('L', (W, W), 0)
    ImageDraw.Draw(контур).polygon(молния(c, c, s), fill=255)
    ImageDraw.Draw(im).polygon(молния(c, c, s), outline=ЧЕРН, width=12 * K)
    im.paste(Image.new('RGBA', (W, W), (255, 255, 255, 255)), (0, 0), контур)
    return im.resize((N, N), Image.LANCZOS)


def плюс():
    W = N * K
    im = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    c = W // 2
    L, T = int(W * 0.40), int(W * 0.13)          # полудлина и полутолщина бруска
    def крест(t, l, цвет):
        d = ImageDraw.Draw(im)
        d.rectangle((c - l, c - t, c + l, c + t), fill=цвет)
        d.rectangle((c - t, c - l, c + t, c + l), fill=цвет)
    крест(T + 11 * K, L + 11 * K, ЧЕРН)
    крест(T + 9 * K, L + 9 * K, (255, 255, 255, 255))
    крест(T, L, (40, 30, 26, 255))
    return im.resize((N, N), Image.LANCZOS)


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else '.'
    os.makedirs(out, exist_ok=True)
    при_установке().save(os.path.join(out, 'при установке.png'))
    плюс().save(os.path.join(out, 'плюс.png'))
    print('готово:', out)


# ---------------------------------------------------------------- варианты 13.10
def круг_подложка():
    """Тот же круг цвета арсенала, что у «при установке», без знака."""
    W = N * K
    im = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    c = W // 2
    R = int(W * 0.47)
    g = ImageDraw.Draw(im)
    g.ellipse((c - R, c - R, c + R, c + R), fill=ЧЕРН)
    g.ellipse((c - R + 5 * K, c - R + 5 * K, c + R - 5 * K, c + R - 5 * K), fill=(236, 242, 244, 255))
    r2 = R - 22 * K
    g.ellipse((c - r2 - 4 * K, c - r2 - 4 * K, c + r2 + 4 * K, c + r2 + 4 * K), fill=ЧЕРН)
    поле = радиальная(W, БИРЮЗА_СВЕТЛ, БИРЮЗА_ТЁМН, c - r2 * 0.4, c - r2 * 0.5, r2 * 1.6)
    im.paste(поле, (0, 0), круг(W, c, c, r2))
    return im, c, r2


def знак(im, многоугольники, сдвиг):
    """Белые фигуры с тёмной тенью вправо-вниз и чёрной обводкой."""
    g = ImageDraw.Draw(im)
    for p in многоугольники:
        g.polygon([(x + сдвиг, y + сдвиг * 1.2) for x, y in p], fill=(20, 40, 48, 255))
    for p in многоугольники:
        g.polygon(p, fill=(255, 255, 255, 255), outline=ЧЕРН, width=3 * K)
    return im


def вариант_молния_зеркало():
    im, c, r = круг_подложка()
    p = молния(c, c, r * 1.55)
    p = [(2 * c - x, y) for x, y in p]                 # отражение по горизонтали
    return знак(im, [p], 9 * K).resize((N, N), Image.LANCZOS)


def вариант_карта_в_ячейку():
    """Карта входит в ячейку: карта со стрелкой вниз и полоса-ячейка."""
    im, c, r = круг_подложка()
    s = r
    карта_ = [(c - 0.34 * s, c - 0.62 * s), (c + 0.34 * s, c - 0.62 * s),
              (c + 0.34 * s, c + 0.05 * s), (c - 0.34 * s, c + 0.05 * s)]
    стрелка = [(c - 0.16 * s, c - 0.42 * s), (c + 0.16 * s, c - 0.42 * s), (c + 0.16 * s, c - 0.12 * s),
               (c + 0.30 * s, c - 0.12 * s), (c, c + 0.22 * s), (c - 0.30 * s, c - 0.12 * s),
               (c - 0.16 * s, c - 0.12 * s)]
    ячейка = [(c - 0.58 * s, c + 0.32 * s), (c + 0.58 * s, c + 0.32 * s),
              (c + 0.58 * s, c + 0.50 * s), (c - 0.58 * s, c + 0.50 * s)]
    знак(im, [карта_, ячейка], 8 * K)
    ImageDraw.Draw(im).polygon(стрелка, fill=БИРЮЗА_ТЁМН + (255,))
    return im.resize((N, N), Image.LANCZOS)


def вариант_вспышка():
    """Четырёхлучевая вспышка — «в этот миг»."""
    im, c, r = круг_подложка()
    p = []
    for i in range(8):
        a = math.pi / 4 * i - math.pi / 2
        rr = r * (0.66 if i % 2 == 0 else 0.20)
        p.append((c + rr * math.cos(a), c + rr * math.sin(a)))
    return знак(im, [p], 8 * K).resize((N, N), Image.LANCZOS)


def вариант_секундомер():
    """Секундомер со стрелкой на нуле — «мгновенно»."""
    im, c, r = круг_подложка()
    g = ImageDraw.Draw(im)
    R = r * 0.52
    cy = c + r * 0.08
    g.ellipse((c - R + 8 * K, cy - R + 10 * K, c + R + 8 * K, cy + R + 10 * K), fill=(20, 40, 48, 255))
    g.rectangle((c - r * 0.10 + 8 * K, cy - R - r * 0.20 + 10 * K, c + r * 0.10 + 8 * K, cy - R + 10 * K),
                fill=(20, 40, 48, 255))
    g.rectangle((c - r * 0.10, cy - R - r * 0.20, c + r * 0.10, cy - R + 2 * K), fill=(255, 255, 255, 255),
                outline=ЧЕРН, width=3 * K)
    g.ellipse((c - R, cy - R, c + R, cy + R), fill=(255, 255, 255, 255), outline=ЧЕРН, width=3 * K)
    g.polygon([(c - 0.07 * r, cy), (c, cy - R * 0.80), (c + 0.07 * r, cy)], fill=БИРЮЗА_ТЁМН + (255,))
    g.ellipse((c - 0.09 * r, cy - 0.09 * r, c + 0.09 * r, cy + 0.09 * r), fill=БИРЮЗА_ТЁМН + (255,))
    return im.resize((N, N), Image.LANCZOS)


def плюс_белый():
    """Плюс: тёмный, острые углы, одна жирная белая обводка."""
    W = N * K
    im = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    c = W // 2
    L, T = int(W * 0.34), int(W * 0.11)
    d = ImageDraw.Draw(im)
    for t, l, цвет in ((T + 26 * K, L + 26 * K, (255, 255, 255, 255)), (T, L, (40, 30, 26, 255))):
        d.rectangle((c - l, c - t, c + l, c + t), fill=цвет)
        d.rectangle((c - t, c - l, c + t, c + l), fill=цвет)
    return im.resize((N, N), Image.LANCZOS)


def варианты(out):
    os.makedirs(out, exist_ok=True)
    for имя, f in (('при-установке-А-молния-зеркало', вариант_молния_зеркало),
                   ('при-установке-Б-карта-в-ячейку', вариант_карта_в_ячейку),
                   ('при-установке-В-вспышка', вариант_вспышка),
                   ('при-установке-Г-секундомер', вариант_секундомер),
                   ('плюс', плюс_белый)):
        f().save(os.path.join(out, имя + '.png'))
