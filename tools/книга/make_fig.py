# -*- coding: utf-8 -*-
"""Рисунок главы 11: четыре жетона модулей (красный обычный и золотой, синий обычный и золотой) с выносками."""
import os
from PIL import Image

D = os.path.dirname(os.path.abspath(__file__))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]

SRC = r"C:\shared\Yandex.Disk\Forged in Kelium\Общие компоненты\экспорт-жетоны-модулей"
H = 600
GAP = 40
files = ["Жетон прокачки атаки-1.png", "Жетон прокачки атаки-7.png",
         "Жетон прокачки найма-3.png", "Жетон прокачки найма-7.png"]
ims = []
for f in files:
    im = Image.open(os.path.join(SRC, f)).convert("RGBA")
    print(f, im.size)
    im = im.resize((round(im.width * H / im.height), H), Image.LANCZOS)
    ims.append(im)
xs = []
x = 0
for k, im in enumerate(ims):
    xs.append(x)
    x += im.width + (GAP * 3 if k == 1 else GAP)
W = x - GAP
canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
for im, x0 in zip(ims, xs):
    canvas.paste(im, (x0, 0), im)
png = os.path.join(D, "_модули.png")
canvas.save(png)
print("canvas", canvas.size, xs)

rp, rg, bp, bg = xs
bw = ims[2].width
marks = [
    ("А", rp + 150, 100, rp + 150, -80),
    ("Б", rp + 130, 430, rp - 90, 430),
    ("В", rg + 300, 215, rg + 300, -80),
    ("Г", rg + 300, 320, rg + 300, 680),
    ("Д", bp + bw // 2, 150, bp + bw // 2, -80),
    ("Е", bp + bw // 2 - 20, 470, bp + bw // 2 - 20, 680),
    ("Ж", bp + bw - 40, 455, bg + bw // 2, 680),
]
figure("модули", png, 1800, W, H, marks, 40, 50, "84%", "8mm 0 12mm", значок_мм=6)
print("ok")
