"""
Сеть оценки позиции — PyTorch-версия ValueNet.java, только на богатом входе
(PublicView через encode.py), а не на 33 признаках StateFeatures.

ФИКСИРОВАННАЯ ЁМКОСТЬ, РУКАМИ РАСТИМ ПРИ ПЛАТО (заказ дизайнера 18.08.2026):
сеть нарочно маленькая для старта (HIDDEN ниже) — увеличивать вручную, когда
кривая обучения выходит на плато, а не заранее «на вырост».

ХАРАКТЕР КАК ВХОД, А НЕ ОТДЕЛЬНАЯ СЕТЬ. Разные стили ботов (упор в род войск,
рывок арсеналом, охота на ЦУ и т.д. — заказ дизайнера) — это разные
терминальные цели одной и той же позиции: хорошая позиция для «охотника на
ЦУ» не та же, что для «учёного». Вместо отдельной сети на каждый характер
сеть получает one-hot характера как часть входа — училась бы ОДНА сеть на
все стили сразу, дообучаясь под них по мере появления Fitness.Goal для
каждого нового характера. Пока характер в обучающих данных не размечен
(TrainingDataExport ещё не пишет его), этот вход всегда нулевой — заготовка,
не полноценный признак.
"""

from __future__ import annotations

import torch
from torch import nn

from .encode import HEX_FEATS, MAX_HEXES, MAX_OTHERS, SEAT_FEATS

N_SCALARS = 4
N_CHARACTERS = 8   # запас под будущие терминальные цели; см. docstring выше
HIDDEN = 64        # СТАРТОВАЯ ёмкость — расти вручную при плато, не заранее


class ValueNet(nn.Module):
    def __init__(self, hidden: int = HIDDEN):
        super().__init__()
        seat_in = (1 + MAX_OTHERS) * SEAT_FEATS
        hex_in = MAX_HEXES * HEX_FEATS
        total_in = seat_in + hex_in + N_SCALARS + N_CHARACTERS
        self.net = nn.Sequential(
            nn.Linear(total_in, hidden),
            nn.Tanh(),
            nn.Linear(hidden, hidden),
            nn.Tanh(),
            nn.Linear(hidden, 1),
        )

    def forward(self, hex_x, hex_mask, seat_x, seat_mask, scalars, character=None):
        b = hex_x.shape[0]
        hex_flat = (hex_x * hex_mask.unsqueeze(-1)).reshape(b, -1)
        seat_flat = (seat_x * seat_mask.unsqueeze(-1)).reshape(b, -1)
        if character is None:
            character = torch.zeros(b, N_CHARACTERS, device=hex_x.device)
        x = torch.cat([seat_flat, hex_flat, scalars, character], dim=1)
        return self.net(x).squeeze(-1)
