# -*- coding: utf-8 -*-
"""Линейная иконка из рендера — в стиль экспорта 60–68 (Влад 03.10.2026):
линии толще (расширение маски), цвет #4C5467, внутри #FAFAFA, снаружи
прозрачно, холст 402×402, предмет высотой ~212 px по центру.
python tools/icon_line_cleanup.py <рендер.jpg> <выход.png> [толщина=6] [высота=212]
"""
import sys
import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage

INK = (76, 84, 103)
FILL = (250, 250, 250)


def обработать(путь, выход, r=6, высота=212, W=402):
    a = np.array(Image.open(путь).convert('L'))
    ink = a < 150
    ink = ndimage.binary_dilation(ink, structure=ndimage.generate_binary_structure(2, 1), iterations=r)
    ink = ndimage.binary_closing(ink, iterations=2)
    снаружи, _ = ndimage.label(~ink)
    фон = снаружи == снаружи[0, 0]
    rgba = np.zeros(a.shape + (4,), np.uint8)
    rgba[~фон] = FILL + (255,)
    rgba[ink] = INK + (255,)
    im = Image.fromarray(rgba)
    im = im.crop(im.getbbox())
    k = высота / im.height
    im = im.resize((round(im.width * k), высота), Image.LANCZOS)
    out = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    out.alpha_composite(im, ((W - im.width) // 2, (W - im.height) // 2))
    out.save(выход)


if __name__ == '__main__':
    обработать(sys.argv[1], sys.argv[2], *(int(x) for x in sys.argv[3:]))
