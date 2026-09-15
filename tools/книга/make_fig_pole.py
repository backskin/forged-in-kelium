# -*- coding: utf-8 -*-
"""Иллюстрации главы 4: кусок поля с базой и «что лежит на поле».

Обе сцены рисует движок (kelium.gui.replay2.СнимокПоля) — на месте заглушек
под художественную графику честнее показать настоящее поле, чем пустой
прямоугольник.
"""
import os
import subprocess

from PIL import Image

D = os.path.dirname(os.path.abspath(__file__))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]

КОРЕНЬ = os.path.join("C:", os.sep, "shared", "forged-in-kelium")
РАННЕР = os.path.join(КОРЕНЬ, "gui", "target", "kelium-runner.jar")

for сцена, имя, ширина in (("база", "база", "86%"), ("поле", "поле-сверху", "40%")):
    png = os.path.join(D, "_%s.png" % сцена)
    subprocess.run(["java", "-Dfile.encoding=UTF-8", "-Djava.awt.headless=true",
                    "-cp", РАННЕР, "kelium.gui.replay2.СнимокПоля", сцена, png, "190"],
                   check=True, capture_output=True, cwd=КОРЕНЬ)
    vw, vh = Image.open(png).size
    figure(имя, png, 1500, vw, vh, [], css_w=ширина, pad="1.0mm 0 1.2mm",
           в_колонке=(сцена == "база"))
    print("ok", имя, (vw, vh))
