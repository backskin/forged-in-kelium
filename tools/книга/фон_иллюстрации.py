# -*- coding: utf-8 -*-
"""Иллюстрации под полосами книги правил (дизайнер 14.10.2026).

Одна картинка из «Иллюстрации/фоны и сцены» на разворот: левая полоса
показывает её левую половину, правая — правую. В полный цвет она видна только
за рамкой; внутри рамки — сплошная вуаль цвета бумаги. Уголки рамки — в цвет,
«вывернутый» из цвета фона разворота.

Картинки копируются в «вёрстка/илл» обработанными, имя — по хешу. Запуск
после restyle.py и до make_back.py (так делает собрать_книгу.py).
"""
import glob
import hashlib
import io
import os
import re

from PIL import Image

D = os.path.dirname(os.path.abspath(__file__))
КНИГА = glob.glob(os.path.join(os.path.dirname(os.path.dirname(D)), "rules", "Книга правил*", "вёрстка", "Книга правил.html"))[0]
ИЛЛ = os.path.join(os.path.dirname(КНИГА), "илл")
ПАПКА = os.path.join(os.path.expanduser("~"), "Yandex.Disk", "Forged in Kelium", "Иллюстрации", "фоны и сцены")

# РАЗВОРОТ → номер картинки в папке (по алфавиту). Одна картинка на разворот,
# растянутая на обе полосы: левая показывает её левую половину, правая —
# правую, поэтому цвета соседних страниц всегда совпадают (дизайнер 14.10.2026).
# Левая полоса разворота — чётная: (4,5), (6,7)…
ВУАЛЬ = {8: .9}                    # разворот подготовки — тише: на нём поле и 18 шагов
РАЗВОРОТЫ = {4: 38, 6: 45, 8: 13, 10: 5, 12: 76, 14: 32, 16: 12, 18: 96, 20: 98, 22: 99,
             24: 103, 26: 53, 28: 17, 30: 16, 32: 46, 34: ("Арсенал", 10), 36: 100, 38: 75}
# Номер может быть парой (подпапка, номер в ней): Наука — лаборатория келемия
# из «Арсенал» (дизайнер 14.10.2026: «научная среда, припамбасы»).

CSS = """
  /* ---- иллюстрации под полосами (фон_иллюстрации.py) ----
     Картинка лежит на всей полосе и видна в полный цвет только за рамкой.
     Всё, что внутри рамки, — сплошная вуаль цвета бумаги с жёстким краем ровно
     по линии рамки (дизайнер 14.10.2026: «как на с. 12–13, везде»). У корешка
     вуаль идёт до края листа — по развороту нет шва. */
  .илл-фон { position: absolute; inset: 0; z-index: -3; pointer-events: none;
    background-repeat: no-repeat; background-size: 200% auto; }
  .илл-фон.лев { background-position: 0% 50%; }
  .илл-фон.прав { background-position: 100% 50%; }
  .илл-вуаль { position: absolute; z-index: -2; pointer-events: none; background: rgba(244,238,220,var(--вуаль,.86)); }
  .илл-вуаль.лев { inset: 5mm 0 5mm 5mm;
    clip-path: polygon(4mm 0, 100% 0, 100% 100%, 4mm 100%, 0 calc(100% - 4mm), 0 4mm); }
  .илл-вуаль.прав { inset: 5mm 5mm 5mm 0;
    clip-path: polygon(0 0, calc(100% - 4mm) 0, 100% 4mm, 100% calc(100% - 4mm), calc(100% - 4mm) 100%, 0 100%); }
"""


def файл(номер):
    папка = ПАПКА
    if isinstance(номер, tuple):
        папка, номер = os.path.join(ПАПКА, номер[0]), номер[1]
    return os.path.join(папка, sorted(x for x in os.listdir(папка) if x.lower().endswith(".jpg"))[номер])


ТОН = .60                          # к какой средней яркости приводятся картинки


def в_илл(путь):
    """Картинка разворота: яркость выровнена по горизонтали (левая и правая
    половины одного тона), контраст чуть сжат. Имя — по хешу результата."""
    import numpy as np
    from scipy.ndimage import gaussian_filter1d
    im = Image.open(путь).convert("RGB")
    im.thumbnail((2600, 2600), Image.LANCZOS)
    a = np.asarray(im, dtype=np.float32) / 255.0
    L = a @ np.array([.299, .587, .114], dtype=np.float32)
    колонки = gaussian_filter1d(L.mean(axis=0), a.shape[1] * 0.12)
    g = np.clip(ТОН / np.maximum(колонки, .05), .7, 1.6)[None, :, None]
    a = np.clip(a * g, 0, 1)
    сер = (a @ np.array([.299, .587, .114], dtype=np.float32))[..., None]
    a = np.clip(сер + (a - сер) * .76, 0, 1)                       # тише цвет
    a = ТОН + (a - ТОН) * .74                                     # и контраст — как у разворота 12–13
    выход = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))
    буфер = io.BytesIO()
    выход.save(буфер, "JPEG", quality=84, optimize=True)
    имя = hashlib.sha1(буфер.getvalue()).hexdigest()[:16] + ".jpg"
    куда = os.path.join(ИЛЛ, имя)
    if not os.path.exists(куда):
        open(куда, "wb").write(буфер.getvalue())
    return "илл/" + имя, уголок(a)


def уголок(a):
    """Цвет уголков рамки для разворота — «вывернутый» цвет фона за рамкой:
    средний цвет внешней кромки картинки, оттенок повёрнут на 180°, яркость —
    противоположная (на светлом фоне тёмный уголок, на тёмном — светлый)."""
    import colorsys
    import numpy as np
    h, w = a.shape[:2]
    видно = int(h - w * 220 / 440) // 2              # картинка выше полосы: срезано сверху и снизу
    a = a[max(видно, 0):h - max(видно, 0)]
    h = a.shape[0]
    к = int(min(h, w) * .05)
    кромка = np.concatenate([a[:к].reshape(-1, 3), a[-к:].reshape(-1, 3),
                             a[:, :к].reshape(-1, 3), a[:, -к:].reshape(-1, 3)])
    r, g, b = кромка.mean(axis=0)
    тон, свет, нас = colorsys.rgb_to_hls(r, g, b)
    свет = .22 if свет > .5 else .85
    r, g, b = colorsys.hls_to_rgb((тон + .5) % 1, свет, max(.55, min(.8, нас * 2.5)))
    return "#%02X%02X%02X" % (round(r * 255), round(g * 255), round(b * 255))


def main():
    t = io.open(КНИГА, encoding="utf-8").read()
    t = re.sub(r'<div class="илл-фон[^"]*" style="[^"]*"></div>(<div class="илл-вуаль[^"]*"( style="[^"]*")?></div>)?', "", t)
    a = t.find("  /* ---- иллюстрации под полосами")
    if a >= 0:
        b = t.index("</style>", a)
        t = t[:a] + t[b:]
    t = t.replace("</style>", CSS + "</style>", 1)
    части, n, поз, кадр, правила = [], 0, 0, {}, []
    for m in re.finditer(r'<div class="стр( [^"]*)?">', t):
        n += 1
        левая = n % 2 == 0
        левый = n if левая else n - 1
        if левый not in РАЗВОРОТЫ or "обложка" in (m.group(1) or ""):
            continue
        if левый not in кадр:
            кадр[левый] = в_илл(файл(РАЗВОРОТЫ[левый]))
            правила.append("  .стр:has(> .илл-фон.р%d) .кайма .к-скобка { stroke: %s; }" % (левый, кадр[левый][1]))
        вставка = ('<div class="илл-фон %s р%d" style="background-image:url(&quot;%s&quot;)"></div>'
                   '<div class="илл-вуаль %s"%s></div>') % ("лев" if левая else "прав", левый, кадр[левый][0],
                                                         "лев" if левая else "прав",
                                                         ' style="--вуаль:%s"' % ВУАЛЬ[левый] if левый in ВУАЛЬ else "")
        части.append(t[поз:m.end()] + вставка)
        поз = m.end()
    t = "".join(части) + t[поз:]
    t = t.replace(CSS + "</style>", CSS + "\n".join(правила) + "\n</style>", 1)
    io.open(КНИГА, "w", encoding="utf-8").write(t)
    print("иллюстрации под полосами:", len(части))


if __name__ == "__main__":
    main()
