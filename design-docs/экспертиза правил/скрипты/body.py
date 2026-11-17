# -*- coding: utf-8 -*-
"""Текст страниц по колонкам: python body.py <имя папки образца или файл.pdf> <первая> <последняя> [мин. кегль]"""
import sys,re,pymupdf
sys.stdout.reconfigure(encoding='utf-8')
import os, json
def _pdf(arg):
    if arg.lower().endswith('.pdf'):
        return arg
    R = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "образцы", "рулбуки")
    f = json.load(open(os.path.join(R, arg, 'meta.json'), encoding='utf-8'))['file']
    for d in os.environ.get("RULEBOOKS_DIR", "").split(os.pathsep):
        if d and os.path.exists(os.path.join(d, f)):
            return os.path.join(d, f)
    raise SystemExit('нет PDF для ' + arg)
doc=pymupdf.open(_pdf(sys.argv[1])); a,b=int(sys.argv[2]),int(sys.argv[3])
lo=float(sys.argv[4]) if len(sys.argv)>4 else 8.5
for pno in range(a-1,b):
    page=doc[pno]; H=page.rect.height; W=page.rect.width
    print(f'===== СТР {pno+1} =====')
    blocks=[]
    for bl in page.get_text('dict')['blocks']:
        if bl['type']!=0: continue
        txt=''; sizes=[]
        for l in bl['lines']:
            s=''.join(sp['text'] for sp in l['spans'])
            for sp in l['spans']: sizes.append((len(sp['text']),sp['size']))
            if txt.endswith('-') or txt.endswith('­'): txt=txt[:-1]+s
            else: txt+=(' ' if txt else '')+s
        if not txt.strip(): continue
        tot=sum(n for n,_ in sizes) or 1
        sz=max(set(s for _,s in sizes), key=lambda z: sum(n for n,s in sizes if s==z))
        if sz<lo: continue
        x0,y0=bl['bbox'][0],bl['bbox'][1]
        col=0 if x0<W*0.47 else 1
        blocks.append((col,round(y0),x0,sz,re.sub(r'\s+',' ',txt).strip()))
    for col,y,x,sz,txt in sorted(blocks):
        tag='##' if sz>=12 else '  '
        print(f'{tag}[{col}|{sz:.0f}] {txt}')
