"""Вставка главы в вёрстку (если передан фрагмент), печать PDF, разворот последних страниц.

python build.py [фрагмент.html]
"""
import glob
import os
import subprocess
import sys

import pymupdf
from PIL import Image

D = os.path.dirname(os.path.abspath(__file__))
SRC = glob.glob(r"C:\shared\forged-in-kelium\rules\Книга правил*\вёрстка\Книга правил.html")[0]
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"

t = open(SRC, encoding="utf-8").read()
if len(sys.argv) > 1:
    frag = open(os.path.join(D, sys.argv[1]), encoding="utf-8").read()
    marker = frag.strip().splitlines()[0]
    assert marker not in t, "фрагмент уже вставлен"
    j = t.rindex("</body>")
    t = t[:j] + frag + t[j:]
    open(SRC, "w", encoding="utf-8").write(t)

book = os.path.join(D, "book.html")
pdf = os.path.join(D, "book.pdf")
open(book, "w", encoding="utf-8").write(t)
if os.path.exists(pdf):
    os.remove(pdf)
subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                "--user-data-dir=" + os.path.join(D, "chr"), "--virtual-time-budget=10000",
                "--print-to-pdf=" + pdf, "file:///" + book.replace("\\", "/")], check=True)
doc = pymupdf.open(pdf)
print("страниц:", len(doc))
ims = []
for k in (len(doc) - 2, len(doc) - 1):
    p = os.path.join(D, f"last{k}.png")
    doc[k].get_pixmap(dpi=70).save(p)
    ims.append(Image.open(p))
S = Image.new("RGB", (ims[0].width * 2, ims[0].height))
S.paste(ims[0], (0, 0))
S.paste(ims[1], (ims[0].width, 0))
S.save(os.path.join(D, "sp.png"))
