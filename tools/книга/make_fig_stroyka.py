# -*- coding: utf-8 -*-
"""Рисунок главы 7: полоса контрпримеров стройки — два «нельзя» и два «можно».

Кадры рисует движок (kelium.gui.replay2.СнимокСтройки): зона стройки и посадка
жетонов по секторам берутся из той же отрисовки, что и настоящая партия.
"""
import os
import subprocess

D = os.path.dirname(os.path.abspath(__file__))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]

КОРЕНЬ = os.path.join("C:", os.sep, "shared", "forged-in-kelium")
РАННЕР = os.path.join(КОРЕНЬ, "gui", "target", "kelium-runner.jar")
png = os.path.join(D, "_стройка.png")

subprocess.run(["java", "-Dfile.encoding=UTF-8", "-Djava.awt.headless=true",
                "-cp", РАННЕР, "kelium.gui.replay2.СнимокСтройки", png, "150"],
               check=True, capture_output=True, cwd=КОРЕНЬ)

from PIL import Image
vw, vh = Image.open(png).size
figure("стройка", png, 2000, vw, vh, [], css_w="100%", pad="1.4mm 0 1.6mm")
print("ok", (vw, vh))
