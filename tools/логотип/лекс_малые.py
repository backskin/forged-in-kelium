# -*- coding: utf-8 -*-
"""LEX TALIONIS — значок «LT», боковая надпись коробки и промты рендера (12.10.2026).

  значок: LT в шестигранной рамке (гекс игры), 1000×1000 → шаблон 2.4:1 без
          изменений не нужен: рендерим на квадрате 1:1;
  боковина: одна строка «LEX ◆ TALIONIS» с планками по концам, шаблон 2400×1000
          (строка по центру; после рендера обрезается и поворачивается на 90°);
  промты для четырёх раскладок логотипа, значка и боковины.

    python tools/логотип/лекс_малые.py
"""
import importlib.util
import os

from PIL import Image, ImageDraw

D = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("р", os.path.join(D, "лекс_раскладки.py"))
р = importlib.util.module_from_spec(spec)
spec.loader.exec_module(р)

ВЫХОД = р.ШАБЛОНЫ

НАЧАЛО = ("Restyle the attached lettering into a finished title logo. KEEP EVERY LETTER AND EVERY SHAPE EXACTLY "
          "AS IN THE IMAGE: the same square-cut typeface with its angular chamfered corners, the same sizes, "
          "spacing and layout — {раскладка}. Do not redraw, respell, reshape or move any letter or shape; add "
          "no letters, remove none; add no other text and no symbols that look like letters.")
БУКВЫ = ("1. THE LETTERS: every letter is a thick slab with a narrow visible side depth and chamfered edges "
         "catching hard pale highlights, made of PALE weathered steel — light warm grey, almost bone-white — "
         "with one very thick near-black outline, so the logo reads clearly on a busy sandy background. "
         "IF THE LETTERS ARE DARK, THE COLOUR IS WRONG.")
КОНЕЦ = ("NO VISUAL NOISE: no web of cracks, no grit, no speckle, no scratches, no splatter, no rubble scatter, "
         "no smoke clouds, no sparks. Nothing in the sky: no ships, no planets, no stars, no space. Plain flat "
         "pure white background, nothing else in the image. Crisp cel-shaded comic-book style: ink line and "
         "hard-edged flat tones; no photographic render, no 3D gloss, no bloom, no lens flare.")
ЗЕРКАЛО = ("MIRRORED DAMAGE (an eye for an eye): the damage on the left half of the logo is repeated exactly "
           "mirrored on the right half — one clean round shell hole in the second letter and the same hole in "
           "the second letter from the end, one big chipped corner on the first letter and the same on the "
           "last. Everything else stays clean.")
ИЗУМРУД = ("one thin crack glows from inside in warm vivid emerald green leaning toward yellow — the only bright "
           "colour in the image, never mint, never teal.")

ПРОМТЫ = {
    "1 линейки": ("the small widely spaced word LEX centred above the huge word TALIONIS, with a thick bar on "
                  "each side of LEX reaching out toward the edges",
                  "2. THE BARS beside LEX are heavy dark gunmetal steel beams with two big bolts each. 3. "
                  + ЗЕРКАЛО),
    "2 шестигранник": ("a regular hexagon with the word LEX inside it, sitting centred on the top edge of the "
                       "huge word TALIONIS",
                       "2. THE HEXAGON is a heavy dark gunmetal ring-plate with six big bolts, the word LEX "
                       "inside it in the same pale steel; in the hexagon ring " + ИЗУМРУД + " 3. " + ЗЕРКАЛО),
    "3 ступень": ("the word LEX at the left with a long thick bar running from it to the right edge, the huge "
                  "word TALIONIS below, one thin bar under it",
                  "2. THE BARS are heavy dark gunmetal steel beams with big bolts. 3. " + ЗЕРКАЛО),
    "5 пластина": ("the word LEX on a plate with cut corners, centred above the huge word TALIONIS and touching "
                   "its top edge",
                   "2. THE PLATE is heavy dark gunmetal steel with cut corners and four big bolts, the word LEX "
                   "on it in pale steel. 3. " + ЗЕРКАЛО),
    "значок LT": ("a thick regular hexagon ring with the two letters LT inside it, big and centred",
                  "2. THE HEXAGON is a heavy dark gunmetal ring-plate with six big bolts; the letters LT inside "
                  "are pale steel. 3. In the ring " + ИЗУМРУД),
    "боковина": ("one single line: a thick bar, the word LEX, a diamond, the word TALIONIS, a thick bar — all "
                 "on one horizontal line",
                 "2. THE BARS and the DIAMOND are heavy dark gunmetal steel with two big bolts each bar. 3. "
                 "One clean round shell hole in the last letter of TALIONIS and the same in the first letter "
                 "of LEX."),
}


def значок():
    S = 1000
    c = Image.new("RGBA", (S, S), р.БЕЛЫЙ)
    d = ImageDraw.Draw(c)
    R = 460
    d.polygon(р.шестигранник(S / 2, S / 2, R), fill=р.ЧЁРНЫЙ)
    d.polygon(р.шестигранник(S / 2, S / 2, R - 62), fill=р.БЕЛЫЙ)
    m = р.по_ширине("LT", 470, 0.06)
    р.чёрным(c, m, ((S - m.width) // 2, (S - m.height) // 2))
    return c


def боковина():
    c = р.пустой()
    d = ImageDraw.Draw(c)
    h = 150
    l = р.по_высоте("LEX", h * 0.78, 0.06)
    t = р.по_высоте("TALIONIS", h, 0.04)
    пр = 70
    ромб = 70
    общ = 120 + пр + l.width + пр + ромб + пр + t.width + пр + 120
    x = (р.W - общ) // 2
    cy = р.H // 2
    base = cy + t.height // 2
    р.планка(d, x, cy - 15, x + 120, cy + 15, 10)
    x += 120 + пр
    р.чёрным(c, l, (x, cy - l.height // 2))
    x += l.width + пр
    d.polygon([(x + ромб // 2, cy - ромб // 2), (x + ромб, cy), (x + ромб // 2, cy + ромб // 2), (x, cy)], fill=р.ЧЁРНЫЙ)
    x += ромб + пр
    р.чёрным(c, t, (x, base - t.height))
    x += t.width + пр
    р.планка(d, x, cy - 15, x + 120, cy + 15, 10)
    return c


def промт(ключ):
    раскладка, середина = ПРОМТЫ[ключ]
    return " ".join([НАЧАЛО.format(раскладка=раскладка), БУКВЫ, середина, КОНЕЦ])


if __name__ == "__main__":
    значок().convert("RGB").save(os.path.join(ВЫХОД, "ЛЕКС — шаблон значок LT.png"))
    боковина().convert("RGB").save(os.path.join(ВЫХОД, "ЛЕКС — шаблон боковина.png"))
    for к in ПРОМТЫ:
        п = промт(к)
        assert len(п) < 10000
        with open(os.path.join(ВЫХОД, "ПРОМТ — ЛЕКС %s (v1).txt" % к), "w", encoding="utf-8") as f:
            f.write(п)
        print(len(п), к)
