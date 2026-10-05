# -*- coding: utf-8 -*-
"""Рисунок главы 8: уничтожение ЦУ (дизайнер 05.10.2026).

Верхний ряд: гекс с ЦУ Брианы до и после последнего удара, жетон ЦУ уходит
на свалку Акселя. Нижний ряд: модуль блокировки боя снимается с планшета
войск Брианы → переворачивается стороной с 3 звёздами → лежит перед Акселем.
Кадры поля рисует движок (СнимокСцены, сцены/цу-*.yaml).
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
шр = ImageFont.truetype(os.path.expanduser("~/.fonts/TekturNarrow-Bold.ttf"), 64)
ОХРА = (107, 68, 19, 255)


def сцена(имя):
    png = os.path.join(D, "_%s.png" % имя)
    subprocess.run(["java", "-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8", "-Djava.awt.headless=true",
                    "-cp", РАННЕР, "kelium.gui.replay2.СнимокСцены",
                    os.path.join(D, "сцены", имя + ".yaml"), png], check=True, capture_output=True,
                   cwd=КОРЕНЬ, env=dict(os.environ, LANG="C.UTF-8", LC_ALL="C.UTF-8"))
    return обвести(Image.open(png).convert("RGBA"), 5)


def выс(im, h):
    return im.resize((round(im.width * h / im.height), h), Image.LANCZOS)


def стрелка(d, x, y, подпись=None):
    d.polygon([(x, y - 34), (x + 130, y - 34), (x + 130, y - 72), (x + 210, y),
               (x + 130, y + 72), (x + 130, y + 34), (x, y + 34)], fill=ОХРА)
    if подпись:
        d.text((x + 105, y - 90), подпись, font=шр, fill=ОХРА, anchor="ms")


def ряд(куски, подписи_стрелок, подписи_под):
    """куски: картинки; между ними стрелки с подписями; под каждой — подпись.
    Колонка куска не уже своей подписи."""
    мерка = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
    кол = [max(k.width, int(мерка.textlength(п or "", font=шр)) + 20) for k, п in zip(куски, подписи_под)]
    H = max(k.height for k in куски)
    W = sum(кол) + 380 * (len(куски) - 1)
    холст = Image.new("RGBA", (W, H + 110), (0, 0, 0, 0))
    d = ImageDraw.Draw(холст)
    x = 0
    for i, k in enumerate(куски):
        холст.alpha_composite(k, (x + (кол[i] - k.width) // 2, (H - k.height) // 2))
        if подписи_под[i]:
            d.text((x + кол[i] // 2, H + 20), подписи_под[i], font=шр, fill=ОХРА, anchor="ma")
        x += кол[i]
        if i < len(куски) - 1:
            стрелка(d, x + 85, H // 2, подписи_стрелок[i])
            x += 380
    return холст


до, после = выс(сцена("цу-до"), 560), выс(сцена("цу-после"), 560)
оборот = обвести(выс(Image.open(os.path.join(ТЕКСТ, "token", "command_center_trophy.png")).convert("RGBA"), 300), 5)
р1 = ряд([до, после, оборот], [None, None], ["последний удар", "ЦУ уничтожен", "на свалку Акселя"])

# планшет войск Брианы (красный); модуль блокировки лежит на ячейке
# специальной атаки пехоты — род, изображённый на модуле
планшет = Image.open(os.path.join(ТЕКСТ, "board", "troop-p2.png")).convert("RGBA")
планшет = планшет.crop((0, 0, round(планшет.width * 0.265), планшет.height))
мод_лицо = Image.open(os.path.join(ТЕКСТ, "module", "mod_cu_infantry.png")).convert("RGBA")
мод_оборот = Image.open(os.path.join(ТЕКСТ, "module", "mod_cu_trophy.png")).convert("RGBA")
ячейка = (486, 425, 772, 760)                      # специальная атака пехоты
м = мод_лицо.resize((ячейка[2] - ячейка[0], ячейка[2] - ячейка[0]), Image.LANCZOS)
планшет.alpha_composite(м, (ячейка[0], (ячейка[1] + ячейка[3] - м.height) // 2))
пустой = Image.open(os.path.join(ТЕКСТ, "board", "troop-p2.png")).convert("RGBA")
пустой = пустой.crop((0, 0, round(пустой.width * 0.265), пустой.height))
пл = обвести(выс(планшет, 420), 5)
# четвёртый шаг (дизайнер 05.10.2026): планшет Брианы, где модуля больше нет —
# следующий удар по её ЦУ модуля уже не даст
р2 = ряд([пл, обвести(выс(мод_лицо, 330), 5), обвести(выс(мод_оборот, 330), 5),
          обвести(выс(пустой, 420), 5)],
         ["снять", "перевернуть", None],
         ["планшет войск Брианы", "модуль блокировки боя", "3 очка — Акселю", "у Брианы модуля нет"])

W = max(р1.width, р2.width)
холст = Image.new("RGBA", (W, р1.height + р2.height + 100), (0, 0, 0, 0))
холст.alpha_composite(р1, ((W - р1.width) // 2, 0))
холст.alpha_composite(р2, ((W - р2.width) // 2, р1.height + 100))
png = os.path.join(D, "_цу-уничтожен.png")
холст.save(png)
figure("цу-уничтожен", png, 2000, холст.width, холст.height, [], css_w="74%", pad="0.4mm 0 0.6mm")
print("ok", холст.size)
