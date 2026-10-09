# -*- coding: utf-8 -*-
"""Рисунок главы 9: бой — атака по одному выбранному гексу с двух соседних.

Кадр рисует движок (kelium.gui.replay2.СнимокБоя): выбранный гекс обведён
пунктиром, стрелки идут с соседних гексов.
"""
import os
import subprocess

from PIL import Image

D = os.path.dirname(os.path.abspath(__file__))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]

КОРЕНЬ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
РАННЕР = os.path.join(КОРЕНЬ, "gui", "target", "kelium-runner.jar")
png = os.path.join(D, "_бой.png")

subprocess.run(["java", "-Dfile.encoding=UTF-8", "-Djava.awt.headless=true",
                "-cp", РАННЕР, "kelium.gui.replay2.СнимокБоя", png, "320"],
               check=True, capture_output=True, cwd=КОРЕНЬ)

vw, vh = Image.open(png).size
figure("бой", png, 1400, vw, vh, [], css_w="100%", pad="0.6mm 0 0.6mm", в_колонке=True)
print("ok", (vw, vh))
