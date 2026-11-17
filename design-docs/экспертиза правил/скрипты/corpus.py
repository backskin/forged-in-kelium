# -*- coding: utf-8 -*-
"""Корпус русских рулбуков: предложения по страницам, поиск оборотов."""
import re, sys, os, json
sys.stdout.reconfigure(encoding='utf-8')
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "образцы", "рулбуки")
CORE = ['ankh', 'circadians-chaos-order', 'clash-of-cultures', 'cyclades', 'dune-imperium-uprising',
        'great-machine-rules', 'plotina', 'rising-sun', 'root', 'scythe']
EXTRA = ['blood-rage', 'civ-new-dawn', 'gaia-project', 'inis', 'sw-rebellion-learn', 'anno-1800-rules',
         'great-machine-appendix', 'civ-terra-incognita', 'kemet-tips', 'sw-rebellion-ref']
SHORT = {'ankh': 'Анх', 'circadians-chaos-order': 'Цирк', 'clash-of-cultures': 'Столк', 'cyclades': 'Кикл',
         'dune-imperium-uprising': 'Дюна', 'great-machine-rules': 'ГВМ', 'plotina': 'Плот', 'rising-sun': 'ВС',
         'root': 'Root', 'scythe': 'Серп', 'blood-rage': 'BR', 'civ-new-dawn': 'Цив', 'gaia-project': 'Гайя',
         'inis': 'Инис', 'sw-rebellion-learn': 'ВК', 'anno-1800-rules': 'Анно', 'great-machine-appendix': 'ГВМприл',
         'civ-terra-incognita': 'ЦивТИ', 'kemet-tips': 'КеметС', 'sw-rebellion-ref': 'ВКспр'}


def load(slug):
    p = os.path.join(R, slug, 'clean.txt')
    if os.path.exists(p):
        t = open(p, encoding='utf-8').read().replace('\u00ad', '')
    else:
        raw = open(os.path.join(R, slug, 'text.txt'), encoding='utf-8').read().replace('\u00ad', '')
        raw = re.sub(r'-\n(?=[\u0430-\u044f\u0451a-z])', '', raw)
        buf = []
        for l in raw.split('\n'):
            l = l.strip()
            if not l:
                continue
            if l.startswith('====='):
                buf.append(l)
                continue
            if buf and not buf[-1].startswith('=====') and (buf[-1][-1] in ',;' or (buf[-1][-1] not in '.!?:\u00bb' and l[:1].islower())):
                buf[-1] += ' ' + l
            else:
                buf.append(l)
        t = '\n'.join(buf)
    pages = re.split(r'===== СТР (\d+) =====', t)
    out = []  # (page, sentence)
    seen = set()
    for i in range(1, len(pages), 2):
        n = int(pages[i])
        for line in pages[i + 1].split('\n'):
            line = line.strip()
            if len(line) < 25 or line in seen:
                continue
            seen.add(line)
            for s in re.split(r'(?<=[.!?…»])\s+(?=[А-ЯЁ«"(\d])', line):
                s = s.strip()
                if len(s) >= 20 and re.search('[а-яё]{3}', s):
                    out.append((n, s))
    return out


_cache = {}


def corpus(slugs):
    res = {}
    for s in slugs:
        if s not in _cache:
            _cache[s] = load(s)
        res[s] = _cache[s]
    return res


def words(sents):
    return sum(len(re.findall(r'[А-Яа-яЁё]+', s)) for _, s in sents)


def find(pat, slugs, flags=0, start=False):
    rx = re.compile(pat, flags)
    c = corpus(slugs)
    res = {}
    for slug, sents in c.items():
        hits = []
        for n, s in sents:
            m = rx.match(s) if start else rx.search(s)
            if m:
                hits.append((n, s))
        res[slug] = hits
    return res


if __name__ == '__main__':
    mode = sys.argv[1]
    slugs = CORE if (len(sys.argv) < 4 or sys.argv[3] == 'core') else CORE + EXTRA
    if mode == 'size':
        for s, sents in corpus(CORE + EXTRA).items():
            print(SHORT[s], len(sents), words(sents))
    elif mode == 'count':
        # sys.argv[2] = файл со списком шаблонов (по одному в строке; '#' - заголовок группы)
        pats = [l.rstrip('\n') for l in open(sys.argv[2], encoding='utf-8') if l.strip()]
        print('оборот'.ljust(34), ' '.join(SHORT[s].rjust(5) for s in slugs), '  всего')
        for p in pats:
            if p.startswith('#'):
                print(p)
                continue
            r = find(p, slugs, re.I)
            row = [len(r[s]) for s in slugs]
            print(p[:34].ljust(34), ' '.join(str(x).rjust(5) for x in row), str(sum(row)).rjust(6))
    elif mode == 'show':
        pat = sys.argv[2]
        lim = int(sys.argv[4]) if len(sys.argv) > 4 else 3
        r = find(pat, slugs, re.I)
        for s in slugs:
            for n, sent in r[s][:lim]:
                print(f'[{SHORT[s]} с.{n}] {sent[:330]}')
