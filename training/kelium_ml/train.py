"""
Обучение ValueNet на выгрузке TrainingDataExport.java.

Запуск:
    python -m kelium_ml.train --data ../data/training/games.jsonl --out checkpoints/v1.pt

Та же схема, что у ValueNet.java: обычная регрессия с учителем на исход
партии (устойчивая и проверяемая — ошибку меряют на партиях, которых сеть
не видела), не REINFORCE по ходам (тот путь уже пробовали и он проиграл).
"""

from __future__ import annotations

import argparse
from pathlib import Path

import torch
from torch.utils.data import DataLoader

from .dataset import GamesDataset, split_train_val
from .model import ValueNet


def run_epoch(model, loader, opt, device):
    model.train() if opt is not None else model.eval()
    total_loss = 0.0
    total_n = 0
    for hex_x, hex_mask, seat_x, seat_mask, scalars, target in loader:
        hex_x, hex_mask = hex_x.to(device), hex_mask.to(device)
        seat_x, seat_mask = seat_x.to(device), seat_mask.to(device)
        scalars, target = scalars.to(device), target.to(device)

        pred = model(hex_x, hex_mask, seat_x, seat_mask, scalars)
        loss = torch.nn.functional.mse_loss(pred, target)

        if opt is not None:
            opt.zero_grad()
            loss.backward()
            opt.step()

        total_loss += loss.item() * target.shape[0]
        total_n += target.shape[0]
    return total_loss / max(1, total_n)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True)
    ap.add_argument("--out", default="checkpoints/v1.pt")
    ap.add_argument("--epochs", type=int, default=30)
    ap.add_argument("--batch-size", type=int, default=64)
    ap.add_argument("--lr", type=float, default=1e-3)
    ap.add_argument("--hidden", type=int, default=64)
    args = ap.parse_args()

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    dataset = GamesDataset(args.data)
    print(f"примеров: {len(dataset)}")
    train_set, val_set = split_train_val(dataset)

    train_loader = DataLoader(train_set, batch_size=args.batch_size, shuffle=True)
    val_loader = DataLoader(val_set, batch_size=args.batch_size, shuffle=False)

    model = ValueNet(hidden=args.hidden).to(device)
    opt = torch.optim.Adam(model.parameters(), lr=args.lr)

    best_val = float("inf")
    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)

    for epoch in range(1, args.epochs + 1):
        train_loss = run_epoch(model, train_loader, opt, device)
        with torch.no_grad():
            val_loss = run_epoch(model, val_loader, None, device)
        marker = ""
        if val_loss < best_val:
            best_val = val_loss
            torch.save(model.state_dict(), out_path)
            marker = " (лучшая -> сохранено)"
        print(f"эпоха {epoch:3d}  train {train_loss:.4f}  val {val_loss:.4f}{marker}")

    print(f"готово. лучшая val-ошибка: {best_val:.4f}, веса: {out_path}")


if __name__ == "__main__":
    main()
