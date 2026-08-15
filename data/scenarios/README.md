# Scenarios — versioned field layouts

Each scenario is a versioned data file describing ONE modular board assembly for
a given player count, transcribed from the designer's field-layout images.

## Формат записи (компактный: shape + special)

Раскладка задаётся формой рядов и списком особых гексов; всё прочее внутри
обводки — обычное «Поле». Ряды считаются сверху вниз, pointy-top: НЕЧЁТНЫЕ
ряды смещены на пол-гекса вправо (offset «even-r»; чётность подтверждена
коррекцией дизайнера по 2p v1). Дизайнер диктует особые гексы как
«ряд-номер гекса В РЯДУ» — в YAML колонка = отступ ряда + номер.

```yaml
scenarios:
  - id: field_4p_v1
    shape:
      - 3                     # ряд 1: 3 гекса, с колонки 1 (старый формат)
      - {offset: 1, count: 4} # ряд 2: 4 гекса, СДВИГ на 1 — колонки 2..5
      - {offset: 0, count: 5} # ряд 3: колонки 1..5
    special:
      - {row: 2, col: 5, content: player_start, seat: 1}
      # col — АБСОЛЮТНАЯ колонка общей сетки (не от начала ряда!)
```

`offset` — отступ ряда от левого края общей сетки (в целых гексах). Ряд с
`{offset: N, count: M}` занимает колонки `N+1..N+M`. Колонки в `special`
всегда абсолютные. Особый гекс вне формы = ошибка загрузки (раньше молча
выпадал — так терялись старты игроков).

Загрузчик (`Scenario.expandedHexes`) разворачивает это в осевые координаты
`q = col0 - (row0 + (row0 & 1)) / 2, r = row0` (0-базовые), id гекса `h<q>_<r>`.

Секция `neutrals:` — нейтральные здания ОТДЕЛЬНЫМ слоем поверх любого гекса
(в том числе с контейнером/грядкой): `{row, col, size: small|big, corners: [5,6]}`.
`corners` — углы гекса, которых касается здание (1 = север, по часовой 1..6);
одинарное = 2 угла (одно ребро), двойное = 3 угла (два ребра).
Соседство выводится автоматически. Опциональный `blocked_edges` — стены/ворота
на конкретных рёбрах (в осевых координатах).

Старый формат с явным списком `hexes:` (осевые q,r) тоже поддерживается.

## Hex `content` vocabulary (matches the board art)

| symbol on art | content code | meaning |
|---|---|---|
| green cube `◇` (plain) | `kelium_tile` | central kelium spawn tile |
| green cube with `S` | `spawn_start` | start-origin spawn tile (near a player) |
| green cube `+1 / -1 / x2` | `kelium_tile` w/ `modifier` | kelium yield modifier |
| brown box `?` | `container` (count 1) | one container |
| brown box `? x2` | `container` (count 2) | two containers |
| radar + N | `player_start` w/ `seat: N` | player N's start hex (CU here) |
| dark / empty hex inside border | `forbidden` | hole in the board (blocks) |
| grey/white bracket on an edge | `blocked_edges` entry | wall/gate on that border |

## Verification workflow

Claude decodes each image into one of these files, then the designer verifies
the transcription against the art before it is used. Fields marked
`_needs_verification: true` have NOT yet been confirmed by the designer.

## Files

- `scenario_2p.<version>.yaml` — 2-player layout
- `scenario_3p.<version>.yaml` — 3-player layout
- `scenario_4p.<version>.yaml` — 4-player layout

(The image set contains several variants per count; additional variants get
their own version suffix, e.g. `scenario_4p.v2.yaml`.)
