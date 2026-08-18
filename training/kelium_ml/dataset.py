"""Датасет поверх JSONL, который пишет TrainingDataExport.java."""

from __future__ import annotations

from pathlib import Path

import numpy as np
import torch
from torch.utils.data import Dataset

from .encode import encode_line

# Цель — margin (мои ПО минус ПО сильнейшего соперника), не сырые ПО: сеть
# должна учиться увеличивать ОТРЫВ от стола, а не просто копить очки себе
# (заказ дизайнера 18.08.2026 — "не только себе хорошо, но сопернику плохо").
MARGIN_SCALE = 15.0


class GamesDataset(Dataset):
    def __init__(self, jsonl_path: str | Path):
        self.lines: list[str] = []
        with open(jsonl_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    self.lines.append(line)

    def __len__(self) -> int:
        return len(self.lines)

    def __getitem__(self, idx: int):
        enc, margin = encode_line(self.lines[idx])
        target = max(-2.0, min(2.0, margin / MARGIN_SCALE))
        return (
            torch.from_numpy(enc.hex_x),
            torch.from_numpy(enc.hex_mask),
            torch.from_numpy(enc.seat_x),
            torch.from_numpy(enc.seat_mask),
            torch.from_numpy(enc.scalars),
            torch.tensor(target, dtype=torch.float32),
        )


def split_train_val(dataset: GamesDataset, val_fraction: float = 0.15, seed: int = 0):
    n = len(dataset)
    idx = np.arange(n)
    rng = np.random.default_rng(seed)
    rng.shuffle(idx)
    n_val = max(1, int(n * val_fraction))
    val_idx = idx[:n_val]
    train_idx = idx[n_val:]
    return (
        torch.utils.data.Subset(dataset, train_idx.tolist()),
        torch.utils.data.Subset(dataset, val_idx.tolist()),
    )
