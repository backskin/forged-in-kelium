# -*- coding: utf-8 -*-
"""Обход всех полос: где текст вылез за поле набора и где полоса полупуста."""
import os
import pymupdf

D = os.path.dirname(os.path.abspath(__file__))
d = pymupdf.open(os.path.join(D, "book.pdf"))
for i, pg in enumerate(d):
    H = pg.rect.height
    блоки = [b for b in pg.get_text("blocks") if b[4].strip() != str(i + 1)]
    if not блоки:
        print("%3d ПУСТАЯ" % (i + 1))
        continue
    низ = max(b[3] for b in блоки)
    if низ > H - 34:
        print("%3d ПЕРЕПОЛНЕНА  низ %.0f из %.0f" % (i + 1, низ, H))
    elif низ < H * 0.72:
        print("%3d полупуста    низ %.0f из %.0f" % (i + 1, низ, H))
d.close()
