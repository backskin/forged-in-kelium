# -*- coding: utf-8 -*-
"""Наложить координатную сетку на картинку — чтобы ставить выноски по числам,
а не на глаз. python сетка.py <путь> [шаг] [ширина показа]"""
import os, sys
from PIL import Image, ImageDraw, ImageFont
p = sys.argv[1]
шаг = int(sys.argv[2]) if len(sys.argv) > 2 else 200
ш = int(sys.argv[3]) if len(sys.argv) > 3 else 1400
im = Image.open(p).convert("RGBA")
bg = Image.new("RGBA", im.size, (247, 241, 225, 255))
bg.alpha_composite(im)
im = bg.convert("RGB")
d = ImageDraw.Draw(im)
f = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", max(14, шаг // 8))
for x in range(0, im.width, шаг):
    d.line((x, 0, x, im.height), fill=(200, 0, 0), width=2)
    d.text((x + 3, 3), str(x), font=f, fill=(200, 0, 0))
for y in range(0, im.height, шаг):
    d.line((0, y, im.width, y), fill=(0, 0, 200), width=2)
    d.text((3, y + 3), str(y), font=f, fill=(0, 0, 200))
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "_сетка.png")
im.resize((ш, round(im.height * ш / im.width))).save(out)
print(out, im.size)
