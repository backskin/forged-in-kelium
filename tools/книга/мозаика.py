# -*- coding: utf-8 -*-
"""Фон внутренних полос — бесшовная мозаика (дизайнер 14.10.2026).

Решётка острых гексов; в каждом гексе — гексаграмма из середин его рёбер.
Стороны гексаграмм соседних гексов лежат на одной прямой, поэтому весь фон
читается как три семейства непрерывных линий, проходящих через решётку:
звёзды, треугольники и малые гексы складываются в один бесконечный узор.
Плитка — ровно один период решётки (√3·s × 3·s); всё, что выступает за её
край, рисуется ещё раз со сдвигом на ширину/высоту и продолжается на соседней
плитке, так что стыков не видно.

Запуск без аргументов пишет пробу «_мозаика-проба.png» (поле 4×4 плитки).
"""
import math

S = 10.0
К3 = math.sqrt(3)
W, H = К3 * S, 3 * S
ЦВЕТ = "%232A2318"


def _точка(cx, cy, угол, r):
    a = math.radians(угол)
    return cx + r * math.cos(a), cy + r * math.sin(a)


def svg(цвет=ЦВЕТ):
    центры = [(0.0, 0.0), (W / 2, H / 2), (W, 0.0), (0.0, H), (W, H)]
    f = lambda v: "%.2f" % v
    рёбра, звёзды, малые, узлы = [], [], [], set()
    for cx, cy in центры:
        в = [_точка(cx, cy, -90 + 60 * k, S) for k in range(6)]
        for k in range(6):                   # общее ребро двух гексов — один раз
            a_, b_ = sorted([(f(в[k][0]), f(в[k][1])), (f(в[(k + 1) % 6][0]), f(в[(k + 1) % 6][1]))])
            рёбра.append((a_, b_))
        м = [_точка(cx, cy, -60 + 60 * k, S * К3 / 2) for k in range(6)]
        for тр in ((0, 2, 4), (1, 3, 5)):
            звёзды.append("M" + "L".join("%s %s" % (f(м[i][0]), f(м[i][1])) for i in тр) + "Z")
        # малый гекс в середине звезды — вершины на пересечениях сторон
        мг = [_точка(cx, cy, -90 + 60 * k, S / 2) for k in range(6)]
        малые.append("M" + "L".join("%s %s" % (f(x), f(y)) for x, y in мг) + "Z")
        узлы.add((round(cx, 2), round(cy, 2)))
        for x, y in в:
            узлы.add((round(x, 2), round(y, 2)))
    круги = "".join("<circle cx='%s' cy='%s' r='%s'/>" % (f(x), f(y), "1.0" if (x, y) in
                    {(round(a, 2), round(b, 2)) for a, b in центры} else ".7") for x, y in узлы)
    return ("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 %s %s'>"
            "<path d='%s' fill='%s' fill-opacity='.035' stroke='none'/>"
            "<path d='%s' fill='none' stroke='%s' stroke-opacity='.09' stroke-width='.45'/>"
            "<path d='%s' fill='none' stroke='%s' stroke-opacity='.06' stroke-width='.35'/>"
            "<g fill='%s' fill-opacity='.10'>%s</g></svg>"
            % (f(W), f(H), "".join(малые), цвет, "".join(звёзды), цвет,
               "".join("M%s %sL%s %s" % (a_ + b_) for a_, b_ in sorted(set(рёбра))), цвет, цвет, круги))


def url():
    return 'url("data:image/svg+xml;utf8,%s")' % svg()


ШИРИНА_ММ = 22.0                     # плитка на полосе: 22 × 38.1 мм (укрупнено 14.10)
ПРОЗРАЧНОСТЬ = .62                   # общая заметность узора на полосе
ВЫСОТА_ММ = ШИРИНА_ММ * H / W


if __name__ == "__main__":
    import os, sys
    import pymupdf
    from PIL import Image
    плитка = svg("#2A2318").replace("%23", "#")
    док = pymupdf.open(stream=плитка.encode(), filetype="svg")
    pix = док[0].get_pixmap(matrix=pymupdf.Matrix(12, 12), alpha=True)
    t = Image.frombytes("RGBA", (pix.width, pix.height), pix.samples)
    поле = Image.new("RGBA", (t.width * 4, t.height * 4), (244, 238, 220, 255))
    for i in range(4):
        for j in range(4):
            поле.alpha_composite(t, (i * t.width, j * t.height))
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "_мозаика-проба.png")
    поле.convert("RGB").save(out)
    print(out, поле.size)
