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

КОРЕНЬ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
РАННЕР = os.path.join(КОРЕНЬ, "gui", "target", "kelium-runner.jar")

for сцена, имя, ширина in (("база", "база", "80%"), ("поле", "поле-сверху", "100%")):
    png = os.path.join(D, "_%s.png" % сцена)
    subprocess.run(["java", "-Dfile.encoding=UTF-8", "-Djava.awt.headless=true",
                    "-cp", РАННЕР, "kelium.gui.replay2.СнимокПоля", сцена, png, "190"],
                   check=True, capture_output=True, cwd=КОРЕНЬ)
    vw, vh = Image.open(png).size
    # Выноски схемы поля (замечание к вёрстке 29.09: без них список под
    # схемой не к чему привязать). Буквы по порядку чтения — слева направо.
    выноски = [] if сцена == "база" else [
        ("А", 200, 560, 90, 880),      # недоступный гекс
        ("Б", 480, 660, 250, 940),     # тайл зарождения
        ("В", 675, 775, 480, 960),     # контейнер
        ("Г", 770, 930, 1000, 960),    # круг энергии
        ("Д", 890, 490, 1110, 330),    # нейтральная постройка
        ("Е", 1060, 670, 1170, 930),   # контейнер в небе
    ]
    figure(имя, png, 1500, vw, vh, выноски, css_w=ширина, pad="1.0mm 0 1.2mm",
           в_колонке=True)
    if сцена == "поле":
        # СТР. 12 — то же поле крупно, во всю ширину полосы (пояснения ниже
        # в две колонки)
        figure("поле-сверху-крупно", png, 2000, vw, vh, выноски, css_w="71%",
               pad="1.0mm 0 2.0mm", в_колонке=False)
    print("ok", имя, (vw, vh))
