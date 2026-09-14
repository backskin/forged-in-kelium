# -*- coding: utf-8 -*-
"""Рисунок главы 5: пример одного хода на поле (движок рисует кадры)."""
import os
import subprocess

from PIL import Image

D = os.path.dirname(os.path.abspath(__file__))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]

КОРЕНЬ = os.path.join("C:", os.sep, "shared", "forged-in-kelium")
РАННЕР = os.path.join(КОРЕНЬ, "gui", "target", "kelium-runner.jar")
png = os.path.join(D, "_пример-хода.png")
subprocess.run(["java", "-Dfile.encoding=UTF-8", "-Djava.awt.headless=true",
                "-cp", РАННЕР, "kelium.gui.replay2.СнимокПримера", png, "150"],
               check=True, capture_output=True, cwd=КОРЕНЬ)
vw, vh = Image.open(png).size
figure("пример-хода", png, 1600, vw, vh, [], css_w="74%", pad="1mm 0 1mm")
print("ok", vw, vh)
