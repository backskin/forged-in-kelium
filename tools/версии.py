# -*- coding: utf-8 -*-
"""КАКАЯ ВЕРСИЯ НАБОРА СЕЙЧАС В ИГРЕ.

Один ответ на всех, кто строит таблицы. Прежде каждый генератор держал версию
у себя в строковой константе, и книга заданий полгода собиралась из каталога
1.15.0, когда в игре давно стоял 1.17.0 — молча, без единой ошибки.

Цепочка та же, что у движка: GameConfig.DEFAULT_RULESET -> свод ->
content_versions -> файл каталога. Захардкоженной версии здесь нет ни одной.
"""
import io
import re

import yaml


def свод_по_умолчанию():
    """Версия свода, которую играет движок (GameConfig.DEFAULT_RULESET)."""
    т = io.open('engine/src/main/java/kelium/dataio/GameConfig.java',
                encoding='utf-8').read()
    m = re.search(r'DEFAULT_RULESET\s*=\s*[\s\S]{0,120}?"kelium\.ruleset",\s*"([\d.]+)"', т)
    if not m:
        raise SystemExit('не нашёл DEFAULT_RULESET в GameConfig.java')
    return m.group(1)


def версия(набор):
    """Версия набора карт, закреплённая в действующем своде."""
    свод = свод_по_умолчанию()
    d = yaml.safe_load(io.open(f'data/rulesets/{свод}.yaml', encoding='utf-8'))
    в = (d.get('content_versions') or {}).get(набор)
    if not в:
        raise SystemExit(f'в своде {свод} нет content_versions.{набор}')
    return свод, str(в)


def каталог(набор, ключ=None):
    """Прочитать закреплённый каталог. Возвращает (свод, версия, список карт)."""
    свод, в = версия(набор)
    путь = f'data/cards/{набор}.{в}.yaml'
    d = yaml.safe_load(io.open(путь, encoding='utf-8'))
    return свод, в, d[ключ or набор], путь


def семейства(путь):
    """Семейства заданий из шапки выгрузки: «Выработка: o04 o05 …».

    В самих картах поля family нет — оно живёт в классах, а в yaml попадает
    только комментарием шапки. Читаем оттуда, второй копии списка не заводим.
    """
    из_карты = {}
    порядок = []
    for строка in io.open(путь, encoding='utf-8'):
        if not строка.startswith('#'):
            if строка.strip() and not строка.startswith('#'):
                break
            continue
        m = re.match(r'#\s*([А-ЯЁ][а-яё]+):\s+((?:[a-zA-Z]\d+\s*)+)$', строка.strip())
        if m:
            имя = m.group(1)
            порядок.append(имя)
            for cid in m.group(2).split():
                из_карты[cid] = имя
    return из_карты, порядок
