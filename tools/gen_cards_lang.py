# -*- coding: utf-8 -*-
"""ЛИЦА ЗАДАНИЙ ЯЗЫКА КАРТ НА ШАБЛОНАХ ДИЗАЙНЕРА (02.10.2026).

Карты набора из бульона (задания 3.0.0 и далее) рисуются тем же генератором,
что и комплект пяти развилок (tools/gen_cards_from_blanks.py): пустой шаблон
дизайнера, иконки из «экспорт-иконки», разметка печатных карт.

Тексты условий собирает Java, поэтому сначала выгрузка:
    java -cp gui/target/kelium-runner.jar kelium.gui.ЧерновыеЛица 1.50.0 --для-генератора карты.yaml
затем:
    python tools/gen_cards_lang.py карты.yaml [папка_вывода]
По умолчанию лица кладутся в data/textures/card/objective|arsenal/<id>.png
(размером прежних лиц игры).

Ветка «Построить …» — иконка развилки и поверх неё знак стройки («все иконки-80»,
решение Влада 02.10.2026). Верх-реакция — на плашке боевого эффекта.
"""
import os
import sys

import yaml
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_cards_from_blanks as G  # noqa: E402

K = G.K
ROOT = G.ROOT
СТРОЙКА = 32            # «все иконки-32» — знак стройки (выгрузка 03.10.2026)


def иконка_ветки(код, сторона):
    """Иконка ветки награды; «построить» — развилка и знак стройки в углу."""
    if код in G.РАЗВИЛКА_ВЕТКИ:
        холст = Image.new('RGBA', (сторона, сторона), (0, 0, 0, 0))
        осн = G.вписать(G.икона(G.И[G.РАЗВИЛКА_ВЕТКИ[код]]), int(сторона * 0.82), int(сторона * 0.82))
        холст.alpha_composite(осн, (0, 0))
        знак = G.вписать(G.икона(СТРОЙКА), int(сторона * 0.56), int(сторона * 0.56))
        холст.alpha_composite(знак, (сторона - знак.width, сторона - знак.height))
        return холст
    return G.иконка_награды(код, сторона)


# ---------------------------------------------------------------------------
#  ВЕРХ: утиль карты → описание верха генератора
# ---------------------------------------------------------------------------
РЕАКЦИИ = {
    'ammo_on_attack': G.ЗАКРОМА, 'evacuate_on_attack': G.ЭВАКУАЦИЯ,
    'return_fire': G.ПОСЛЕДНИЙ, 'barrage': G.ОТВЕТНЫЙ, 'ricochet': G.РИКОШЕТ,
    'counterattack': G.КОНТРАТАКА,
    'withdraw': dict(слот='inf', реакция='Отход!',
                     верх=['В чужой Бой уведи один', 'свой жетон из атакуемого', 'гекса на один гекс']),
    'evacuate_trophy': dict(слот='inf', реакция='Эвакуация трофеев',
                            верх=['Твой уничтоженный жетон', 'уходит в твой запас,', 'а не атакующему']),
    'battle_first': dict(слот='inf', реакция='Бой перед боем!',
                         верх=['Разыграй свой Бой', 'перед чужим Боем']),
}
# крупная иконка верха по действию / эффекту
ИКОНКА_ДЕЙСТВИЯ = {'mining': 'mining', 'science': 'science', 'market': 'market',
                   'assembly': 'assembly', 'build': 'buildings', 'movement': 'movement',
                   'combat': 'combat', 'energy_swap': 'swap'}
ИКОНКА_ЭФФЕКТА = {'convert': 'trophy', 'shield': 'heart', 'heal_hex': 'heart',
                  'speed_boost': 'movement', 'deploy_units': 'troops', 'redeploy_hex': 'movement',
                  'spec_actions': 'spec', 'energy_or_modules': 'swap',
                  'exchange_science_or_market': 'science', 'gain': 'coin'}


def строки(текст, длина=26, макс=3):
    """Короткие строки для верха (по словам)."""
    слова, out, cur = текст.split(), [], ''
    for с in слова:
        if cur and len(cur) + 1 + len(с) > длина:
            out.append(cur)
            cur = с
        else:
            cur = (cur + ' ' + с).strip()
    if cur:
        out.append(cur)
    return out[:макс]


def как_в_книге(метка):
    """«СВОБОДНАЯ ДОБЫЧА» → «Свободная добыча»; уже строчные — как есть."""
    if метка.upper() != метка:
        return метка
    t = метка.lower()
    return t[:1].upper() + t[1:]


def верх_карты(верх):
    if not верх:
        return dict(слот='spec', иконка_верха='spec', верх=[''])
    эф = верх.get('effect')
    п = верх.get('params') or {}
    метка = str(верх.get('label', ''))
    if эф == 'reaction' and п.get('kind') in РЕАКЦИИ:
        return dict(РЕАКЦИИ[п['kind']])
    if эф == 'module_swap':
        return dict(G.МОДУЛИ)
    if метка.startswith('АТАКУЙ ДВАЖДЫ'):
        return dict(G.АТАКУЙ)
    if метка.startswith('2 max'):
        return dict(G.ВЕРХИ['2'])
    слот = 'inf' if п.get('free') else 'spec'
    if эф == 'free_action':
        икн = ИКОНКА_ДЕЙСТВИЯ.get(п.get('action'), 'spec')
    elif эф == 'gain':
        икн = 'ammo' if 'ammo' in п else 'coin'
    else:
        икн = ИКОНКА_ЭФФЕКТА.get(эф, 'spec')
    return dict(слот=слот, иконка_верха=икн, верх=строки(как_в_книге(метка)))


# ---------------------------------------------------------------------------
#  НАГРАДА: кольца «одно из двух» и добавки (спец-действия, монеты, трофеи…)
# ---------------------------------------------------------------------------
ДОБАВКИ = [('coin', 'coin'), ('ammo', 'ammo'), ('trophy', 'trophy'), ('kelium', 'kel'),
           ('arsenal', 'arsenal'), ('objective_card', 'arsenal'), ('spec_actions', 'spec')]


def добавки(награда):
    out = []
    for ключ, икн in ДОБАВКИ:
        v = (награда or {}).get(ключ)
        if isinstance(v, int) and v > 0:
            out.append((икн, v))
    return out


def нарисовать_награду(im, награда, сдвиг):
    """Кольца веток там же, где на печатных картах: два — 424 и 574 с чертой, одно — 500."""
    действие = (награда or {}).get('action')
    коды = str(действие).split('|') if действие else []
    места = (424, 574) if len(коды) == 2 else (500,)
    cy = 614 + сдвиг
    for код, cx in zip(коды, места):
        G.положить(im, G.вписать(G.икона(G.И['ring']), 110 * K, 110 * K), cx, cy)
        G.положить(im, иконка_ветки(код, 104 * K), cx, cy - 2)
    if len(коды) == 2:
        G.черта(im, сдвиг)


# ---------------------------------------------------------------------------
#  КАРТА
# ---------------------------------------------------------------------------
def лицо(к, номер):
    доп = к.get('дополнительно')
    пара = 2 + 2 * ((номер - 1) % 11)          # 11 иллюстраций: чётный — с «дополнительно»
    шаблон = пара if доп else пара + 1
    карта = dict(верх_карты(к.get('верх')), id=к['id'], номер=номер, шаблон=шаблон,
                 имя=к['имя'], семейство=к['имя'], условие=str(к.get('условие') or ''),
                 дополнительно=доп)
    im = G.шаблон_задания(шаблон)
    G.верх_задания(im, карта)
    d = ImageDraw.Draw(im)
    имя = карта['имя']
    р = 52
    f = G.шрифт(G.ШР_ЗАГ, р)
    while d.textlength(имя, font=f) > 470 * K and р > 30:
        р -= 1
        f = G.шрифт(G.ШР_ЗАГ, р)
    d.text((615 * K, 284 * K), имя, font=f, fill=G.БЕЛЫЙ, anchor='rm',
           stroke_width=4 * K, stroke_fill=G.КРАСНАЯ_ОБВ)
    G.плашки_и_текст(im, карта['условие'], 344, 53, 35)
    нарисовать_награду(im, к.get('награда'), 0 if доп else 127)
    if доп:
        # как у дизайнера: две строки; длинное — три-четыре строки мельче
        for строк, р, шаг, y in ((2, 33.4, 51, 767), (2, 30, 46, 767), (3, 29, 37, 765),
                                 (3, 26, 35, 765), (4, 25, 30, 762)):
            if G.перенести(доп, р, строк=строк) is not None:
                G.плашки_и_текст(im, доп, y, шаг, р, правый=626, строк=строк)
                break
        else:
            raise ValueError('«дополнительно» не влезает: ' + доп)
    # нижний ряд: награда за «дополнительно»; без него — добавки основной награды
    # (спец-действия, монеты), как на печатных картах без полосы
    ряд = добавки(к.get('доп_награда') if доп else к.get('награда'))
    ряд.sort(key=lambda x: ['coin', 'ammo', 'trophy', 'kel', 'arsenal', 'spec'].index(x[0]))
    карта['доп_награда'] = ряд
    return G.низ_задания(im, карта, доп)


# ---------------------------------------------------------------------------
#  АРСЕНАЛ: верх — утиль, низ — срабатывание установленной карты (шаблон ∞)
# ---------------------------------------------------------------------------
G.ИА.update({'mining': 36, 'ammo': 3, 'trophy': 9, 'movement': 38, 'combat': 39,
             'arsenal': 15, 'gild': 48, 'troops': 54,
             # нарисованы дизайнером под утили арсенала (выгрузка 03.10.2026)
             'grab_first': 86, 'kel_mining': 87, 'hire_two': 88, 'spec3': 89,
             'build_coin': 90, 'steal_arsenal': 91, 'discard_arsenal': 93,
             'steal_objective': 94, 'market_card': 95, 'market_one': 96, 'heal': 98})
ИКОНКА_УТИЛЯ = {'landing': 'hire_two', 'gild_module': 'gild', 'grab_first_player': 'grab_first',
                'three_spec_actions': 'spec3', 'steal_objective_cards': 'steal_objective',
                'steal_arsenal_card': 'steal_arsenal', 'discard_enemy_arsenal': 'discard_arsenal',
                'swap_order_card': 'order', 'market_card_from_discard': 'market_card',
                'heal_hex': 'heal'}
ДЕЙСТВИЕ_А = {'mining': 'mining', 'build': 'buildings', 'combat': 'combat',
              'assembly': 'assembly', 'movement': 'movement', 'market': 'market',
              'science': 'science', 'energy_swap': 'swap'}
# слова в тексте низа, при которых печать ставит иконку (как ОПЕР_НИЗ дизайнера)
ИКОНКИ_НИЗА = [('спец-действие', '{spec} спец-действие'), ('1 келемий', '1 {kel} келемий'),
               ('2 монеты', '2 {coin} монеты'), ('1 монету', '1 {coin} монету')]


def иконка_утиля(верх):
    эф, п = верх.get('effect'), верх.get('params') or {}
    if эф == 'free_action':
        # утили со своей иконкой дизайнера: «1 келемий + добыча», «здание за
        # 1 монету», «предложение Рынка за 1 келемий»
        if п.get('action') == 'mining' and п.get('kelium'):
            return 'kel_mining'
        if п.get('action') == 'build' and п.get('cost') is not None or 'за 1 монету' in str(верх.get('label')):
            return 'build_coin'
        if п.get('action') == 'market' and 'за 1' in str(верх.get('label')):
            return 'market_one'
        return ДЕЙСТВИЕ_А.get(п.get('action'), 'spec')
    if эф == 'gain':
        return 'ammo' if 'ammo' in п else ('kel' if 'kelium' in п else 'coin')
    return ИКОНКА_УТИЛЯ.get(эф, 'spec')


def низ_арсенала(текст):
    import re
    текст = re.sub(r'«([^»]+)»', lambda m: '**«%s»**' % m.group(1), текст)
    текст = re.sub(r'(любую ветку )(\w+)', lambda m: m.group(1) + '**%s**' % m.group(2), текст)
    for было, стало in ИКОНКИ_НИЗА:
        текст = текст.replace(было, стало, 1)
    return текст


def две_строки(текст):
    """Утиль арсенала — одна строка или две равные (шрифт подберёт генератор)."""
    слова = текст.split()
    if len(текст) <= 22 or len(слова) < 2:
        return [текст]
    i = min(range(1, len(слова)), key=lambda k: max(len(' '.join(слова[:k])), len(' '.join(слова[k:]))))
    return [' '.join(слова[:i]), ' '.join(слова[i:])]


def лицо_арсенала(к, номер):
    верх = к.get('верх') or {}
    метка = как_в_книге(str(верх.get('label', ''))).replace('⇨', '→')
    карта = dict(id=к['id'], номер=номер, имя=к['имя'], верх=две_строки(метка),
                 верх_иконки=[(иконка_утиля(верх), 722, 62, 110)],
                 низ=низ_арсенала(str(к.get('низ') or '')), р_низ=40)
    return G.арсенал(карта)


def сохранить(im, путь):
    """В папку игры — размером прежнего лица (игра режет по нему); иначе как есть."""
    if os.path.exists(путь):
        старое = Image.open(путь).size
        if старое != im.size:
            im = im.resize(старое, Image.LANCZOS)
    im.save(путь)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    данные = yaml.safe_load(open(sys.argv[1], encoding='utf-8'))
    if isinstance(данные, list):
        данные = {'задания': данные, 'арсенал': []}
    корень = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, 'data', 'textures', 'card')
    for колода, рисовать, папка in (('задания', лицо, 'objective'),
                                    ('арсенал', лицо_арсенала, 'arsenal')):
        выход = os.path.join(корень, папка)
        os.makedirs(выход, exist_ok=True)
        for i, к in enumerate(данные.get(колода) or []):
            try:
                сохранить(рисовать(к, i + 1), os.path.join(выход, к['id'] + '.png'))
            except Exception as e:                   # карта не влезла — сказать, не молчать
                print('не нарисовано', к['id'], e)
        print(колода, len(данные.get(колода) or []), '→', выход)


if __name__ == '__main__':
    main()
