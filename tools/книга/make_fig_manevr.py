# -*- coding: utf-8 -*-
"""Рисунок главы 8: манёвр — выбранный гекс, кто с него уходит и кто приходит.

Кадр рисует движок (kelium.gui.replay2.СнимокМанёвра) тем же рисовальщиком
поля, что и партию: выбранный гекс обведён пунктиром, пути пронумерованы
в порядке правил — сначала вывести, потом ввести.
"""
import os
import subprocess

from PIL import Image

D = os.path.dirname(os.path.abspath(__file__))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]

КОРЕНЬ = os.path.dirname(os.path.dirname(D))
РАННЕР = os.path.join(КОРЕНЬ, "gui", "target", "kelium-runner.jar")
png = os.path.join(D, "_манёвр.png")

subprocess.run(["java", "-Dfile.encoding=UTF-8", "-Djava.awt.headless=true",
                "-cp", РАННЕР, "kelium.gui.replay2.СнимокМанёвра", png, "320"],
               check=True, capture_output=True, cwd=КОРЕНЬ)

vw, vh = Image.open(png).size
figure("манёвр", png, 1400, vw, vh, [], css_w="100%", pad="0.6mm 0 0.6mm", в_колонке=True)
print("ok", (vw, vh))
