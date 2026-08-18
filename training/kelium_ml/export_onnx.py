"""
Экспорт обученной ValueNet в ONNX — мост в Java (onnxruntime), без второй
реализации архитектуры на другом языке.

Запуск:
    python -m kelium_ml.export_onnx --weights checkpoints/v1.pt --out model.onnx
"""

from __future__ import annotations

import argparse
import sys

import torch

# torch.onnx печатает emoji-индикаторы прогресса; консоль Windows по
# умолчанию в cp1251 и падает на них UnicodeEncodeError. Раз и навсегда,
# а не через PYTHONIOENCODING в каждом вызове.
if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

from .encode import HEX_FEATS, MAX_HEXES, MAX_OTHERS, SEAT_FEATS
from .model import N_CHARACTERS, N_SCALARS, ValueNet


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--weights", required=True)
    ap.add_argument("--out", default="model.onnx")
    ap.add_argument("--hidden", type=int, default=64)
    args = ap.parse_args()

    model = ValueNet(hidden=args.hidden)
    model.load_state_dict(torch.load(args.weights, map_location="cpu"))
    model.eval()

    hex_x = torch.zeros(1, MAX_HEXES, HEX_FEATS)
    hex_mask = torch.zeros(1, MAX_HEXES)
    seat_x = torch.zeros(1, 1 + MAX_OTHERS, SEAT_FEATS)
    seat_mask = torch.zeros(1, 1 + MAX_OTHERS)
    scalars = torch.zeros(1, N_SCALARS)
    character = torch.zeros(1, N_CHARACTERS)

    torch.onnx.export(
        model,
        (hex_x, hex_mask, seat_x, seat_mask, scalars, character),
        args.out,
        input_names=["hex_x", "hex_mask", "seat_x", "seat_mask", "scalars", "character"],
        output_names=["value"],
        dynamic_axes={
            "hex_x": {0: "batch"}, "hex_mask": {0: "batch"},
            "seat_x": {0: "batch"}, "seat_mask": {0: "batch"},
            "scalars": {0: "batch"}, "character": {0: "batch"},
            "value": {0: "batch"},
        },
        opset_version=17,
    )
    print(f"экспортировано -> {args.out}")


if __name__ == "__main__":
    main()
