# -*- coding: utf-8 -*-
"""Склеивает выжимку, где каждое слово оказалось на своей строке.

Пустая строка считается границей абзаца. Всё остальное соединяется пробелами.
Использование: python склеить-epub.py <вход.txt> <выход.txt>
"""
import sys

src, dst = sys.argv[1], sys.argv[2]

with open(src, encoding="utf-8") as f:
    lines = f.read().split("\n")

paras = []
buf = []
for line in lines:
    s = line.strip()
    if not s:
        if buf:
            paras.append(" ".join(buf))
            buf = []
    else:
        buf.append(s)
if buf:
    paras.append(" ".join(buf))

# выбрасываем пустышки и колонцифры
out = [p for p in paras if p and not (p.isdigit() and len(p) < 4)]

with open(dst, "w", encoding="utf-8") as f:
    f.write("\n\n".join(out))

print("абзацев:", len(out))
