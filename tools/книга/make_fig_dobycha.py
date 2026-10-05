# -*- coding: utf-8 -*-
"""Рисунок главы 7, пример добычи (дизайнер 05.10.2026: «стр. 26 бедная —
визуальный пример стройки добытчика у тайла зарождения, добычи двумя
добытчиками и отправки келемия на планшет хранилища»).

Три кадра: 1) Глория ставит добытчик стенкой к тайлу — он сразу добывает
1 келемий; 2) «Добыть»: два её запитанных добытчика берут по 1;
3) кубики ложатся в открытые ячейки её планшета хранилища.
Кадры поля рисует движок (СнимокСцены, сцены/добыча-*.yaml).
"""
import os
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFont

D = os.path.dirname(os.path.abspath(__file__))
КОРЕНЬ = os.path.dirname(os.path.dirname(D))
sys.path.insert(0, D)
from контур import обвести  # noqa: E402

ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]
РАННЕР = os.path.join(КОРЕНЬ, "gui", "target", "kelium-runner.jar")
ТЕКСТ = os.path.join(КОРЕНЬ, "data", "textures")
шр = ImageFont.truetype(os.path.expanduser("~/.fonts/TekturNarrow-Bold.ttf"), 62)
ОХРА = (107, 68, 19, 255)
ВЫСОТА = 620


def сцена(имя):
    png = os.path.join(D, "_%s.png" % имя)
    subprocess.run(["java", "-Dfile.encoding=UTF-8", "-Djava.awt.headless=true", "-cp", РАННЕР,
                    "kelium.gui.replay2.СнимокСцены", os.path.join(D, "сцены", имя + ".yaml"), png],
                   check=True, capture_output=True, cwd=КОРЕНЬ,
                   env=dict(os.environ, LANG="C.UTF-8", LC_ALL="C.UTF-8"))
    return обвести(Image.open(png).convert("RGBA"), 5)


def выс(im, h):
    return im.resize((round(im.width * h / im.height), h), Image.LANCZOS)


# планшет хранилища Глории (жёлтая, p4): открыты ячейка под добытчиком №1,
# под №2 (оба построены) и базовая; в базовой уже лежал келемий
пл = Image.open(os.path.join(ТЕКСТ, "board", "storage-p4.png")).convert("RGBA")
кел = Image.open(os.path.join(ТЕКСТ, "icons", "kelium.png")).convert("RGBA")
for x, y, w, h in ((461, 36, 112, 112), (188, 179, 112, 112), (718, 606, 112, 112)):
    к = кел.resize((round(w * 0.92), round(w * 0.92)), Image.LANCZOS)
    пл.alpha_composite(к, (x + (w - к.width) // 2, y + (h - к.height) // 2))
пл = пл.crop((90, 0, 900, 886))
хран = обвести(выс(пл, ВЫСОТА), 5)

кадры = [выс(сцена("добыча-стройка"), ВЫСОТА), выс(сцена("добыча-два"), ВЫСОТА), хран]
подписи = ["1. новый добытчик сразу добывает", "2. «Добыть»: каждый запитанный +1",
           "3. в ячейки хранилища"]
мерка = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
кол = [max(k.width, int(мерка.textlength(п, font=шр)) + 30) for k, п in zip(кадры, подписи)]
ЗАЗОР = 300
W = sum(кол) + ЗАЗОР * 2
холст = Image.new("RGBA", (W, ВЫСОТА + 110), (0, 0, 0, 0))
d = ImageDraw.Draw(холст)
x = 0
for i, (k, п) in enumerate(zip(кадры, подписи)):
    холст.alpha_composite(k, (x + (кол[i] - k.width) // 2, 0))
    d.text((x + кол[i] // 2, ВЫСОТА + 24), п, font=шр, fill=ОХРА, anchor="ma")
    x += кол[i]
    if i < 2:
        y = ВЫСОТА // 2
        x0 = x + 50
        d.polygon([(x0, y - 30), (x0 + 120, y - 30), (x0 + 120, y - 66), (x0 + 200, y),
                   (x0 + 120, y + 66), (x0 + 120, y + 30), (x0, y + 30)], fill=ОХРА)
        x += ЗАЗОР
png = os.path.join(D, "_добыча-пример.png")
холст.save(png)
figure("добыча-пример", png, 2200, холст.width, холст.height, [], css_w="82%", pad="0.6mm 0 0.8mm")
print("ok", холст.size)
