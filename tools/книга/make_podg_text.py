# -*- coding: utf-8 -*-
"""Текст разворота «Подготовка к игре» — из главы 3.

Глава 3 не проходит через reflow: у разворота своя вёрстка (снимок стола по
центру, столбцы текста у внешних краёв). Этот сборщик читает
«03 — Подготовка к игре.md» и заменяет в вёрстке оба столбца: шаги 1–9 идут
на левую полосу, остальные — на правую. Разделы «## …» становятся
заголовками блоков, «**N. Имя.** текст» — шагами с кружком номера,
«> **Совет.** …» — врезкой.

python make_podg_text.py
"""
import glob
import html
import io
import os
import re

D = os.path.dirname(os.path.abspath(__file__))
ПАПКА = glob.glob(os.path.join(os.path.dirname(os.path.dirname(D)), "rules", "Книга правил*"))[0]
ГЛАВА = os.path.join(ПАПКА, "03 — Подготовка к игре.md")
КНИГА = os.path.join(ПАПКА, "вёрстка", "Книга правил.html")
ЛЕВАЯ_ДО = 9


def строй(s):
    s = html.escape(s, quote=False)
    s = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", s)
    s = re.sub(r"(?<![\w])(с\.|глава|главе) (\d)", "\\1\u00a0\\2", s)
    return s


def разобрать():
    t = io.open(ГЛАВА, encoding="utf-8").read()
    t = t.split("\n---\n", 1)[1]
    части = []          # (номер шага или None, заголовок раздела или None, html)
    раздел = None
    for абз in re.split(r"\n\s*\n", t):
        абз = абз.strip()
        if not абз:
            continue
        if абз.startswith("## "):
            раздел = абз[3:].strip()
            continue
        if абз.startswith(">"):
            текст = " ".join(x.lstrip("> ").strip() for x in абз.splitlines())
            m = re.match(r"\*\*(Совет|Важно!?)\.\*\*\s*(.+)", текст)
            вид = "совет" if m.group(1) == "Совет" else "важно"
            части.append((None, None,
                          f'<div class="пример {вид}" style="margin:0 0 1.1mm"><span class="метка">'
                          f'{m.group(1)}</span><p>{строй(m.group(2))}</p></div>'))
            continue
        текст = " ".join(x.strip() for x in абз.splitlines())
        m = re.match(r"\*\*(\d+)\.\s*(.+?)\*\*\s*(.+)", текст)
        n, имя, тело = int(m.group(1)), m.group(2), m.group(3)
        # у шага «Первый игрок» имя совпадает с разделом — не повторяем его
        голова = "" if раздел and имя.rstrip(".") == раздел else f"<b>{строй(имя)}</b> "
        p = f'<p><span class="шаг">{n}</span>{голова}{строй(тело)}</p>'
        части.append((n, раздел, p))
        раздел = None
    return части


def столбец(части):
    out = ['    <div class="столбец">']
    for n, раздел, p in части:
        if n is None:
            out.append("      " + p)
        elif раздел:
            out.append(f'      <div class="блок">\n        <h3>{строй(раздел)}</h3>\n        {p}\n      </div>')
        else:
            out.append(f'      <div class="блок">{p}</div>')
    out.append("    </div>")
    return "\n".join(out)


def заменить(t, класс, новый):
    a = t.index(класс)
    b = t.index('<div class="столбец">', a)
    c = t.index('<div class="колонцифра">', b)
    return t[:b] + новый.lstrip() + "\n\n    " + t[c:]


def main():
    части = разобрать()
    лев = [ч for ч in части if ч[0] is not None and ч[0] <= ЛЕВАЯ_ДО]
    прав = [ч for ч in части if ч[0] is None or ч[0] > ЛЕВАЯ_ДО]
    t = io.open(КНИГА, encoding="utf-8").read()
    t = заменить(t, "подготовка левая-подг", столбец(лев))
    t = заменить(t, "подготовка правая-подг", столбец(прав))
    io.open(КНИГА, "w", encoding="utf-8").write(t)
    print("текст разворота подготовки собран: шагов", sum(1 for ч in части if ч[0]))


if __name__ == "__main__":
    main()
