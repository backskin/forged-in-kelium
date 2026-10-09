# -*- coding: utf-8 -*-
"""Сжать PDF книги, НЕ трогая значки (05.10.2026).

pymupdf.rewrite_images ужимал каждую картинку до заданного dpi — и значок
в строке (4 мм) становился пятном в 35 точек: «смутные силуэты» вместо иконок.
Здесь пережимаются только крупные картинки (иллюстрации, планшеты, обложки):
ширина больше ПОРОГ точек; значки и мелкие жетоны остаются как есть.

    python tools/книга/сжать_pdf.py <вход.pdf> <выход.pdf>
"""
import io
import sys

import pymupdf
from PIL import Image

ПОРОГ = 700          # точек: всё уже — значки и жетоны, их не трогаем
DPI = 200            # до какой плотности ужимать крупные картинки

вход, выход = sys.argv[1], sys.argv[2]
d = pymupdf.open(вход)
сделано = set()
for стр in d:
    for инфо in стр.get_images(full=True):
        xref, smask = инфо[0], инфо[1]
        if xref in сделано:
            continue
        сделано.add(xref)
        w, h = инфо[2], инфо[3]
        if w <= ПОРОГ:
            continue
        рамки = стр.get_image_rects(xref)
        if not рамки:
            continue
        ширина_дюйм = max(r.width for r in рамки) / 72.0
        нужно = max(ПОРОГ, round(ширина_дюйм * DPI))
        if нужно >= w * 0.9:
            continue
        pix = pymupdf.Pixmap(d, xref)
        if smask:
            pix = pymupdf.Pixmap(pix, pymupdf.Pixmap(d, smask))
        im = Image.open(io.BytesIO(pix.tobytes("png")))
        im = im.resize((нужно, round(h * нужно / w)), Image.LANCZOS)
        буф = io.BytesIO()
        if im.mode in ("RGBA", "LA") and im.getchannel("A").getextrema()[0] < 250:
            # прозрачные рисунки — палитрой 256 цветов с прозрачностью: вдвое-втрое меньше
            im.convert("RGBA").quantize(colors=256, method=Image.FASTOCTREE).save(буф, "PNG", optimize=True)
        else:
            im.convert("RGB").save(буф, "JPEG", quality=85, optimize=True)
        стр.replace_image(xref, stream=буф.getvalue())
d.save(выход, garbage=4, deflate=True)
print("готово:", выход)
