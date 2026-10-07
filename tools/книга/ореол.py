# -*- coding: utf-8 -*-
"""Светлый ореол под выносками рисунков, как на развороте подготовки (с. 8–9).

Просьба дизайнера 05.10.2026: «здесь и везде использовать обводку ломаных и
жирных точек, как на страницах раскладки игры». Под каждую линию, точку и
кольцо выноски кладётся та же фигура светлым цветом шире линии — выноска
читается и на тёмном арте, и на светлом.

Работает по готовым `_*.svg` (часть генераторов читает экспорт дизайнера, его
в облаке нет), повторный прогон ничего не меняет: старый ореол снимается.
    python3 tools/книга/ореол.py            — все рисунки
"""
import glob
import os
import re

D = os.path.dirname(os.path.abspath(__file__))
ЦВЕТ = "#F7F1E1"
КЛАСС = "выноска-ореол"


def _ширина(тег, по_умолчанию):
    м = re.search(r'stroke-width="([\d.]+)"', тег)
    return float(м.group(1)) if м else по_умолчанию


def с_ореолом(html):
    html = re.sub(r'<(?:polyline|circle)[^>]*class="%s"[^>]*/>' % КЛАСС, "", html)

    def слой(svg):
        ореолы = []
        линия = 3.0
        for тег in re.findall(r'<polyline[^>]*class="выноска-л"[^>]*/>', svg):
            линия = _ширина(тег, линия)
            т = re.search(r'points="([^"]+)"', тег).group(1)
            ореолы.append('<polyline points="%s" class="%s" fill="none" stroke="%s" '
                          'stroke-width="%.1f" stroke-linejoin="round" stroke-linecap="round" '
                          'opacity=".9"/>' % (т, КЛАСС, ЦВЕТ, линия * 2.8))
        for тег in re.findall(r'<circle[^>]*/>', svg):
            cx = re.search(r'cx="([-\d.]+)"', тег)
            cy = re.search(r'cy="([-\d.]+)"', тег)
            r = re.search(r'\br="([\d.]+)"', тег)
            if not (cx and cy and r):
                continue
            if 'class="выноска-т"' in тег:
                ореолы.append('<circle cx="%s" cy="%s" r="%.1f" class="%s" fill="%s" opacity=".9"/>'
                              % (cx.group(1), cy.group(1), float(r.group(1)) + линия, КЛАСС, ЦВЕТ))
            elif 'class="выноска-л"' in тег:          # кольцо вокруг элемента
                ореолы.append('<circle cx="%s" cy="%s" r="%s" class="%s" fill="none" stroke="%s" '
                              'stroke-width="%.1f" opacity=".9"/>'
                              % (cx.group(1), cy.group(1), r.group(1), КЛАСС, ЦВЕТ,
                                 _ширина(тег, линия) * 2.6))
        if not ореолы:
            return svg
        i = svg.index(">") + 1
        return svg[:i] + "".join(ореолы) + svg[i:]

    return re.sub(r'<svg\b.*?</svg>', lambda м: слой(м.group(0)), html, flags=re.S)


if __name__ == "__main__":
    n = 0
    for п in sorted(glob.glob(os.path.join(D, "_*.svg"))):
        s = open(п, encoding="utf-8").read()
        t = с_ореолом(s)
        if t != s:
            open(п, "w", encoding="utf-8").write(t)
            n += 1
    print("ореол:", n, "рисунков")
