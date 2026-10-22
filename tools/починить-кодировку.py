# -*- coding: utf-8 -*-
"""Чинит текст, выжатый из PDF со сломанной таблицей ToUnicode.

В таких файлах часть букв съехала ровно на 29 позиций вверх по ASCII:
p→S, q→T, r→U, s→V, t→W, u→X, v→Y, w→Z, x→[, y→\\, z→].
Отсюда «Whe» вместо «the» и «\\oX» вместо «you».

Беда в том, что S…Z — это и настоящие заглавные. Поэтому правим только там,
где подмена очевидна: заглавная из диапазона стоит ВНУТРИ слова, после
строчной буквы («deVigneUV» → «designers»). Если в слове нашлась хоть одна
такая, слово чинится целиком — вместе с первой буквой («WhaW» → «that»).
Отдельно правим горстку самых частых слов, где сломана только первая буква
(«Whe», «Wo», «\\oX»): их ни с чем не спутать.
"""
import io
import re
import sys
import pathlib

СДВИГ = 29
ДИАПАЗОН = set('STUVWXYZ[\\]')
КАВЫЧКИ = {'³': '"', '´': '"', '¶': "'", '²': '—',
           '¨': '', '­': ''}
# Слова, где сломана только первая буква. Настоящих омонимов у них нет.
ОДИНОЧКИ = {
    'Whe': 'the', 'Wo': 'to', 'Who': 'tho', 'Zho': 'who', 'Zill': 'will',
    'Wheir': 'their', 'Whem': 'them', 'Where': 'there', 'Whese': 'these',
    'Whose': 'those', 'Whis': 'this', 'Whan': 'than', 'Wake': 'take',
    'Wime': 'time', 'Wype': 'type', 'Zith': 'with', 'Zell': 'well',
    'Zere': 'were', 'Zhen': 'when', 'Zhile': 'while', 'Zork': 'work',
    'Zant': 'want', 'Zay': 'way', 'Zays': 'ways', 'Uules': 'rules',
    'Uule': 'rule', 'Ume': 'ume', 'Sage': 'page', 'Sages': 'pages',
    'Slay': 'play', 'Slayer': 'player', 'Slayers': 'players',
    'Sart': 'part', 'Soint': 'point', 'Vome': 'some', 'Vuch': 'such',
    'Vame': 'same', 'Xse': 'use', 'Xp': 'up', 'Yery': 'very',
    'Tuite': 'quite', 'Tuestion': 'question',
}


def чинить_слово(слово: str) -> str:
    return ''.join(chr(ord(c) + СДВИГ) if c in ДИАПАЗОН else c for c in слово)


ВНУТРИ = re.compile(r'[a-z][STUVWXYZ\[\\\]]')
СЛОВО = re.compile(r"[A-Za-z\[\\\]]+")


def чинить(текст: str) -> str:
    for плохо, хорошо in КАВЫЧКИ.items():
        текст = текст.replace(плохо, хорошо)

    def замена(m):
        слово = m.group(0)
        if ВНУТРИ.search(слово):
            return чинить_слово(слово)
        return ОДИНОЧКИ.get(слово, ОДИНОЧКИ.get(
            слово.lower(), слово).capitalize() if слово.lower() in ОДИНОЧКИ
            else слово)

    return СЛОВО.sub(замена, текст)


if __name__ == '__main__':
    for имя in sys.argv[1:]:
        путь = pathlib.Path(имя)
        было = io.open(путь, encoding='utf-8').read()
        стало = чинить(было)
        io.open(путь, 'w', encoding='utf-8', newline='').write(стало)
        порча = len(re.findall(r'[a-z][STUVWXYZ\[\\\]]', стало))
        print(f'{путь.name}: осталось подозрительных мест {порча}')
