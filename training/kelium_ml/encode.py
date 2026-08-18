"""
Числовой кодировщик PublicView -> плоский вектор для PyTorch.

ПОЧЕМУ ТАК. Java-сторона (PublicView.java) уже отдаёт стол целиком: каждый
гекс со своими секторами, каждый жетон, каждый планшет. Это дерево JSON
разной формы (число гексов и жетонов зависит от раскладки), а сети нужен
вектор ФИКСИРОВАННОЙ длины. Здесь — единственное место, где это дерево
превращается в числа: если менять кодировку, менять только тут, а не в
трёх местах (обучение, экспорт ONNX, инференс в Java должны видеть один и
тот же порядок признаков).

Соседи в PublicView уже даны В ПОРЯДКЕ ХОДА ОТ МЕНЯ (me.order=0), поэтому
кодировщик тоже работает в этом порядке, а не по абсолютному номеру места
— это то же решение, что и в PublicView.java, и по той же причине: номер
места ничего не значит, значение имеет только очерёдность.
"""

from __future__ import annotations

import json
from dataclasses import dataclass

import numpy as np

MAX_HEXES = 50
MAX_OTHERS = 3          # до 4 игроков за столом = до 3 соседей
HEX_FEATS = 17
SEAT_FEATS = 24


def _clip(v: float, lo: float = -2.0, hi: float = 2.0) -> float:
    return max(lo, min(hi, v))


def _seat_order_map(view: dict) -> dict:
    """Абсолютный номер места -> порядок хода от меня (0=я). Сентинели вроде
    -1000 (край поля) или -1 (никто) в карте нет и остаются "чужими"."""
    m = {view["me"]["seat"]: 0}
    for st in view["others"]:
        m[st["seat"]] = st["order"]
    return m


def _hex_features(h: dict, order_of: dict, players: int) -> list[float]:
    f = [0.0] * HEX_FEATS
    f[0] = h.get("containerCell", -1) / 6.0
    f[1] = h.get("energyCell", -1) / 6.0
    tint = h.get("ownerTint", -1)
    f[2] = order_of.get(tint, -1) / max(1, players - 1)
    f[3] = 1.0 if h.get("ownerBuilt") else 0.0
    sides = h.get("sideOwner", [-1] * 6)
    for i in range(6):
        s = sides[i] if i < len(sides) else -1
        f[4 + i] = order_of.get(s, -1) / max(1, players - 1)
    spawn = h.get("spawn")
    if spawn:
        f[10] = 1.0
        f[11] = spawn.get("kelium", 0) / 4.0
        f[12] = spawn.get("stack", 0) / 2.0
        f[13] = 1.0 if spawn.get("flipped") else 0.0
    neutrals = h.get("neutrals", [])
    f[14] = len(neutrals) / 2.0
    f[15] = sum(n.get("hp", 0) for n in neutrals) / 4.0
    f[16] = sum(n.get("hpMax", 0) for n in neutrals) / 4.0
    return [_clip(x) for x in f]


def _seat_features(st: dict) -> list[float]:
    tech = st.get("tech", {}) or {}
    tech_sum = sum(tech.values())
    tech_peaks = sum(1 for v in tech.values() if v >= 3)
    vp_total = (st.get("vp", {}) or {}).get("total", 0)
    f = [
        st.get("coin", 0) / 15.0,
        st.get("kelium", 0) / 8.0,
        st.get("ammo", 0) / 8.0,
        st.get("debris", 0) / 10.0,
        st.get("keliumCap", 0) / 8.0,
        st.get("ammoCap", 0) / 8.0,
        st.get("debrisCap", 0) / 10.0,
        st.get("storeCap", 0) / 6.0,
        st.get("trophyTokens", 0) / 6.0,
        st.get("trophyPoints", 0) / 10.0,
        tech_sum / 12.0,
        tech_peaks / 3.0,
        st.get("redModules", 0) / 3.0,
        st.get("blueModules", 0) / 3.0,
        st.get("goldModules", 0) / 2.0,
        st.get("cuTokens", 0) / 2.0,
        1.0 if st.get("ownCuToken", True) else 0.0,
        vp_total / 30.0,
        st.get("superProgress", 0) / 5.0,
        1.0 if st.get("superComplete") else 0.0,
        st.get("containers", 0) / 4.0,
        len(st.get("arsenalInstalled", []) or []) / 5.0,
        len(st.get("orderPlayed", []) or []) / 3.0,
        len(st.get("storageTokens", []) or []) / 6.0,
    ]
    assert len(f) == SEAT_FEATS
    return [_clip(x) for x in f]


@dataclass
class Encoded:
    hex_x: np.ndarray       # [MAX_HEXES, HEX_FEATS]
    hex_mask: np.ndarray    # [MAX_HEXES]
    seat_x: np.ndarray      # [1 + MAX_OTHERS, SEAT_FEATS]
    seat_mask: np.ndarray   # [1 + MAX_OTHERS]
    scalars: np.ndarray     # [4] round, circle, players, active_is_me


def encode_view(view: dict) -> Encoded:
    players = view["players"]
    order_of = _seat_order_map(view)

    hex_x = np.zeros((MAX_HEXES, HEX_FEATS), dtype=np.float32)
    hex_mask = np.zeros(MAX_HEXES, dtype=np.float32)
    hexes = view.get("hexes", [])
    for i, h in enumerate(hexes[:MAX_HEXES]):
        hex_x[i] = _hex_features(h, order_of, players)
        hex_mask[i] = 1.0

    seat_x = np.zeros((1 + MAX_OTHERS, SEAT_FEATS), dtype=np.float32)
    seat_mask = np.zeros(1 + MAX_OTHERS, dtype=np.float32)
    seat_x[0] = _seat_features(view["me"])
    seat_mask[0] = 1.0
    for st in view["others"][:MAX_OTHERS]:
        seat_x[st["order"]] = _seat_features(st)
        seat_mask[st["order"]] = 1.0

    active = view.get("active")
    scalars = np.array([
        view.get("round", 0) / 12.0,
        view.get("circle", 0) / 6.0,
        players / 4.0,
        1.0 if active == view["me"]["seat"] else 0.0,
    ], dtype=np.float32)

    return Encoded(hex_x, hex_mask, seat_x, seat_mask, scalars)


def encode_line(line: str) -> tuple[Encoded, float]:
    """Одна строка JSONL из TrainingDataExport.java -> (вход, цель=margin)."""
    rec = json.loads(line)
    enc = encode_view(rec["view"])
    margin = float(rec["outcome"]["margin"])
    return enc, margin
