# -*- coding: utf-8 -*-
"""
Генератор content/data.js для интерактивного справочника «Кристаллы Раздора».

Читает машинные данные симулятора (rulesets / boards / cards / scenarios) и
складывает их в один JS-файл с глобальным объектом KR_DATA. Справочник
открывается двойным кликом по index.html, поэтому fetch/JSON использовать
нельзя — данные приходят обычным <script>.

Запуск:  python tools/gen_content.py
"""
import io
import json
import os
import sys

import yaml

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)
ROOT = os.path.dirname(APP)
DATA = os.path.join(ROOT, "simulator", "data")

RULESET_ID = "1.5.0"


def load(*parts):
    path = os.path.join(DATA, *parts)
    with io.open(path, encoding="utf-8") as fh:
        return yaml.safe_load(fh)


def boards_by_id(boards):
    return {b["id"]: b for b in boards["boards"]}


def build():
    ruleset = load("rulesets", "%s.yaml" % RULESET_ID)
    cv = ruleset["content_versions"]
    boards = load("boards", "boards.%s.yaml" % cv["boards"])
    bid = boards_by_id(boards)

    cards = {
        "objectives": load("cards", "objectives.%s.yaml" % cv["objectives"]),
        "arsenal": load("cards", "arsenal.%s.yaml" % cv["arsenal"]),
        "containers": load("cards", "containers.%s.yaml" % cv["containers"]),
        "market": load("cards", "market.%s.yaml" % cv["market"]),
        "orders": load("cards", "orders.%s.yaml" % cv["orders"]),
        "super_objectives": load(
            "cards", "super_objectives.%s.yaml" % cv["super_objectives"]),
        "super_arsenal": load(
            "cards", "super_arsenal.%s.yaml" % cv["super_arsenal"]),
    }

    scenarios = {}
    for n in (2, 3, 4):
        doc = load("scenarios", "scenario_%dp.%s.yaml" % (n, cv["scenarios"]))
        scenarios[str(n)] = doc["scenarios"]

    out = {
        "versions": {
            "ruleset": ruleset["meta"]["id"],
            "boards": cv["boards"],
            "scenarios": cv["scenarios"],
            "objectives": cv["objectives"],
            "arsenal": cv["arsenal"],
            "containers": cv["containers"],
            "market": cv["market"],
            "orders": cv["orders"],
            "super_objectives": cv["super_objectives"],
            "super_arsenal": cv["super_arsenal"],
        },
        "ruleset": ruleset,
        "tokens": bid["tokens"],
        "troopSides": {
            b["side"]: b for b in boards["boards"] if b.get("kind") == "troop_side"
        },
        "storageSides": {
            b["side"]: b for b in boards["boards"] if b.get("kind") == "storage_side"
        },
        "techBoard": bid["tech_tracks"],
        "cards": cards,
        "scenarios": scenarios,
    }
    return out


def main():
    data = build()
    dest = os.path.join(APP, "content", "data.js")
    body = json.dumps(data, ensure_ascii=False, indent=1, sort_keys=False)
    header = (
        "/* СГЕНЕРИРОВАНО tools/gen_content.py — руками не править.\n"
        "   Источник: simulator/data/ (ruleset %s). */\n" % RULESET_ID
    )
    with io.open(dest, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(header)
        fh.write("window.KR_DATA = ")
        fh.write(body)
        fh.write(";\n")
    print("written: %s (%d bytes)" % (dest, os.path.getsize(dest)))


if __name__ == "__main__":
    sys.exit(main())
