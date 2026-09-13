# -*- coding: utf-8 -*-
"""Выжимает текст из книг и рулбуков в docs/настолки/выжимка/*.txt.

EPUB — это zip с XHTML внутри: читаем порядок из OPF spine, снимаем разметку.
PDF — текстовый слой через pymupdf; где слоя нет, пишем пометку: такие листы
придётся смотреть глазами, как картинки."""
import html
import io
import pathlib
import re
import sys
import zipfile

import pymupdf

sys.stdout.reconfigure(encoding='utf-8')
ИСТОЧНИКИ = pathlib.Path(r'C:\Users\backskin\Documents\bg rules')
КУДА = pathlib.Path(r'C:\shared\forged-in-kelium\docs\настолки\выжимка')
КУДА.mkdir(parents=True, exist_ok=True)


def чисто(s):
    s = re.sub(r'<(script|style)[^>]*>.*?</\1>', ' ', s, flags=re.S | re.I)
    s = re.sub(r'<(p|div|br|h[1-6]|li|tr)\b[^>]*>', '\n', s, flags=re.I)
    s = re.sub(r'<[^>]+>', '', s)
    s = html.unescape(s)
    s = re.sub(r'[ \t\xa0]+', ' ', s)
    return re.sub(r'\n\s*\n\s*\n+', '\n\n', s).strip()


def из_epub(путь):
    z = zipfile.ZipFile(путь)
    имена = z.namelist()
    opf = next((n for n in имена if n.lower().endswith('.opf')), None)
    порядок = []
    if opf:
        сырое = z.read(opf).decode('utf-8', 'ignore')
        корень = opf.rsplit('/', 1)[0] + '/' if '/' in opf else ''
        ид = dict(re.findall(r'<item\b[^>]*id="([^"]+)"[^>]*href="([^"]+)"', сырое))
        ид.update({i: h for h, i in re.findall(
            r'<item\b[^>]*href="([^"]+)"[^>]*id="([^"]+)"', сырое)})
        for ref in re.findall(r'<itemref\b[^>]*idref="([^"]+)"', сырое):
            if ref in ид:
                порядок.append(корень + ид[ref].split('#')[0])
    if not порядок:
        порядок = [n for n in имена if n.lower().endswith(('.xhtml', '.html'))]
    куски = []
    for имя in порядок:
        if имя in имена:
            куски.append(чисто(z.read(имя).decode('utf-8', 'ignore')))
    return '\n\n'.join(k for k in куски if k)


def из_pdf(путь):
    doc = pymupdf.open(путь)
    куски = []
    пусто = 0
    for i in range(doc.page_count):
        t = doc[i].get_text().strip()
        if not t:
            пусто += 1
            t = '[[БЕЗ ТЕКСТОВОГО СЛОЯ]]'
        куски.append(f'\n\n===== стр. {i + 1} =====\n{t}')
    doc.close()
    return ''.join(куски), пусто


if __name__ == '__main__':
    for путь in sorted(ИСТОЧНИКИ.rglob('*')):
        if путь.suffix.lower() not in ('.pdf', '.epub'):
            continue
        папка = путь.parent.name
        метка = ('' if папка == ИСТОЧНИКИ.name else папка + '—')
        цель = КУДА / (метка + путь.stem[:60] + '.txt')
        if путь.suffix.lower() == '.epub':
            текст, пусто = из_epub(путь), 0
        else:
            текст, пусто = из_pdf(путь)
        io.open(цель, 'w', encoding='utf-8').write(текст)
        print(f'{len(текст):8d} знаков  без слоя: {пусто:3d}  {цель.name}')
