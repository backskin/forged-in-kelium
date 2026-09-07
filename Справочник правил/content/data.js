/* СГЕНЕРИРОВАНО tools/gen_content.py — руками не править.
   Источник: simulator/data/ (ruleset 1.5.0). */
window.KR_DATA = {
 "versions": {
  "ruleset": "1.5.0",
  "boards": "1.0.0",
  "scenarios": "1.0.0",
  "objectives": "1.5.0",
  "arsenal": "1.2.0",
  "containers": "1.0.0",
  "market": "1.0.0",
  "orders": "1.0.0",
  "super_objectives": "1.0.0",
  "super_arsenal": "1.0.0"
 },
 "ruleset": {
  "meta": {
   "id": "1.5.0",
   "based_on": "1.4.0 + каталог заданий 8.0 «с ценой» + СВОД-старт (2026-08-11)",
   "description": "Baseline ruleset. Disputed backlog items set to the doc's provisional choice and flagged with their backlog id.\n"
  },
  "setup": {
   "start_miner": false,
   "start_coins": [
    5,
    5,
    5,
    5
   ]
  },
  "economy": {
   "coins_per_vp": 5,
   "trophy_per_vp": 3,
   "kelium_per_vp": 2,
   "spawn_flip_start_vp": 1,
   "spawn_flip_normal_vp": 2,
   "buildings_per_vp": 4,
   "units_per_vp": 4,
   "kelium_value_coins": 5,
   "kelium_value_ue": 3,
   "trophy_value_coins_via_kelium": 3
  },
  "rounds": {
   "min": 6,
   "max": 7,
   "circles_per_round": 4,
   "order_hand_size": 5,
   "objective_hand_limit": 3,
   "blind_discard_choice": true
  },
  "asymmetry": {
   "mode": "A",
   "board_sides": null,
   "token_hp_bonus_all": 0,
   "token_overrides": null
  },
  "actions": {
   "spec_per_turn": 1,
   "coincidence_rule_enabled": true,
   "build": {
    "surcharge_coins": [
     0,
     1
    ],
    "demolish_refund_coins": 1,
    "move_building_repays_full_price": true,
    "cu_free_move_per_turn": 1
   },
   "combat": {
    "surcharge_model": "right_to_battle",
    "open_battle_surcharge_ammo": [
     0,
     1
    ],
    "primary_row_ammo_cost": 1,
    "secondary_row_ammo_cost": 2,
    "retaliation_enabled": true,
    "retaliation_is_free": true,
    "retaliation_to_retaliation": false
   },
   "movement": {
    "cost_model": "flat",
    "first_hex_free": true,
    "flat_ammo_per_extra_move": 1,
    "escalating_surcharge_ammo": [
     0,
     1,
     2,
     3
    ]
   },
   "empty_energy_slot_coin_cost": 1,
   "energy_swap": {
    "surcharge_coins": [
     0,
     1
    ]
   }
  },
  "combat_model": {
   "all_attacks_damage": 1,
   "damage_persists_until_refresh": true
  },
  "tech": {
   "tracks": 3,
   "steps_per_track": 4,
   "step_cost_trophy": [
    1,
    2,
    3,
    4
   ],
   "step_vp_cumulative": [
    1,
    1,
    2,
    3
   ],
   "step_capacity": [
    3,
    2,
    2,
    1
   ],
   "step1_prize": {
    "left": {
     "first": {
      "ammo": 2
     },
     "second": {
      "ammo": 1
     }
    },
    "middle": {
     "first": {
      "kelium": 2
     },
     "second": {
      "kelium": 1
     }
    },
    "right": {
     "first": {
      "coin": 4
     },
     "second": {
      "coin": 2
     }
    }
   },
   "science_one_step_per_track_per_action": true,
   "science_exchanges": [
    {
     "id": "trophy_to_coin",
     "give_trophy": [
      1,
      2
     ],
     "get_coin": [
      1,
      2
     ]
    },
    {
     "id": "move_module",
     "give_trophy": 1,
     "effect": "move_module"
    },
    {
     "id": "draw_arsenal",
     "give_trophy": 2,
     "effect": "draw2_keep1"
    },
    {
     "id": "gild_module",
     "give_trophy": 3,
     "effect": "gild_module"
    }
   ]
  },
  "market": {
   "cell_cost_kelium": 1,
   "base_exchanges": [
    {
     "id": "kelium_to_coin",
     "per_kelium_coin": 3
    },
    {
     "id": "kelium_to_ammo",
     "per_kelium_ammo": 2
    },
    {
     "id": "kelium_to_objective",
     "per_kelium_cards": 1
    },
    {
     "id": "kelium_to_energy",
     "effect": "place_on_energy_cell"
    }
   ]
  },
  "return_step": {
   "trophy_to_upgrade_exchange_enabled": false,
   "return_destroyed_tokens": true,
   "refill_objectives_to_limit": true
  },
  "contested_cards": {
   "energy_without_source_enabled": false,
   "effect_survives_round_enabled": false,
   "attack_first_initiative_enabled": false
  },
  "command_center": {
   "destruction_token_vp": 3,
   "own_token_vp_if_cu_never_destroyed": 3,
   "respawns": true,
   "returns_to_reserve": true,
   "military_win_on_second_cu_kill": true,
   "owner_compensation_containers": 2,
   "build_price_coins": 2
  },
  "building_compensation_containers": {
   "barracks": 1,
   "factory": 1,
   "airbase": 1,
   "miner_by_level": [
    1,
    0,
    1,
    0
   ],
   "power_station_by_level": [
    1,
    0,
    1,
    0
   ]
  },
  "content_versions": {
   "boards": "1.0.0",
   "scenarios": "1.0.0",
   "objectives": "1.5.0",
   "arsenal": "1.2.0",
   "containers": "1.0.0",
   "market": "1.0.0",
   "orders": "1.0.0",
   "super_objectives": "1.0.0",
   "super_arsenal": "1.0.0"
  },
  "super_objectives": {
   "enabled": true
  },
  "containers_storage": {
   "open_is_spec": true,
   "arsenal_cells": 3,
   "slots_per_free_cell": 2,
   "slots_on_open_card_with_slot": 1
  }
 },
 "tokens": {
  "id": "tokens",
  "kind": "token_stats",
  "units": {
   "infantry": {
    "hp": 1,
    "trophy": [
     1,
     1,
     1,
     1
    ],
    "count": null
   },
   "vehicle": {
    "hp": 2,
    "trophy": [
     1,
     1,
     2,
     2
    ]
   },
   "aircraft": {
    "hp": 1,
    "trophy": [
     1,
     2,
     2,
     2
    ]
   },
   "tower": {
    "hp": 2,
    "trophy": [
     1,
     1,
     2,
     2
    ]
   }
  },
  "unit_tokens_per_color": 16,
  "building_tokens_per_color": 12,
  "buildings": {
   "barracks": {
    "energy_slots": 1,
    "hp": 1,
    "assembly": [
     "infantry",
     "ammo"
    ],
    "trophy": 2
   },
   "factory": {
    "energy_slots": 2,
    "hp": 2,
    "assembly": [
     "vehicle",
     "ammo"
    ],
    "trophy": 3
   },
   "airbase": {
    "energy_slots": 3,
    "hp": 3,
    "assembly": [
     "aircraft",
     "ammo"
    ],
    "trophy": 4
   },
   "command_center": {
    "energy_needed": 2,
    "energy_gives": 2,
    "energy_slots": 1,
    "hp": 3,
    "assembly": [
     "tower",
     "ammo"
    ],
    "trophy": 0
   }
  },
  "miners": [
   {
    "level": 1,
    "energy_slots": 2,
    "hp": 1,
    "yield_kelium": 1,
    "cost": 1,
    "trophy": 1
   },
   {
    "level": 2,
    "energy_slots": 1,
    "hp": 1,
    "yield_kelium": 1,
    "cost": 2,
    "trophy": 1
   },
   {
    "level": 3,
    "energy_slots": 2,
    "hp": 2,
    "yield_kelium": 2,
    "cost": 3,
    "trophy": 2
   },
   {
    "level": 4,
    "energy_slots": 1,
    "hp": 2,
    "yield_kelium": 2,
    "cost": 4,
    "trophy": 3,
    "vp_on_field": true
   }
  ],
  "power_plants": [
   {
    "level": 1,
    "hp": 1,
    "energy_gives": 1,
    "cost": 1,
    "trophy": 1
   },
   {
    "level": 2,
    "hp": 1,
    "energy_gives": 2,
    "cost": 2,
    "trophy": 1
   },
   {
    "level": 3,
    "hp": 2,
    "energy_gives": 2,
    "cost": 3,
    "trophy": 2
   },
   {
    "level": 4,
    "hp": 2,
    "energy_gives": 3,
    "cost": 4,
    "trophy": 3,
    "vp_on_field": true
   }
  ],
  "energy_invariant": {
   "sources": 10,
   "consumer_slots": 13,
   "deficit": 3
  },
  "modules": {
   "red_per_player": 4,
   "blue_per_player": 4
  }
 },
 "troopSides": {
  "A": {
   "id": "troop_A",
   "kind": "troop_side",
   "side": "A",
   "shared": true,
   "attacks": {
    "infantry": [
     "infantry",
     "vehicle"
    ],
    "vehicle": [
     "buildings_towers",
     "infantry"
    ],
    "aircraft": [
     "aircraft",
     "buildings_towers"
    ],
    "tower": [
     "vehicle",
     "aircraft"
    ]
   },
   "speeds": {
    "infantry": 1,
    "vehicle": 1,
    "aircraft": 2,
    "tower": 0
   },
   "building_prices": {
    "barracks": 1,
    "factory": 3,
    "airbase": 2
   },
   "tower_hits_buildings": false
  },
  "B1": {
   "id": "troop_B1",
   "kind": "troop_side",
   "side": "B1",
   "name": "Авиакрыло",
   "pillar": "aircraft",
   "counters": "B2",
   "tower_target": "vehicle",
   "attacks": {
    "infantry": [
     "aircraft",
     "vehicle"
    ],
    "vehicle": [
     "buildings_towers",
     "infantry"
    ],
    "aircraft": [
     "infantry",
     "buildings_towers"
    ],
    "tower": [
     "vehicle",
     "aircraft"
    ]
   },
   "building_prices": {
    "barracks": 2,
    "factory": 3,
    "airbase": 1
   },
   "speeds": {
    "infantry": 1,
    "vehicle": 1,
    "aircraft": 2,
    "tower": 0
   }
  },
  "B2": {
   "id": "troop_B2",
   "kind": "troop_side",
   "side": "B2",
   "name": "Старая школа",
   "pillar": "vehicle",
   "counters": "B4",
   "tower_target": "buildings_towers",
   "mirror_side": true,
   "tower_hits_buildings": true,
   "attacks": {
    "infantry": [
     "infantry",
     "vehicle"
    ],
    "vehicle": [
     "vehicle",
     "aircraft"
    ],
    "aircraft": [
     "aircraft",
     "buildings_towers"
    ],
    "tower": [
     "buildings_towers",
     "infantry"
    ]
   },
   "building_prices": {
    "barracks": 2,
    "factory": 1,
    "airbase": 3
   },
   "speeds": {
    "infantry": 1,
    "vehicle": 2,
    "aircraft": 1,
    "tower": 0
   }
  },
  "B3": {
   "id": "troop_B3",
   "kind": "troop_side",
   "side": "B3",
   "name": "Лёгкий десант",
   "pillar": "infantry",
   "counters": "B1",
   "tower_target": "aircraft",
   "attacks": {
    "infantry": [
     "vehicle",
     "aircraft"
    ],
    "vehicle": [
     "infantry",
     "buildings_towers"
    ],
    "aircraft": [
     "buildings_towers",
     "vehicle"
    ],
    "tower": [
     "aircraft",
     "infantry"
    ]
   },
   "building_prices": {
    "barracks": 1,
    "factory": 3,
    "airbase": 2
   },
   "speeds": {
    "infantry": 2,
    "vehicle": 1,
    "aircraft": 1,
    "tower": 0
   }
  },
  "B4": {
   "id": "troop_B4",
   "kind": "troop_side",
   "side": "B4",
   "name": "Инженерный корпус",
   "pillar": "tower",
   "counters": "B3",
   "tower_target": "infantry",
   "towers_move": true,
   "attacks": {
    "infantry": [
     "vehicle",
     "buildings_towers"
    ],
    "vehicle": [
     "aircraft",
     "infantry"
    ],
    "aircraft": [
     "buildings_towers",
     "vehicle"
    ],
    "tower": [
     "infantry",
     "aircraft"
    ]
   },
   "building_prices": {
    "barracks": 1,
    "factory": 2,
    "airbase": 3
   },
   "speeds": {
    "infantry": 1,
    "vehicle": 1,
    "aircraft": 1,
    "tower": 1
   }
  }
 },
 "storageSides": {
  "A": {
   "id": "storage_A",
   "kind": "storage_side",
   "side": "A",
   "shared": true,
   "miners": [
    "U",
    "U",
    "UU",
    "UU"
   ],
   "plants": [
    "U",
    "U",
    "UU",
    "UU"
   ],
   "prices": [
    1,
    2,
    3,
    4
   ],
   "storage_token": {
    "sides": [
     "+1_energy",
     "+1_universal_cell"
    ]
   },
   "vp_star_level": 4
  },
  "B1": {
   "id": "storage_B1",
   "kind": "storage_side",
   "side": "B1",
   "miners": [
    "KK",
    "KK",
    "U",
    "U"
   ],
   "plants": [
    "AA",
    "AA",
    "U",
    "U"
   ],
   "prices": [
    1,
    2,
    3,
    4
   ],
   "vp_star_level": 4
  },
  "B2": {
   "id": "storage_B2",
   "kind": "storage_side",
   "side": "B2",
   "miners": [
    "U",
    "U",
    "UU",
    "UU"
   ],
   "plants": [
    "AA",
    "U",
    "UU",
    "U"
   ],
   "prices": [
    2,
    2,
    3,
    3
   ],
   "vp_star_level": 4
  },
  "B3": {
   "id": "storage_B3",
   "kind": "storage_side",
   "side": "B3",
   "miners": [
    "",
    "KK",
    "KK",
    "UU"
   ],
   "plants": [
    "",
    "AA",
    "AA",
    "UU"
   ],
   "prices": [
    1,
    1,
    4,
    4
   ],
   "vp_star_level": 4
  },
  "B4": {
   "id": "storage_B4",
   "kind": "storage_side",
   "side": "B4",
   "miners": [
    "KU",
    "KU",
    "KU",
    "KU"
   ],
   "plants": [
    "A",
    "A",
    "A",
    "A"
   ],
   "prices": [
    3,
    3,
    2,
    2
   ],
   "vp_star_level": 4
  }
 },
 "techBoard": {
  "id": "tech_tracks",
  "kind": "tech_board",
  "tracks": [
   {
    "id": "left",
    "modules": "red",
    "ability": "+1 ammo in combat"
   },
   {
    "id": "middle",
    "modules": "storage",
    "ability": "+1 kelium per Mining"
   },
   {
    "id": "right",
    "modules": "blue",
    "ability": "+1 extra move"
   }
  ],
  "step_rewards": [
   "prize_cube",
   "module",
   "module",
   "super_arsenal_card"
  ],
  "first_arriver_prizes": {
   "left": "1_ammo",
   "middle": "1_kelium",
   "right": "1_coin"
  },
  "science_dept_exchanges": [
   "1-2 trophy -> 1-2 coins",
   "1 trophy -> move 1 module",
   "2 trophy -> draw 2 arsenal keep 1",
   "3 trophy -> gild (upgrade) 1 module"
  ]
 },
 "cards": {
  "objectives": {
   "meta": {
    "id": "1.5.0",
    "type": "objectives"
   },
   "objectives": [
    {
     "id": "o01",
     "name": "Полный залп",
     "kind": "regular",
     "type": "incident",
     "requirement": {
      "predicate": "assembly_all_chose_ammo",
      "params": {
       "count": 2
      }
     },
     "enhanced": {
      "predicate": "assembly_all_chose_ammo",
      "params": {
       "count": 2,
       "include_types": [
        "factory",
        "airbase"
       ]
      }
     },
     "base_reward": {
      "coin": 1,
      "ammo": 2,
      "container": 2
     },
     "special_reward": {
      "trophy": 2,
      "objective_card": 1
     },
     "top": {
      "effect": "free_action",
      "params": {
       "action": "build"
      },
      "label": "ДЕСАНТ (аппрокс.: стройка)"
     }
    },
    {
     "id": "o02",
     "name": "Конвейер",
     "kind": "regular",
     "type": "incident",
     "requirement": {
      "predicate": "produced_units_ex_tower",
      "params": {
       "count": 2,
       "distinct_buildings": 2
      }
     },
     "enhanced": {
      "predicate": "produced_units_ex_tower",
      "params": {
       "count": 2,
       "distinct_buildings": 2,
       "building_types_any": [
        "airbase"
       ]
      }
     },
     "base_reward": {
      "coin": 1,
      "ammo": 3,
      "container": 1
     },
     "special_reward": {
      "trophy": 2,
      "objective_card": 1
     },
     "top": {
      "effect": "free_action",
      "params": {
       "action": "build"
      },
      "label": "ЛЬГОТНАЯ СТРОЙКА"
     }
    },
    {
     "id": "o03",
     "name": "Опорный пункт",
     "kind": "regular",
     "type": "incident",
     "requirement": {
      "predicate": "tower_placed_off_cu",
      "params": {}
     },
     "enhanced": {
      "predicate": "tower_placed_off_cu",
      "params": {
       "borders_enemy": true
      }
     },
     "base_reward": {
      "coin": 1,
      "ammo": 3,
      "container": 1
     },
     "special_reward": {
      "module": "attack",
      "trophy": 1,
      "objective_card": 1
     },
     "top": {
      "effect": "gain",
      "params": {
       "coin": 1
      },
      "label": "МАРОДЁРЫ (аппрокс.: +1 монета)"
     }
    },
    {
     "id": "o04",
     "name": "Жила",
     "kind": "regular",
     "type": "state",
     "requirement": {
      "predicate": "powered_miners_distinct_spawns",
      "params": {
       "count": 2
      }
     },
     "enhanced": {
      "predicate": "powered_miners_distinct_spawns",
      "params": {
       "count": 2,
       "nonstart": 1
      }
     },
     "base_reward": {
      "coin": 1,
      "ammo": 2,
      "container": 2
     },
     "special_reward": {
      "kelium": 1,
      "objective_card": 1
     },
     "top": {
      "effect": "move_unit",
      "params": {
       "hexes": 1
      },
      "label": "ФОРСАЖ: войско на 1 гекс дальше"
     }
    },
    {
     "id": "o05",
     "name": "Выработка",
     "kind": "regular",
     "type": "incident",
     "requirement": {
      "predicate": "last_kelium_nonstart",
      "params": {}
     },
     "enhanced": {
      "predicate": "last_kelium_nonstart",
      "params": {
       "claimed": true
      }
     },
     "base_reward": {
      "coin": 1,
      "ammo": 3,
      "container": 1
     },
     "special_reward": {
      "arsenal": 1,
      "objective_card": 1
     },
     "top": {
      "effect": "free_action",
      "params": {
       "action": "science"
      },
      "label": "МАЛАЯ НАУКА"
     }
    },
    {
     "id": "o06",
     "name": "Отзыв",
     "kind": "regular",
     "type": "sacrifice",
     "sacrifice": {
      "resource": "units_off_base",
      "amount": 2
     },
     "requirement": {
      "predicate": "sacrifice_paid",
      "params": {}
     },
     "enhanced": {
      "predicate": "sacrifice_enhanced",
      "params": {
       "resource": "units_off_base",
       "amount": 3
      }
     },
     "base_reward": {
      "coin": 1,
      "ammo": 4
     },
     "special_reward": {
      "trophy": 2,
      "objective_card": 1
     },
     "top": {
      "effect": "free_action",
      "params": {
       "action": "market"
      },
      "label": "РАЗОВАЯ СДЕЛКА"
     }
    },
    {
     "id": "o07",
     "name": "Засада",
     "kind": "regular",
     "type": "state",
     "requirement": {
      "predicate": "hidden_unit_near_enemy",
      "params": {
       "count": 1
      }
     },
     "enhanced": {
      "predicate": "hidden_unit_near_enemy",
      "params": {
       "count": 2
      }
     },
     "base_reward": {
      "coin": 1,
      "ammo": 3,
      "container": 1
     },
     "special_reward": {
      "arsenal": 1,
      "objective_card": 1
     },
     "top": {
      "effect": "free_action",
      "params": {
       "action": "combat"
      },
      "label": "ОДИНОЧНЫЙ ВЫСТРЕЛ"
     }
    },
    {
     "id": "o08",
     "name": "Разведка недр",
     "kind": "regular",
     "type": "incident",
     "requirement": {
      "predicate": "miner_took_container",
      "params": {}
     },
     "enhanced": {
      "predicate": "miner_took_container",
      "params": {
       "levels": [
        3,
        4
       ]
      }
     },
     "base_reward": {
      "coin": 1,
      "ammo": 3,
      "container": 1
     },
     "special_reward": {
      "module": "assembly",
      "objective_card": 1
     },
     "top": {
      "effect": "gain",
      "params": {
       "coin": 1
      },
      "label": "ЭКОНОМИЯ КАБЕЛЯ: +1 монета"
     }
    },
    {
     "id": "o09",
     "name": "Всеобщая мобилизация",
     "kind": "regular",
     "type": "incident",
     "requirement": {
      "predicate": "produced_units_ex_tower",
      "params": {
       "count": 2,
       "no_ammo": true
      }
     },
     "enhanced": {
      "predicate": "produced_units_ex_tower",
      "params": {
       "count": 3,
       "no_ammo": true
      }
     },
     "base_reward": {
      "coin": 1,
      "ammo": 3,
      "container": 1
     },
     "special_reward": {
      "module": "assembly",
      "objective_card": 1
     },
     "top": {
      "effect": "gain",
      "params": {
       "coin": 2
      },
      "label": "СТРАХОВКА (аппрокс.: +2 монеты)"
     }
    },
    {
     "id": "o10",
     "name": "Форсаж производства",
     "kind": "regular",
     "type": "sacrifice",
     "sacrifice": {
      "resource": "kelium",
      "amount": 1
     },
     "requirement": {
      "predicate": "sacrifice_paid",
      "params": {}
     },
     "enhanced": {
      "predicate": "sacrifice_enhanced",
      "params": {
       "resource": "kelium",
       "amount": 2
      }
     },
     "base_reward": {
      "coin": 1,
      "ammo": 4
     },
     "special_reward": {
      "module": "assembly",
      "objective_card": 1
     },
     "top": {
      "effect": "free_action",
      "params": {
       "action": "energy_swap"
      },
      "label": "ПЕРЕКОММУТАЦИЯ"
     }
    },
    {
     "id": "o11",
     "name": "Передовая база",
     "kind": "regular",
     "type": "incident",
     "requirement": {
      "predicate": "built_bordering_enemy",
      "params": {
       "enemy": "units"
      }
     },
     "enhanced": {
      "predicate": "built_bordering_enemy",
      "params": {
       "enemy": "building"
      }
     },
     "base_reward": {
      "coin": 1,
      "ammo": 3,
      "container": 1
     },
     "special_reward": {
      "trophy": 2,
      "objective_card": 1
     },
     "top": {
      "effect": "move_unit",
      "params": {
       "hexes": 1
      },
      "label": "ОДИНОЧНЫЙ МАРШ"
     }
    },
    {
     "id": "o12",
     "name": "Наглая стройка",
     "kind": "regular",
     "type": "state",
     "requirement": {
      "predicate": "building_on_hex_with_enemy_units",
      "params": {
       "units": 1
      }
     },
     "enhanced": {
      "predicate": "building_on_hex_with_enemy_units",
      "params": {
       "units": 2
      }
     },
     "base_reward": {
      "coin": 1,
      "ammo": 4,
      "container": 1
     },
     "special_reward": {
      "module": "attack",
      "trophy": 1,
      "objective_card": 1
     },
     "top": {
      "effect": "heal_one",
      "params": {},
      "label": "ЩИТ — ПЕХОТА (аппрокс.: снять 1 урон)"
     }
    },
    {
     "id": "o13",
     "name": "Расчистка",
     "kind": "regular",
     "type": "incident",
     "requirement": {
      "predicate": "demolished_this_turn",
      "params": {}
     },
     "enhanced": {
      "predicate": "demolished_this_turn",
      "params": {
       "and_built": true
      }
     },
     "base_reward": {
      "coin": 1,
      "ammo": 3,
      "container": 1
     },
     "special_reward": {
      "storage_token": 1,
      "objective_card": 1
     },
     "top": {
      "effect": "free_action",
      "params": {
       "action": "mining"
      },
      "label": "ОДИНОЧНАЯ ДОБЫЧА"
     }
    },
    {
     "id": "o14",
     "name": "Осадный лагерь",
     "kind": "regular",
     "type": "state",
     "cull": "[4]",
     "requirement": {
      "predicate": "buildings_ring_around_hex",
      "params": {
       "count": 2,
       "enemy_building_on_center": true
      }
     },
     "enhanced": {
      "predicate": "buildings_ring_around_hex",
      "params": {
       "count": 3,
       "enemy_building_on_center": true
      }
     },
     "base_reward": {
      "coin": 1,
      "ammo": 3,
      "container": 1
     },
     "special_reward": {
      "module": "attack",
      "trophy": 2,
      "objective_card": 1
     },
     "top": {
      "effect": "heal_one",
      "params": {},
      "label": "ЩИТ — ТЕХНИКА (аппрокс.: снять 1 урон)"
     }
    },
    {
     "id": "o15",
     "name": "Стройбум",
     "kind": "regular",
     "type": "incident",
     "requirement": {
      "predicate": "build_ops_this_turn",
      "params": {
       "count": 2
      }
     },
     "enhanced": {
      "predicate": "build_ops_this_turn",
      "params": {
       "count": 3
      }
     },
     "base_reward": {
      "coin": 1,
      "ammo": 3,
      "container": 1
     },
     "special_reward": {
      "trophy": 2,
      "objective_card": 1
     },
     "top": {
      "effect": "free_action",
      "params": {
       "action": "combat"
      },
      "label": "ОДИНОЧНЫЙ ВЫСТРЕЛ"
     }
    },
    {
     "id": "o16",
     "name": "Переезд",
     "kind": "regular",
     "type": "incident",
     "requirement": {
      "predicate": "moved_noncu_building",
      "params": {
       "count": 1
      }
     },
     "enhanced": {
      "predicate": "moved_noncu_building",
      "params": {
       "count": 2
      }
     },
     "base_reward": {
      "coin": 1,
      "ammo": 2,
      "container": 2
     },
     "special_reward": {
      "trophy": 2,
      "objective_card": 1
     },
     "top": {
      "effect": "gain",
      "params": {
       "kelium": 1
      },
      "label": "БОГАТАЯ ЖИЛА: +1 келемий"
     }
    },
    {
     "id": "o17",
     "name": "Штаб на передовой",
     "kind": "regular",
     "type": "incident",
     "requirement": {
      "predicate": "moved_cu_to_virgin_hex",
      "params": {}
     },
     "enhanced": {
      "predicate": "moved_cu_to_virgin_hex",
      "params": {
       "borders_enemy": true
      }
     },
     "base_reward": {
      "coin": 1,
      "ammo": 3,
      "container": 1
     },
     "special_reward": {
      "trophy": 2,
      "objective_card": 1
     },
     "top": {
      "effect": "heal_one",
      "params": {},
      "label": "ПОЛЕВОЙ РЕМОНТ: снять 1 урон"
     }
    },
    {
     "id": "o18",
     "name": "Полная нагрузка",
     "kind": "regular",
     "type": "state",
     "requirement": {
      "predicate": "no_unpowered_buildings",
      "params": {
       "min_buildings": 1
      }
     },
     "enhanced": {
      "predicate": "no_unpowered_buildings",
      "params": {
       "min_buildings": 4
      }
     },
     "base_reward": {
      "coin": 1,
      "ammo": 2,
      "container": 1
     },
     "special_reward": {
      "trophy": 1,
      "objective_card": 1
     },
     "top": {
      "effect": "gain",
      "params": {
       "ammo": 1
      },
      "label": "ТРОФЕЙНАЯ КОМАНДА (аппрокс.: +1 БПР)"
     }
    },
    {
     "id": "o19",
     "name": "Военпром",
     "kind": "regular",
     "type": "state",
     "requirement": {
      "predicate": "military_buildings_of_distinct_types",
      "params": {
       "count": 2,
       "all_powered": true
      }
     },
     "enhanced": {
      "predicate": "military_buildings_of_distinct_types",
      "params": {
       "count": 3,
       "all_powered": true
      }
     },
     "base_reward": {
      "coin": 1,
      "ammo": 3,
      "container": 1
     },
     "special_reward": {
      "kelium": 1,
      "objective_card": 1
     },
     "top": {
      "effect": "place_damage",
      "params": {},
      "label": "КОНВЕРСИЯ (аппрокс.: 1 урон соседу)"
     }
    },
    {
     "id": "o20",
     "name": "Коммутация",
     "kind": "regular",
     "type": "incident",
     "requirement": {
      "predicate": "energy_swap_cross_hex",
      "params": {
       "sources": 2
      }
     },
     "enhanced": {
      "predicate": "energy_swap_cross_hex",
      "params": {
       "sources": 2,
       "and_built": true
      }
     },
     "base_reward": {
      "coin": 1,
      "ammo": 2,
      "container": 1
     },
     "special_reward": {
      "trophy": 1,
      "objective_card": 1
     },
     "top": {
      "effect": "gain",
      "params": {
       "ammo": 1
      },
      "label": "ЛИШНЯЯ ОБОЙМА: +1 боеприпас"
     }
    },
    {
     "id": "o21",
     "name": "Первая кровь",
     "kind": "regular",
     "type": "incident",
     "requirement": {
      "predicate": "destroyed_enemy_this_turn",
      "params": {
       "count": 1
      }
     },
     "enhanced": {
      "predicate": "destroyed_two_in_one_battle_this_turn",
      "params": {}
     },
     "base_reward": {
      "coin": 5,
      "container": 1
     },
     "special_reward": {
      "module": "attack",
      "objective_card": 1
     },
     "top": {
      "effect": "free_action",
      "params": {
       "action": "assembly"
      },
      "label": "МАЛАЯ СБОРКА"
     }
    },
    {
     "id": "o22",
     "name": "Зачистка",
     "kind": "regular",
     "type": "incident",
     "requirement": {
      "predicate": "destroyed_neutral_this_turn",
      "params": {
       "count": 1
      }
     },
     "enhanced": {
      "predicate": "destroyed_neutral_this_turn",
      "params": {
       "count": 1,
       "and_damaged_enemy": true
      }
     },
     "base_reward": {
      "coin": 4,
      "container": 1
     },
     "special_reward": {
      "arsenal": 1,
      "objective_card": 1
     },
     "top": {
      "effect": "free_action",
      "params": {
       "action": "science"
      },
      "label": "МАЛАЯ НАУКА"
     }
    },
    {
     "id": "o23",
     "name": "Подранки",
     "kind": "regular",
     "type": "incident",
     "requirement": {
      "predicate": "damaged_distinct_no_kills",
      "params": {
       "count": 2
      }
     },
     "enhanced": {
      "predicate": "damaged_distinct_no_kills",
      "params": {
       "count": 3
      }
     },
     "base_reward": {
      "coin": 4,
      "container": 1
     },
     "special_reward": {
      "trophy": 2,
      "objective_card": 1
     },
     "top": {
      "effect": "gain",
      "params": {
       "containers": 1
      },
      "label": "ПОДРЯД (аппрокс.: +1 контейнер)"
     }
    },
    {
     "id": "o24",
     "name": "Чистая работа",
     "kind": "regular",
     "type": "incident",
     "requirement": {
      "predicate": "destroyed_with_ammo_at_most",
      "params": {
       "ammo": 2
      }
     },
     "enhanced": {
      "predicate": "destroyed_with_ammo_at_most",
      "params": {
       "ammo": 1
      }
     },
     "base_reward": {
      "coin": 4,
      "container": 1
     },
     "special_reward": {
      "module": "attack",
      "objective_card": 1
     },
     "top": {
      "effect": "gain",
      "params": {
       "ammo": 1
      },
      "label": "ПЕРЕГРУЗ (аппрокс.: +1 БПР)"
     }
    },
    {
     "id": "o25",
     "name": "Осада",
     "kind": "regular",
     "type": "incident",
     "requirement": {
      "predicate": "enemy_building_hit_this_turn",
      "params": {
       "count": 1
      }
     },
     "enhanced": {
      "predicate": "enemy_building_hit_this_turn",
      "params": {
       "count": 2
      }
     },
     "base_reward": {
      "coin": 4,
      "container": 1
     },
     "special_reward": {
      "trophy": 2,
      "objective_card": 1
     },
     "top": {
      "effect": "free_action",
      "params": {
       "action": "assembly"
      },
      "label": "МАЛАЯ СБОРКА"
     }
    },
    {
     "id": "o26",
     "name": "Блицкриг",
     "kind": "regular",
     "type": "incident",
     "requirement": {
      "predicate": "moved_and_destroyed_same_unit",
      "params": {
       "kills": 1
      }
     },
     "enhanced": {
      "predicate": "moved_and_destroyed_same_unit",
      "params": {
       "kills": 2
      }
     },
     "base_reward": {
      "coin": 5,
      "container": 1
     },
     "special_reward": {
      "module": "attack",
      "trophy": 2,
      "objective_card": 1
     },
     "top": {
      "effect": "gain",
      "params": {
       "arsenal": 1
      },
      "label": "ДВОЙНАЯ ПОСТАВКА (аппрокс.: 1 арсенал)"
     }
    },
    {
     "id": "o27",
     "name": "На чужом дворе",
     "kind": "regular",
     "type": "state",
     "requirement": {
      "predicate": "unit_on_hex_with_enemy_units",
      "params": {
       "count": 1
      }
     },
     "enhanced": {
      "predicate": "unit_on_hex_with_enemy_units",
      "params": {
       "count": 2
      }
     },
     "base_reward": {
      "coin": 4,
      "container": 1
     },
     "special_reward": {
      "trophy": 2,
      "objective_card": 1
     },
     "top": {
      "effect": "free_action",
      "params": {
       "action": "assembly"
      },
      "label": "АВТОНОМНЫЙ ЗАПУСК (аппрокс.)"
     }
    },
    {
     "id": "o28",
     "name": "Клещи",
     "kind": "regular",
     "type": "state",
     "cull": "[4]",
     "requirement": {
      "predicate": "units_bordering_enemy_units_hex",
      "params": {
       "hexes": 2
      }
     },
     "enhanced": {
      "predicate": "units_bordering_enemy_units_hex",
      "params": {
       "hexes": 3
      }
     },
     "base_reward": {
      "coin": 4,
      "container": 1
     },
     "special_reward": {
      "trophy": 2,
      "objective_card": 1
     },
     "top": {
      "effect": "gain",
      "params": {
       "coin": 1
      },
      "label": "ТЕНЕВОЙ КУРЬЕР (аппрокс.: +1 монета)"
     }
    },
    {
     "id": "o29",
     "name": "Пустой двор",
     "kind": "regular",
     "type": "state",
     "requirement": {
      "predicate": "units_off_own_hexes",
      "params": {
       "count": 2
      }
     },
     "enhanced": {
      "predicate": "units_off_own_hexes",
      "params": {
       "count": 3
      }
     },
     "base_reward": {
      "coin": 4,
      "container": 1
     },
     "special_reward": {
      "trophy": 2,
      "objective_card": 1
     },
     "top": {
      "effect": "free_action",
      "params": {
       "action": "assembly"
      },
      "label": "СВЕРХУРОЧНЫЕ (аппрокс.: сборка)"
     }
    },
    {
     "id": "o30",
     "name": "Мародёр",
     "kind": "regular",
     "type": "incident",
     "requirement": {
      "predicate": "picked_container_by_unit",
      "params": {
       "count": 1
      }
     },
     "enhanced": {
      "predicate": "picked_container_by_unit",
      "params": {
       "count": 2
      }
     },
     "base_reward": {
      "coin": 4,
      "container": 1
     },
     "special_reward": {
      "trophy": 2,
      "objective_card": 1
     },
     "top": {
      "effect": "gain",
      "params": {
       "trophy": 1
      },
      "label": "ДВОЙНОЙ ЗАЧЁТ (аппрокс.: +1 ТРФ)"
     }
    },
    {
     "id": "o31",
     "name": "Господство в небе",
     "kind": "regular",
     "type": "state",
     "requirement": {
      "predicate": "aircraft_on_enemy_hex",
      "params": {}
     },
     "enhanced": {
      "predicate": "aircraft_on_enemy_hex",
      "params": {
       "cu_on_hex": true
      }
     },
     "base_reward": {
      "coin": 5,
      "container": 1
     },
     "special_reward": {
      "module": "attack",
      "trophy": 2,
      "objective_card": 1
     },
     "top": {
      "effect": "gain",
      "params": {
       "trophy": 1
      },
      "label": "ОТКАТ (аппрокс.: +1 ТРФ)"
     }
    },
    {
     "id": "o32",
     "name": "Разоружение",
     "kind": "regular",
     "type": "sacrifice",
     "sacrifice": {
      "resource": "ammo",
      "amount": 4
     },
     "requirement": {
      "predicate": "sacrifice_paid",
      "params": {}
     },
     "enhanced": {
      "predicate": "sacrifice_enhanced",
      "params": {
       "resource": "ammo",
       "amount": 6
      }
     },
     "base_reward": {
      "coin": 5
     },
     "special_reward": {
      "trophy": 2,
      "objective_card": 1
     },
     "top": {
      "effect": "free_action",
      "params": {
       "action": "mining"
      },
      "label": "ОДИНОЧНАЯ ДОБЫЧА"
     }
    },
    {
     "id": "o33",
     "name": "Биржа",
     "kind": "regular",
     "type": "incident",
     "cull": "[3+]",
     "requirement": {
      "predicate": "used_market_card_offer",
      "params": {}
     },
     "enhanced": {
      "predicate": "used_market_card_offer",
      "params": {
       "and_printed": true
      }
     },
     "base_reward": {
      "coin": 3,
      "ammo": 2
     },
     "special_reward": {
      "arsenal": 1,
      "objective_card": 1
     },
     "top": {
      "effect": "move_unit",
      "params": {
       "hexes": 1
      },
      "label": "ОДИНОЧНЫЙ МАРШ"
     }
    },
    {
     "id": "o34",
     "name": "Военный заём",
     "kind": "regular",
     "type": "sacrifice",
     "sacrifice": {
      "resource": "coin",
      "amount": 6
     },
     "requirement": {
      "predicate": "sacrifice_paid",
      "params": {}
     },
     "enhanced": {
      "predicate": "sacrifice_enhanced",
      "params": {
       "resource": "coin",
       "amount": 8
      }
     },
     "base_reward": {
      "coin": 1,
      "ammo": 4
     },
     "special_reward": {
      "module": "attack",
      "objective_card": 1
     },
     "top": {
      "effect": "free_action",
      "params": {
       "action": "assembly"
      },
      "label": "МАЛАЯ СБОРКА"
     }
    },
    {
     "id": "o35",
     "name": "Ставка на трек",
     "kind": "regular",
     "type": "sacrifice",
     "sacrifice": {
      "resource": "trophy",
      "amount": 2
     },
     "requirement": {
      "predicate": "sacrifice_paid",
      "params": {}
     },
     "enhanced": {
      "predicate": "sacrifice_enhanced",
      "params": {
       "resource": "trophy",
       "amount": 3
      }
     },
     "base_reward": {
      "coin": 5
     },
     "special_reward": {
      "kelium": 1,
      "objective_card": 1
     },
     "top": {
      "effect": "move_unit",
      "params": {
       "hexes": 1
      },
      "label": "ПРОСАЧИВАНИЕ (аппрокс.: марш)"
     }
    },
    {
     "id": "o36",
     "name": "Научный рывок",
     "kind": "regular",
     "type": "state",
     "requirement": {
      "predicate": "tech_step_reached",
      "params": {
       "step": 3,
       "tracks": 1
      }
     },
     "enhanced": {
      "predicate": "tech_step_reached",
      "params": {
       "step": 4,
       "tracks": 1
      }
     },
     "base_reward": {
      "coin": 4,
      "ammo": 1
     },
     "special_reward": {
      "trophy": 2,
      "objective_card": 1
     },
     "top": {
      "effect": "heal_one",
      "params": {},
      "label": "ЩИТ — АВИАЦИЯ (аппрокс.: снять 1 урон)"
     }
    },
    {
     "id": "o37",
     "name": "Три трека",
     "kind": "regular",
     "type": "state",
     "requirement": {
      "predicate": "tracks_occupied",
      "params": {
       "tracks": 3,
       "min_step": 1
      }
     },
     "enhanced": {
      "predicate": "tracks_occupied",
      "params": {
       "tracks": 3,
       "min_step": 2
      }
     },
     "base_reward": {
      "coin": 4,
      "ammo": 2
     },
     "special_reward": {
      "trophy": 2,
      "objective_card": 1
     },
     "top": {
      "effect": "free_action",
      "params": {
       "action": "build"
      },
      "label": "ЛЬГОТНАЯ СТРОЙКА"
     }
    },
    {
     "id": "o38",
     "name": "Промышленник",
     "kind": "regular",
     "type": "state",
     "requirement": {
      "predicate": "powered_miners_count",
      "params": {
       "count": 3
      }
     },
     "enhanced": {
      "predicate": "powered_miners_count",
      "params": {
       "count": 4
      }
     },
     "base_reward": {
      "coin": 3,
      "ammo": 2
     },
     "special_reward": {
      "kelium": 1,
      "objective_card": 1
     },
     "top": {
      "effect": "free_action",
      "params": {
       "action": "combat"
      },
      "label": "ОДИНОЧНЫЙ ВЫСТРЕЛ"
     }
    },
    {
     "id": "o39",
     "name": "Штабная чистка",
     "kind": "regular",
     "type": "sacrifice",
     "sacrifice": {
      "resource": "objective_cards",
      "amount": 2
     },
     "requirement": {
      "predicate": "sacrifice_paid",
      "params": {}
     },
     "enhanced": {
      "predicate": "sacrifice_enhanced",
      "params": {
       "resource": "objective_cards",
       "amount": 3
      }
     },
     "base_reward": {
      "coin": 5
     },
     "special_reward": {
      "trophy": 2,
      "objective_card": 1
     },
     "top": {
      "effect": "free_action",
      "params": {
       "action": "assembly"
      },
      "label": "МАЛАЯ СБОРКА"
     }
    },
    {
     "id": "o40",
     "name": "Ва-банк",
     "kind": "regular",
     "type": "state",
     "requirement": {
      "predicate": "resources_at_most",
      "params": {
       "coin": 0,
       "ammo": 0
      }
     },
     "enhanced": {
      "predicate": "resources_at_most",
      "params": {
       "coin": 0,
       "ammo": 0,
       "kelium": 0
      }
     },
     "base_reward": {
      "coin": 3,
      "ammo": 2
     },
     "special_reward": {
      "trophy": 1,
      "objective_card": 1
     },
     "top": {
      "effect": "free_action",
      "params": {
       "action": "science"
      },
      "label": "МАЛАЯ НАУКА"
     }
    },
    {
     "id": "n1",
     "name": "Подъём",
     "kind": "starting",
     "type": "state",
     "requirement": {
      "predicate": "buildings_on_field_count",
      "params": {
       "count": 2
      }
     },
     "base_reward": {
      "coin": 1
     },
     "special_reward": {
      "trophy": 1,
      "objective_card": 1
     }
    },
    {
     "id": "n2",
     "name": "Первый боец",
     "kind": "starting",
     "type": "incident",
     "requirement": {
      "predicate": "produced_units_this_turn",
      "params": {
       "count": 1
      }
     },
     "base_reward": {
      "coin": 1
     },
     "special_reward": {
      "trophy": 1,
      "objective_card": 1
     }
    },
    {
     "id": "n3",
     "name": "Патроны",
     "kind": "starting",
     "type": "state",
     "requirement": {
      "predicate": "resource_at_least",
      "params": {
       "resource": "ammo",
       "amount": 2
      }
     },
     "base_reward": {
      "coin": 1
     },
     "special_reward": {
      "trophy": 1,
      "objective_card": 1
     }
    },
    {
     "id": "n4",
     "name": "Первая сделка",
     "kind": "starting",
     "type": "incident",
     "requirement": {
      "predicate": "used_market_this_turn",
      "params": {
       "printed_rate": true
      }
     },
     "base_reward": {
      "coin": 1
     },
     "special_reward": {
      "trophy": 1,
      "objective_card": 1
     }
    },
    {
     "id": "n5",
     "name": "Коммутация",
     "kind": "starting",
     "type": "state",
     "requirement": {
      "predicate": "non_cu_building_has_energy",
      "params": {}
     },
     "base_reward": {
      "coin": 1
     },
     "special_reward": {
      "trophy": 1,
      "objective_card": 1
     }
    },
    {
     "id": "n6",
     "name": "Выход",
     "kind": "starting",
     "type": "state",
     "requirement": {
      "predicate": "unit_off_cu_hex",
      "params": {}
     },
     "base_reward": {
      "coin": 1
     },
     "special_reward": {
      "trophy": 1,
      "objective_card": 1
     }
    },
    {
     "id": "n7",
     "name": "Жила",
     "kind": "starting",
     "type": "state",
     "requirement": {
      "predicate": "resource_at_least",
      "params": {
       "resource": "kelium",
       "amount": 2
      }
     },
     "base_reward": {
      "coin": 1
     },
     "special_reward": {
      "trophy": 1,
      "objective_card": 1
     }
    },
    {
     "id": "n8",
     "name": "Находка",
     "kind": "starting",
     "type": "state",
     "requirement": {
      "predicate": "has_unopened_container",
      "params": {}
     },
     "base_reward": {
      "coin": 1
     },
     "special_reward": {
      "trophy": 1,
      "objective_card": 1
     }
    }
   ]
  },
  "arsenal": {
   "meta": {
    "id": "1.2.0",
    "type": "arsenal"
   },
   "arsenal": [
    {
     "id": "as1",
     "name": "Ударное звено",
     "kind": "starting",
     "top": {
      "effect": "free_action",
      "params": {
       "action": "combat"
      },
      "label": "free infantry attack"
     },
     "bottom": {
      "kind": "SPEC",
      "passive": "unit_makes_one_attack",
      "label": "SPEC: a unit makes one attack"
     }
    },
    {
     "id": "as2",
     "container_slot": true,
     "name": "Старатели",
     "kind": "starting",
     "top": {
      "effect": "gain",
      "params": {
       "coin": 2,
       "ammo": 1
      },
      "label": "2 coin + 1 ammo"
     },
     "bottom": {
      "kind": "POST",
      "passive": "extraction_flip_bonus_trophy",
      "label": "POST: +1 trophy on extraction flip"
     }
    },
    {
     "id": "as3",
     "name": "Окопы",
     "kind": "starting",
     "top": {
      "effect": "heal_hex",
      "params": {},
      "label": "heal all own in one hex"
     },
     "bottom": {
      "kind": "POST",
      "passive": "buildings_plus1_hp",
      "label": "POST: your buildings +1 HP"
     }
    },
    {
     "id": "as4",
     "container_slot": true,
     "name": "Связной",
     "kind": "starting",
     "top": {
      "effect": "move_unit",
      "params": {
       "hexes": 2
      },
      "label": "move a unit 2 hexes"
     },
     "bottom": {
      "kind": "SPEC",
      "passive": "move_one_unit_1",
      "label": "SPEC: move a unit 1 hex"
     }
    },
    {
     "id": "as5",
     "name": "Энергоцех",
     "kind": "starting",
     "top": {
      "effect": "free_action",
      "params": {
       "action": "energy_swap"
      },
      "label": "free Energy swap"
     },
     "bottom": {
      "kind": "POST",
      "passive": "plants_plus1_energy",
      "label": "POST: your power plants give +1 energy"
     }
    },
    {
     "id": "as6",
     "container_slot": true,
     "name": "Снабженцы",
     "kind": "starting",
     "top": {
      "effect": "gain",
      "params": {
       "containers": 2
      },
      "label": "2 containers"
     },
     "bottom": {
      "kind": "SPEC",
      "passive": "miner_takes_container",
      "label": "SPEC: a powered miner takes a container"
     }
    },
    {
     "id": "as7",
     "container_slot": true,
     "name": "Штаб связи",
     "kind": "starting",
     "top": {
      "effect": "gain",
      "params": {
       "objective_cards": 1,
       "coin": 1
      },
      "label": "1 objective + 1 coin"
     },
     "bottom": {
      "kind": "POST",
      "passive": "objective_hand_plus1",
      "label": "POST: objective hand refill limit +1"
     }
    },
    {
     "id": "as8",
     "name": "Изыскатели",
     "kind": "starting",
     "top": {
      "effect": "free_action",
      "params": {
       "action": "science"
      },
      "label": "free Science"
     },
     "bottom": {
      "kind": "POST",
      "passive": "science_first_step_discount",
      "label": "POST: first Science step each action -1 trophy (min 1)"
     }
    },
    {
     "id": "a01",
     "name": "Пристрелка",
     "top": {
      "effect": "place_damage",
      "params": {},
      "label": "1 damage to adjacent enemy"
     },
     "bottom": {
      "kind": "POST",
      "passive": "first_attack_minus1_ammo",
      "label": "POST: first attack each battle -1 ammo"
     }
    },
    {
     "id": "a02",
     "container_slot": true,
     "name": "Мародёры",
     "top": {
      "effect": "gain",
      "params": {
       "ammo": 3
      },
      "label": "3 ammo"
     },
     "bottom": {
      "kind": "POST",
      "passive": "ammo_on_kill",
      "label": "POST: +1 ammo when you destroy a token"
     }
    },
    {
     "id": "a03",
     "name": "Штурмовая группа",
     "top": {
      "effect": "free_action",
      "params": {
       "action": "combat"
      },
      "label": "free Combat"
     },
     "bottom": {
      "kind": "SPEC",
      "passive": "unit_makes_one_attack",
      "label": "SPEC: a unit makes one attack"
     }
    },
    {
     "id": "a04",
     "name": "Бронебойщики",
     "top": {
      "effect": "place_damage",
      "params": {
       "finish_off": true
      },
      "label": "finish off adjacent enemy"
     },
     "bottom": {
      "kind": "POST",
      "passive": "anti_armor_minus1_ammo",
      "label": "POST: attacks vs vehicle/buildings/towers -1 ammo"
     }
    },
    {
     "id": "a05",
     "name": "Двойной залп",
     "cull": "[4]",
     "top": {
      "effect": "free_action",
      "params": {
       "action": "combat"
      },
      "label": "two battles, no 2nd surcharge"
     },
     "bottom": {
      "kind": "POST",
      "passive": "no_second_battle_surcharge",
      "label": "POST: no 2nd-battle surcharge"
     }
    },
    {
     "id": "a06",
     "container_slot": true,
     "name": "Трофейная бригада",
     "cull": "[3+]",
     "top": {
      "effect": "gain",
      "params": {
       "trophy": 2
      },
      "label": "2 trophy"
     },
     "bottom": {
      "kind": "POST",
      "passive": "bonus_trophy_on_kill",
      "label": "POST: +1 trophy per token destroyed"
     }
    },
    {
     "id": "a07",
     "name": "Перекупщик",
     "top": {
      "effect": "gain",
      "params": {
       "coin": 4
      },
      "label": "4 coin"
     },
     "bottom": {
      "kind": "POST",
      "passive": "market_second_kelium_full",
      "label": "POST: 2nd kelium in a market deal pays full"
     }
    },
    {
     "id": "a08",
     "name": "Геологи",
     "top": {
      "effect": "free_action",
      "params": {
       "action": "mining"
      },
      "label": "free Mining"
     },
     "bottom": {
      "kind": "SPEC",
      "passive": "miner_takes_container",
      "label": "SPEC: a powered miner takes a container"
     }
    },
    {
     "id": "a09",
     "container_slot": true,
     "name": "Складчина",
     "top": {
      "effect": "gain",
      "params": {
       "objective_cards": 2,
       "coin": 2
      },
      "label": "2 objectives + 2 coin"
     },
     "bottom": {
      "kind": "POST",
      "passive": "plus1_storage_cell",
      "label": "POST: +1 storage cell"
     }
    },
    {
     "id": "a10",
     "container_slot": true,
     "name": "Лаборатория",
     "top": {
      "effect": "free_action",
      "params": {
       "action": "science"
      },
      "label": "free Science"
     },
     "bottom": {
      "kind": "SPEC",
      "passive": "commit_1_trophy_to_track",
      "label": "SPEC: put 1 trophy on any track"
     }
    },
    {
     "id": "a11",
     "name": "Глубокое бурение",
     "cull": "[4]",
     "top": {
      "effect": "free_action",
      "params": {
       "action": "mining"
      },
      "label": "free Mining x2"
     },
     "bottom": {
      "kind": "POST",
      "passive": "miner_takes_kelium_and_container",
      "label": "POST: each miner takes kelium AND container"
     }
    },
    {
     "id": "a12",
     "name": "Промышленник",
     "cull": "[3+]",
     "top": {
      "effect": "free_action",
      "params": {
       "action": "build"
      },
      "label": "free Build (no surcharge)"
     },
     "bottom": {
      "kind": "SPEC",
      "passive": "build_minus2_coin",
      "label": "SPEC: build one building -2 coin"
     }
    },
    {
     "id": "a13",
     "container_slot": true,
     "name": "Полевой госпиталь",
     "top": {
      "effect": "heal_hex",
      "params": {},
      "label": "heal all own in one hex"
     },
     "bottom": {
      "kind": "SPEC",
      "passive": "heal_one_damage",
      "label": "SPEC: remove 1 damage from a token"
     }
    },
    {
     "id": "a14",
     "name": "Заграждения",
     "top": {
      "effect": "free_action",
      "params": {
       "action": "build"
      },
      "label": "free tower"
     },
     "bottom": {
      "kind": "POST",
      "passive": "defenders_cost_more",
      "label": "POST: attacks on your units in your building-hexes cost +1 ammo"
     }
    },
    {
     "id": "a15",
     "container_slot": true,
     "name": "Резерв",
     "top": {
      "effect": "deploy_units",
      "params": {
       "count": 2
      },
      "label": "deploy 2 units"
     },
     "bottom": {
      "kind": "SPEC",
      "passive": "deploy_1_unit",
      "label": "SPEC: deploy 1 matching unit from reserve"
     }
    },
    {
     "id": "a16",
     "name": "Автономный контур",
     "top": {
      "effect": "free_action",
      "params": {
       "action": "assembly"
      },
      "label": "free full Assembly"
     },
     "bottom": {
      "kind": "POST",
      "passive": "plants_plus1_energy",
      "label": "POST: your power plants give +1 energy"
     }
    },
    {
     "id": "a17",
     "name": "Глубокая оборона",
     "cull": "[4]",
     "contested": "attack_first_initiative",
     "top": {
      "effect": "heal_all_own",
      "params": {},
      "label": "heal all own tokens"
     },
     "bottom": {
      "kind": "POST",
      "passive": "retaliation_strikes_first",
      "label": "POST: in retaliation your units strike first"
     }
    },
    {
     "id": "a18",
     "container_slot": true,
     "name": "Бастион",
     "cull": "[3+]",
     "top": {
      "effect": "free_action",
      "params": {
       "action": "build"
      },
      "label": "free building"
     },
     "bottom": {
      "kind": "POST",
      "passive": "cu_plus2_hp",
      "label": "POST: your CU +2 HP"
     }
    },
    {
     "id": "a19",
     "name": "Разведка",
     "top": {
      "effect": "gain",
      "params": {
       "containers": 2
      },
      "label": "2 containers"
     },
     "bottom": {
      "kind": "SPEC",
      "passive": "grab_adjacent_container",
      "label": "SPEC: take a container from a hex adjacent to a unit"
     }
    },
    {
     "id": "a20",
     "name": "Форсированный марш",
     "top": {
      "effect": "free_action",
      "params": {
       "action": "movement"
      },
      "label": "free Movement"
     },
     "bottom": {
      "kind": "POST",
      "passive": "first_extra_move_free",
      "label": "POST: first over-speed hex each Movement is free"
     }
    },
    {
     "id": "a21",
     "container_slot": true,
     "name": "Диспетчер",
     "top": {
      "effect": "free_action",
      "params": {
       "action": "energy_swap"
      },
      "label": "free module + energy swap"
     },
     "bottom": {
      "kind": "SPEC",
      "passive": "move_one_module",
      "label": "SPEC: move one module"
     }
    },
    {
     "id": "a22",
     "name": "Переброска",
     "top": {
      "effect": "move_unit",
      "params": {
       "hexes": 2
      },
      "label": "move 2 units 2 hexes"
     },
     "bottom": {
      "kind": "POST",
      "passive": "first_two_moves_free",
      "label": "POST: first two off-hex moves each Movement are free"
     }
    },
    {
     "id": "a23",
     "name": "Штабная работа",
     "cull": "[3+]",
     "top": {
      "effect": "free_action",
      "params": {
       "action": "assembly"
      },
      "label": "two actions of one order"
     },
     "bottom": {
      "kind": "POST",
      "passive": "two_spec_actions",
      "label": "POST: you have two SPEC actions per turn"
     }
    },
    {
     "id": "a24",
     "name": "Воздушный мост",
     "cull": "[4]",
     "top": {
      "effect": "move_unit",
      "params": {
       "hexes": 2
      },
      "label": "redeploy a unit"
     },
     "bottom": {
      "kind": "POST",
      "passive": "aircraft_speed3_spawn",
      "label": "POST: your aircraft speed 3, may enter spawn hexes"
     }
    }
   ]
  },
  "containers": {
   "meta": {
    "id": "1.0.0",
    "type": "containers"
   },
   "containers": [
    {
     "id": "c01",
     "name": "Ящик с патронами",
     "tier": "common",
     "a": {
      "effect": "gain",
      "params": {
       "ammo": 2
      },
      "label": "2 ammo"
     },
     "b": {
      "effect": "gain",
      "params": {
       "coin": 2
      },
      "label": "2 coin"
     }
    },
    {
     "id": "c02",
     "name": "Полевая касса",
     "tier": "common",
     "a": {
      "effect": "gain",
      "params": {
       "coin": 3
      },
      "label": "3 coin"
     },
     "b": {
      "effect": "gain",
      "params": {
       "ammo": 1,
       "coin": 1
      },
      "label": "1 ammo + 1 coin"
     }
    },
    {
     "id": "c03",
     "name": "Штабная почта",
     "tier": "common",
     "a": {
      "effect": "gain",
      "params": {
       "objective_cards": 1
      },
      "label": "1 objective"
     },
     "b": {
      "effect": "gain",
      "params": {
       "coin": 2
      },
      "label": "2 coin"
     }
    },
    {
     "id": "c04",
     "name": "Архив",
     "tier": "common",
     "a": {
      "effect": "gain",
      "params": {
       "objective_cards": 1
      },
      "label": "1 objective"
     },
     "b": {
      "effect": "gain",
      "params": {
       "trophy": 1
      },
      "label": "1 trophy"
     }
    },
    {
     "id": "c05",
     "name": "Ремкомплект",
     "tier": "common",
     "a": {
      "effect": "heal_one",
      "params": {
       "amount": 1
      },
      "label": "remove 1 damage"
     },
     "b": {
      "effect": "gain",
      "params": {
       "ammo": 1
      },
      "label": "1 ammo"
     }
    },
    {
     "id": "c06",
     "name": "Топливный бак",
     "tier": "common",
     "a": {
      "effect": "move_unit",
      "params": {
       "hexes": 1
      },
      "label": "move a unit 1 hex"
     },
     "b": {
      "effect": "gain",
      "params": {
       "coin": 2
      },
      "label": "2 coin"
     }
    },
    {
     "id": "c07",
     "name": "Сигнальная ракета",
     "tier": "common",
     "a": {
      "effect": "free_action",
      "params": {
       "action": "combat"
      },
      "label": "free attack"
     },
     "b": {
      "effect": "gain",
      "params": {
       "ammo": 2
      },
      "label": "2 ammo"
     }
    },
    {
     "id": "c08",
     "name": "Инструмент",
     "tier": "common",
     "a": {
      "effect": "free_action",
      "params": {
       "action": "build"
      },
      "label": "build -2 coin"
     },
     "b": {
      "effect": "gain",
      "params": {
       "coin": 3
      },
      "label": "3 coin"
     }
    },
    {
     "id": "c09",
     "name": "Аккумулятор",
     "tier": "common",
     "a": {
      "effect": "free_action",
      "params": {
       "action": "energy_swap"
      },
      "label": "shift energy"
     },
     "b": {
      "effect": "gain",
      "params": {
       "coin": 2
      },
      "label": "2 coin"
     }
    },
    {
     "id": "c10",
     "name": "Образцы породы",
     "tier": "common",
     "a": {
      "effect": "gain",
      "params": {
       "trophy": 1
      },
      "label": "1 trophy"
     },
     "b": {
      "effect": "gain",
      "params": {
       "coin": 2
      },
      "label": "2 coin"
     }
    },
    {
     "id": "c11",
     "name": "Пайка",
     "tier": "common",
     "a": {
      "effect": "gain",
      "params": {
       "coin": 2
      },
      "label": "2 coin"
     },
     "b": {
      "effect": "gain",
      "params": {
       "objective_cards": 1
      },
      "label": "1 objective"
     }
    },
    {
     "id": "c12",
     "name": "ЗИП",
     "tier": "common",
     "a": {
      "effect": "heal_one",
      "params": {
       "amount": 1
      },
      "label": "remove 1 damage"
     },
     "b": {
      "effect": "gain",
      "params": {
       "coin": 2
      },
      "label": "2 coin"
     }
    },
    {
     "id": "c13",
     "name": "Геологическая карта",
     "tier": "common",
     "a": {
      "effect": "free_action",
      "params": {
       "action": "mining"
      },
      "label": "mine once"
     },
     "b": {
      "effect": "gain",
      "params": {
       "objective_cards": 1
      },
      "label": "1 objective"
     }
    },
    {
     "id": "c14",
     "name": "Проволока",
     "tier": "common",
     "a": {
      "effect": "noop",
      "params": {
       "note": "cancel one attack on your token"
      },
      "label": "cancel an attack"
     },
     "b": {
      "effect": "gain",
      "params": {
       "coin": 2
      },
      "label": "2 coin"
     }
    },
    {
     "id": "c15",
     "name": "Радиоперехват",
     "tier": "common",
     "a": {
      "effect": "gain",
      "params": {
       "objective_cards": 1
      },
      "label": "1 objective"
     },
     "b": {
      "effect": "gain",
      "params": {
       "ammo": 1,
       "coin": 1
      },
      "label": "1 ammo + 1 coin"
     }
    },
    {
     "id": "c16",
     "name": "Канистра",
     "tier": "common",
     "a": {
      "effect": "move_unit",
      "params": {
       "hexes": 1
      },
      "label": "unit +1 move"
     },
     "b": {
      "effect": "gain",
      "params": {
       "ammo": 2
      },
      "label": "2 ammo"
     }
    },
    {
     "id": "c17",
     "name": "Запасной ствол",
     "tier": "common",
     "a": {
      "effect": "free_action",
      "params": {
       "action": "combat"
      },
      "label": "wild attack"
     },
     "b": {
      "effect": "gain",
      "params": {
       "ammo": 2
      },
      "label": "2 ammo"
     }
    },
    {
     "id": "c18",
     "name": "Документы",
     "tier": "common",
     "a": {
      "effect": "gain",
      "params": {
       "coin": 2
      },
      "label": "2 coin"
     },
     "b": {
      "effect": "gain",
      "params": {
       "objective_cards": 1
      },
      "label": "1 objective"
     }
    },
    {
     "id": "c19",
     "name": "Медикаменты",
     "tier": "common",
     "a": {
      "effect": "heal_one",
      "params": {
       "amount": "all"
      },
      "label": "remove all damage from one token"
     },
     "b": {
      "effect": "gain",
      "params": {
       "coin": 3
      },
      "label": "3 coin"
     }
    },
    {
     "id": "c20",
     "name": "Крепёж",
     "tier": "common",
     "a": {
      "effect": "noop",
      "params": {
       "note": "one building counts powered for one action"
      },
      "label": "free power 1 action"
     },
     "b": {
      "effect": "gain",
      "params": {
       "coin": 2
      },
      "label": "2 coin"
     }
    },
    {
     "id": "c21",
     "name": "Сейф",
     "tier": "good",
     "a": {
      "effect": "gain",
      "params": {
       "coin": 5
      },
      "label": "5 coin"
     },
     "b": {
      "effect": "gain",
      "params": {
       "kelium": 1
      },
      "label": "1 kelium"
     }
    },
    {
     "id": "c22",
     "name": "Склад боеприпасов",
     "tier": "good",
     "a": {
      "effect": "gain",
      "params": {
       "ammo": 4
      },
      "label": "4 ammo"
     },
     "b": {
      "effect": "gain",
      "params": {
       "objective_cards": 2
      },
      "label": "2 objectives"
     }
    },
    {
     "id": "c23",
     "name": "Чертежи",
     "tier": "good",
     "a": {
      "effect": "gain",
      "params": {
       "objective_cards": 2
      },
      "label": "2 objectives"
     },
     "b": {
      "effect": "gain",
      "params": {
       "trophy": 2
      },
      "label": "2 trophy"
     }
    },
    {
     "id": "c24",
     "name": "Обогащённая порода",
     "tier": "good",
     "a": {
      "effect": "gain",
      "params": {
       "kelium": 1
      },
      "label": "1 kelium"
     },
     "b": {
      "effect": "gain",
      "params": {
       "trophy": 2
      },
      "label": "2 trophy"
     }
    },
    {
     "id": "c25",
     "name": "Трофейный тягач",
     "tier": "good",
     "a": {
      "effect": "free_action",
      "params": {
       "action": "movement"
      },
      "label": "free Movement action"
     },
     "b": {
      "effect": "gain",
      "params": {
       "containers": 2
      },
      "label": "2 containers"
     }
    },
    {
     "id": "c26",
     "name": "Резервный генератор",
     "tier": "good",
     "contested": "energy_without_source",
     "a": {
      "effect": "noop",
      "params": {
       "note": "+1 permanent energy cube on a building"
      },
      "label": "+1 permanent energy"
     },
     "b": {
      "effect": "gain",
      "params": {
       "coin": 4
      },
      "label": "4 coin"
     }
    },
    {
     "id": "c27",
     "name": "Полевая мастерская",
     "tier": "good",
     "a": {
      "effect": "free_action",
      "params": {
       "action": "assembly"
      },
      "label": "free Assembly (1 building)"
     },
     "b": {
      "effect": "gain",
      "params": {
       "ammo": 3
      },
      "label": "3 ammo"
     }
    },
    {
     "id": "c28",
     "name": "Шифровка",
     "tier": "good",
     "contested": "effect_survives_round",
     "a": {
      "effect": "noop",
      "params": {
       "note": "take first-player token next round"
      },
      "label": "grab first player"
     },
     "b": {
      "effect": "gain",
      "params": {
       "objective_cards": 2
      },
      "label": "2 objectives"
     }
    },
    {
     "id": "c29",
     "name": "Оружейный контейнер",
     "tier": "rare",
     "a": {
      "effect": "gain",
      "params": {
       "module_half": "attack"
      },
      "label": "half attack module"
     },
     "b": {
      "effect": "gain",
      "params": {
       "ammo": 3,
       "coin": 2
      },
      "label": "3 ammo + 2 coin"
     }
    },
    {
     "id": "c30",
     "name": "Заводской контейнер",
     "tier": "rare",
     "a": {
      "effect": "gain",
      "params": {
       "module_half": "assembly"
      },
      "label": "half assembly module"
     },
     "b": {
      "effect": "gain",
      "params": {
       "coin": 4,
       "objective_cards": 1
      },
      "label": "4 coin + 1 objective"
     }
    },
    {
     "id": "c31",
     "name": "Гермобокс",
     "tier": "rare",
     "a": {
      "effect": "gain",
      "params": {
       "module_half": "choice"
      },
      "label": "half module (choice)"
     },
     "b": {
      "effect": "gain",
      "params": {
       "kelium": 1
      },
      "label": "1 kelium"
     }
    },
    {
     "id": "c32",
     "name": "Чёрный ящик",
     "tier": "rare",
     "a": {
      "effect": "gain",
      "params": {
       "gild_module": true
      },
      "label": "gild a module"
     },
     "b": {
      "effect": "gain",
      "params": {
       "trophy": 2,
       "coin": 2
      },
      "label": "2 trophy + 2 coin"
     }
    }
   ]
  },
  "market": {
   "meta": {
    "id": "1.0.0",
    "type": "market"
   },
   "market": [
    {
     "id": "military_contract",
     "name": "Военный подряд",
     "left": {
      "name": "Мобилизация",
      "effect": "free_action",
      "params": {
       "action": "assembly"
      },
      "label": "free Assembly"
     },
     "right": {
      "name": "Госзаказ",
      "effect": "gain",
      "params": {
       "coin": 5
      },
      "label": "5 coin"
     }
    },
    {
     "id": "shadow_convoy",
     "name": "Теневой обоз",
     "left": {
      "name": "Контрабанда",
      "effect": "gain",
      "params": {
       "ammo": 4
      },
      "label": "4 ammo"
     },
     "right": {
      "name": "Подлог",
      "effect": "gain",
      "params": {
       "containers": 3
      },
      "label": "3 containers"
     }
    },
    {
     "id": "engineering_office",
     "name": "Инженерная контора",
     "left": {
      "name": "Подряд на стройку",
      "effect": "free_action",
      "params": {
       "action": "build"
      },
      "label": "free Build (no surcharge)"
     },
     "right": {
      "name": "Перекоммутация",
      "effect": "gain",
      "params": {
       "coin": 2
      },
      "label": "free Energy-swap + 2 coin"
     }
    },
    {
     "id": "science_mission",
     "name": "Научная миссия",
     "left": {
      "name": "Грант",
      "effect": "gain",
      "params": {
       "trophy": 4
      },
      "label": "4 trophy"
     },
     "right": {
      "name": "Лицензия",
      "effect": "free_action",
      "params": {
       "action": "science"
      },
      "label": "free Science (2 steps)"
     }
    },
    {
     "id": "weapons_fair",
     "name": "Оружейная ярмарка",
     "left": {
      "name": "Партия модулей",
      "effect": "gain",
      "params": {
       "module_half": "choice"
      },
      "label": "half module (choice)"
     },
     "right": {
      "name": "Модернизация",
      "effect": "gain",
      "params": {
       "gild_module": true
      },
      "label": "gild a module"
     }
    },
    {
     "id": "military_alarm",
     "name": "Военная тревога",
     "left": {
      "name": "Внезапный удар",
      "effect": "free_action",
      "params": {
       "action": "combat"
      },
      "label": "free Combat"
     },
     "right": {
      "name": "Передислокация",
      "effect": "free_action",
      "params": {
       "action": "movement"
      },
      "label": "free Movement (no ammo)"
     }
    },
    {
     "id": "corps_hq",
     "name": "Штаб корпуса",
     "left": {
      "name": "Приоритет",
      "effect": "noop",
      "params": {
       "note": "take first-player token next round"
      },
      "label": "grab first player"
     },
     "right": {
      "name": "Штабная работа",
      "effect": "gain",
      "params": {
       "objective_cards": 3
      },
      "label": "3 objectives"
     }
    },
    {
     "id": "civil_contract",
     "name": "Гражданский подряд",
     "left": {
      "name": "Восстановление",
      "effect": "noop",
      "params": {
       "note": "build a neutral building"
      },
      "label": "build neutral building"
     },
     "right": {
      "name": "Эвакуация",
      "effect": "heal_all_own",
      "params": {
       "coin": 2
      },
      "label": "heal all own + 2 coin"
     }
    }
   ]
  },
  "orders": {
   "meta": {
    "id": "1.0.0",
    "type": "orders"
   },
   "orders": [
    {
     "id": "blue_infra",
     "deck": "blue",
     "top": "infrastructure",
     "bottom": "development",
     "maneuver": true
    },
    {
     "id": "blue_dev",
     "deck": "blue",
     "top": "development",
     "bottom": "operation",
     "maneuver": false
    },
    {
     "id": "blue_oper",
     "deck": "blue",
     "top": "operation",
     "bottom": "acquisitions",
     "maneuver": false
    },
    {
     "id": "blue_acq",
     "deck": "blue",
     "top": "acquisitions",
     "bottom": "infrastructure",
     "maneuver": true
    },
    {
     "id": "red_infra",
     "deck": "scarlet",
     "top": "infrastructure",
     "bottom": "acquisitions",
     "maneuver": true
    },
    {
     "id": "red_acq",
     "deck": "scarlet",
     "top": "acquisitions",
     "bottom": "operation",
     "maneuver": true
    },
    {
     "id": "red_oper",
     "deck": "scarlet",
     "top": "operation",
     "bottom": "development",
     "maneuver": false
    },
    {
     "id": "red_dev",
     "deck": "scarlet",
     "top": "development",
     "bottom": "infrastructure",
     "maneuver": false
    },
    {
     "id": "green_infra",
     "deck": "green",
     "top": "infrastructure",
     "bottom": "operation",
     "maneuver": true
    },
    {
     "id": "green_oper",
     "deck": "green",
     "top": "operation",
     "bottom": "infrastructure",
     "maneuver": false
    },
    {
     "id": "green_dev",
     "deck": "green",
     "top": "development",
     "bottom": "acquisitions",
     "maneuver": false
    },
    {
     "id": "green_acq",
     "deck": "green",
     "top": "acquisitions",
     "bottom": "development",
     "maneuver": true
    },
    {
     "id": "yellow_infra",
     "deck": "yellow",
     "top": "infrastructure",
     "bottom": "development",
     "maneuver": true
    },
    {
     "id": "yellow_dev",
     "deck": "yellow",
     "top": "development",
     "bottom": "acquisitions",
     "maneuver": false
    },
    {
     "id": "yellow_acq",
     "deck": "yellow",
     "top": "acquisitions",
     "bottom": "infrastructure",
     "maneuver": true
    },
    {
     "id": "yellow_oper",
     "deck": "yellow",
     "top": "operation",
     "bottom": "operation",
     "maneuver": false
    },
    {
     "id": "security_1",
     "deck": "security",
     "joker": true,
     "maneuver": false
    },
    {
     "id": "security_2",
     "deck": "security",
     "joker": true,
     "maneuver": false
    },
    {
     "id": "security_3",
     "deck": "security",
     "joker": true,
     "maneuver": false
    },
    {
     "id": "security_4",
     "deck": "security",
     "joker": true,
     "maneuver": false
    }
   ]
  },
  "super_objectives": {
   "meta": {
    "id": "1.0.0",
    "type": "super_objectives"
   },
   "super_objectives": [
    {
     "id": "prizma",
     "name": "Призма",
     "subtitle": "келемиевый излучатель",
     "assembly": {
      "parts": [
       {
        "kind": "kelium",
        "amount": 4
       }
      ]
     },
     "win_pattern": {
      "id": "sp_line_of_three_buildings_mid_adjacent_enemy"
     }
    },
    {
     "id": "liteynya",
     "name": "Литейня",
     "subtitle": "переплавка брони",
     "assembly": {
      "parts": [
       {
        "kind": "enemy_unit_token",
        "amount": 3
       }
      ]
     },
     "win_pattern": {
      "id": "sp_three_buildings_one_hex_touching"
     }
    },
    {
     "id": "koloss",
     "name": "Колосс",
     "assembly": {
      "parts": [
       {
        "kind": "own_building_adjacent_enemy",
        "amount": 2
       }
      ]
     },
     "win_pattern": {
      "id": "sp_triangle_three_buildings_one_adjacent_enemy"
     }
    },
    {
     "id": "zavesa",
     "name": "Завеса",
     "assembly": {
      "parts": [
       {
        "kind": "coin",
        "amount": 8
       }
      ]
     },
     "win_pattern": {
      "id": "sp_four_buildings_around_common_hex"
     }
    },
    {
     "id": "krot",
     "name": "Крот",
     "assembly": {
      "parts": [
       {
        "kind": "own_miner_bordering_grid",
        "amount": 2
       }
      ]
     },
     "win_pattern": {
      "id": "sp_chain_three_miners_bordering_grids"
     }
    },
    {
     "id": "yadro",
     "name": "Ядро",
     "assembly": {
      "parts": [
       {
        "kind": "trophy",
        "amount": 6
       }
      ]
     },
     "win_pattern": {
      "id": "sp_three_unit_hexes_adjacent_one_enemy_hex"
     }
    },
    {
     "id": "roy",
     "name": "Рой",
     "assembly": {
      "parts": [
       {
        "kind": "enemy_building_token",
        "amount": 2
       },
       {
        "kind": "coin",
        "amount": 2
       }
      ]
     },
     "win_pattern": {
      "id": "sp_four_hexes_diamond_each_with_unit"
     }
    },
    {
     "id": "zenit",
     "name": "Зенит",
     "assembly": {
      "parts": [
       {
        "kind": "own_unit_on_enemy_hex",
        "amount": 3
       }
      ]
     },
     "win_pattern": {
      "id": "sp_three_tower_building_pairs"
     }
    }
   ]
  },
  "super_arsenal": {
   "meta": {
    "id": "1.0.0",
    "type": "super_arsenal"
   },
   "super_arsenal": [
    {
     "id": "sa1",
     "name": "Гвардия «Кель»",
     "kind": "troop",
     "unit": "infantry",
     "hp_bonus": 1,
     "vp_on_card": 1,
     "label": "супер-пехота: 2 универсальные атаки, +1 HP, +1 ПО"
    },
    {
     "id": "sa2",
     "name": "Тяжёлый танк «Раздор»",
     "kind": "troop",
     "unit": "vehicle",
     "hp_bonus": 1,
     "vp_on_card": 1,
     "label": "супер-техника: 2 универсальные атаки, +1 HP, +1 ПО"
    },
    {
     "id": "sa3",
     "name": "Штурмовик «Гроза»",
     "kind": "troop",
     "unit": "aircraft",
     "hp_bonus": 1,
     "vp_on_card": 1,
     "label": "супер-авиация: 2 универсальные атаки, +1 HP, +1 ПО"
    },
    {
     "id": "sa4",
     "name": "Цитадель",
     "kind": "troop",
     "unit": "tower",
     "hp_bonus": 1,
     "vp_on_card": 1,
     "label": "супер-вышка: 2 универсальные атаки, +1 HP, +1 ПО"
    },
    {
     "id": "sa5",
     "name": "Штабная директива",
     "kind": "power",
     "passive": "ignore_coincidence",
     "inert": true,
     "label": "правило совпадения приказов тебя не касается"
    },
    {
     "id": "sa6",
     "name": "Келемиевый рудник",
     "kind": "power",
     "passive": "kelium_income",
     "label": "+1 келемий каждое Обновление"
    },
    {
     "id": "sa7",
     "name": "Военная машина",
     "kind": "power",
     "passive": "all_attacks_minus1_ammo",
     "label": "все твои атаки стоят на 1 БП меньше (минимум 1)"
    },
    {
     "id": "sa8",
     "name": "Мандат совета",
     "kind": "power",
     "vp_flat": 2,
     "label": "+2 ПО, пока карта у тебя"
    },
    {
     "id": "sa9",
     "name": "Параллельные штабы",
     "kind": "power",
     "passive": "two_spec_actions",
     "label": "у тебя два СПЕЦ-действия за ход"
    }
   ]
  }
 },
 "scenarios": {
  "2": [
   {
    "id": "field_2p_v1",
    "shape": [
     {
      "offset": 1,
      "count": 3
     },
     {
      "offset": 1,
      "count": 4
     },
     {
      "offset": 0,
      "count": 5
     },
     {
      "offset": 1,
      "count": 4
     },
     {
      "offset": 1,
      "count": 3
     }
    ],
    "special": [
     {
      "row": 1,
      "col": 3,
      "content": "container",
      "count": 1
     },
     {
      "row": 1,
      "col": 4,
      "content": "container",
      "count": 1
     },
     {
      "row": 2,
      "col": 3,
      "content": "kelium_tile"
     },
     {
      "row": 2,
      "col": 4,
      "content": "container",
      "count": 1
     },
     {
      "row": 2,
      "col": 5,
      "content": "spawn_start"
     },
     {
      "row": 3,
      "col": 1,
      "content": "player_start",
      "seat": 0
     },
     {
      "row": 3,
      "col": 5,
      "content": "player_start",
      "seat": 1
     },
     {
      "row": 4,
      "col": 2,
      "content": "spawn_start"
     },
     {
      "row": 4,
      "col": 3,
      "content": "container",
      "count": 1
     },
     {
      "row": 4,
      "col": 4,
      "content": "kelium_tile"
     },
     {
      "row": 5,
      "col": 2,
      "content": "container",
      "count": 1
     },
     {
      "row": 5,
      "col": 3,
      "content": "container",
      "count": 1
     }
    ],
    "neutrals": [
     {
      "row": 1,
      "col": 3,
      "size": "small",
      "corners": [
       5,
       6
      ]
     },
     {
      "row": 2,
      "col": 2,
      "size": "small",
      "corners": [
       2,
       3
      ]
     },
     {
      "row": 2,
      "col": 4,
      "size": "small",
      "corners": [
       5,
       6
      ]
     },
     {
      "row": 4,
      "col": 3,
      "size": "small",
      "corners": [
       2,
       3
      ]
     },
     {
      "row": 4,
      "col": 5,
      "size": "small",
      "corners": [
       5,
       6
      ]
     },
     {
      "row": 5,
      "col": 3,
      "size": "small",
      "corners": [
       2,
       3
      ]
     }
    ]
   },
   {
    "id": "field_2p_v2",
    "shape": [
     {
      "offset": 1,
      "count": 3
     },
     {
      "offset": 1,
      "count": 4
     },
     {
      "offset": 1,
      "count": 3
     },
     {
      "offset": 1,
      "count": 4
     },
     {
      "offset": 1,
      "count": 3
     }
    ],
    "special": [
     {
      "row": 1,
      "col": 2,
      "content": "container",
      "count": 1
     },
     {
      "row": 1,
      "col": 3,
      "content": "container",
      "count": 1
     },
     {
      "row": 1,
      "col": 4,
      "content": "spawn_start"
     },
     {
      "row": 2,
      "col": 2,
      "content": "container",
      "count": 1
     },
     {
      "row": 2,
      "col": 3,
      "content": "container",
      "count": 1
     },
     {
      "row": 2,
      "col": 5,
      "content": "player_start",
      "seat": 0
     },
     {
      "row": 3,
      "col": 3,
      "content": "kelium_tile",
      "modifier": "x2"
     },
     {
      "row": 4,
      "col": 2,
      "content": "player_start",
      "seat": 1
     },
     {
      "row": 4,
      "col": 4,
      "content": "container",
      "count": 1
     },
     {
      "row": 4,
      "col": 5,
      "content": "container",
      "count": 1
     },
     {
      "row": 5,
      "col": 2,
      "content": "spawn_start"
     },
     {
      "row": 5,
      "col": 3,
      "content": "container",
      "count": 1
     },
     {
      "row": 5,
      "col": 4,
      "content": "container",
      "count": 1
     }
    ],
    "neutrals": [
     {
      "row": 2,
      "col": 3,
      "size": "big",
      "corners": [
       5,
       6,
       1
      ]
     },
     {
      "row": 2,
      "col": 4,
      "size": "small",
      "corners": [
       5,
       6
      ]
     },
     {
      "row": 3,
      "col": 2,
      "size": "small",
      "corners": [
       2,
       3
      ]
     },
     {
      "row": 3,
      "col": 4,
      "size": "small",
      "corners": [
       5,
       6
      ]
     },
     {
      "row": 4,
      "col": 3,
      "size": "small",
      "corners": [
       2,
       3
      ]
     },
     {
      "row": 4,
      "col": 4,
      "size": "big",
      "corners": [
       2,
       3,
       4
      ]
     }
    ]
   }
  ],
  "3": [
   {
    "id": "field_3p_v1",
    "shape": [
     {
      "offset": 1,
      "count": 2
     },
     {
      "offset": 1,
      "count": 4
     },
     {
      "offset": 0,
      "count": 5
     },
     {
      "offset": 1,
      "count": 4
     },
     {
      "offset": 0,
      "count": 5
     },
     {
      "offset": 1,
      "count": 4
     }
    ],
    "special": [
     {
      "row": 1,
      "col": 2,
      "content": "spawn_start"
     },
     {
      "row": 1,
      "col": 3,
      "content": "player_start",
      "seat": 0
     },
     {
      "row": 3,
      "col": 1,
      "content": "container",
      "count": 1
     },
     {
      "row": 3,
      "col": 2,
      "content": "kelium_tile"
     },
     {
      "row": 3,
      "col": 3,
      "content": "container",
      "count": 1
     },
     {
      "row": 3,
      "col": 4,
      "content": "kelium_tile"
     },
     {
      "row": 3,
      "col": 5,
      "content": "container",
      "count": 1
     },
     {
      "row": 4,
      "col": 2,
      "content": "container",
      "count": 1
     },
     {
      "row": 4,
      "col": 3,
      "content": "container",
      "count": 1
     },
     {
      "row": 4,
      "col": 4,
      "content": "container",
      "count": 1
     },
     {
      "row": 4,
      "col": 5,
      "content": "container",
      "count": 1
     },
     {
      "row": 5,
      "col": 1,
      "content": "spawn_start"
     },
     {
      "row": 5,
      "col": 3,
      "content": "kelium_tile"
     },
     {
      "row": 5,
      "col": 5,
      "content": "spawn_start"
     },
     {
      "row": 6,
      "col": 2,
      "content": "player_start",
      "seat": 1
     },
     {
      "row": 6,
      "col": 5,
      "content": "player_start",
      "seat": 2
     }
    ],
    "neutrals": [
     {
      "row": 2,
      "col": 3,
      "size": "small",
      "corners": [
       4,
       5
      ]
     },
     {
      "row": 2,
      "col": 5,
      "size": "small",
      "corners": [
       4,
       5
      ]
     },
     {
      "row": 3,
      "col": 3,
      "size": "big",
      "corners": [
       1,
       2,
       3
      ]
     },
     {
      "row": 4,
      "col": 3,
      "size": "small",
      "corners": [
       2,
       3
      ]
     },
     {
      "row": 4,
      "col": 4,
      "size": "small",
      "corners": [
       5,
       6
      ]
     },
     {
      "row": 6,
      "col": 3,
      "size": "small",
      "corners": [
       5,
       6
      ]
     },
     {
      "row": 6,
      "col": 3,
      "size": "big",
      "corners": [
       1,
       2,
       3
      ]
     },
     {
      "row": 6,
      "col": 4,
      "size": "big",
      "corners": [
       5,
       6,
       1
      ]
     },
     {
      "row": 6,
      "col": 4,
      "size": "small",
      "corners": [
       2,
       3
      ]
     }
    ]
   },
   {
    "id": "field_3p_v2",
    "shape": [
     {
      "offset": 2,
      "count": 2
     },
     {
      "offset": 1,
      "count": 4
     },
     {
      "offset": 0,
      "count": 5
     },
     {
      "offset": 0,
      "count": 5
     },
     {
      "offset": 0,
      "count": 5
     },
     {
      "offset": 2,
      "count": 3
     }
    ],
    "special": [
     {
      "row": 1,
      "col": 3,
      "content": "player_start",
      "seat": 0
     },
     {
      "row": 1,
      "col": 4,
      "content": "spawn_start"
     },
     {
      "row": 2,
      "col": 5,
      "content": "container",
      "count": 1
     },
     {
      "row": 3,
      "col": 1,
      "content": "container",
      "count": 1
     },
     {
      "row": 3,
      "col": 2,
      "content": "kelium_tile"
     },
     {
      "row": 3,
      "col": 3,
      "content": "container",
      "count": 1
     },
     {
      "row": 3,
      "col": 4,
      "content": "kelium_tile"
     },
     {
      "row": 4,
      "col": 1,
      "content": "spawn_start"
     },
     {
      "row": 4,
      "col": 3,
      "content": "container",
      "count": 1
     },
     {
      "row": 4,
      "col": 4,
      "content": "container",
      "count": 1
     },
     {
      "row": 5,
      "col": 1,
      "content": "player_start",
      "seat": 2
     },
     {
      "row": 5,
      "col": 3,
      "content": "kelium_tile"
     },
     {
      "row": 5,
      "col": 5,
      "content": "player_start",
      "seat": 1
     },
     {
      "row": 6,
      "col": 4,
      "content": "container",
      "count": 1
     },
     {
      "row": 6,
      "col": 5,
      "content": "spawn_start"
     }
    ],
    "neutrals": [
     {
      "row": 2,
      "col": 2,
      "size": "big",
      "corners": [
       1,
       2,
       3
      ]
     },
     {
      "row": 3,
      "col": 3,
      "size": "small",
      "corners": [
       5,
       6
      ]
     },
     {
      "row": 3,
      "col": 5,
      "size": "big",
      "corners": [
       4,
       5,
       6
      ]
     },
     {
      "row": 4,
      "col": 3,
      "size": "small",
      "corners": [
       3,
       4
      ]
     },
     {
      "row": 4,
      "col": 4,
      "size": "small",
      "corners": [
       1,
       2
      ]
     },
     {
      "row": 6,
      "col": 3,
      "size": "big",
      "corners": [
       5,
       6,
       1
      ]
     }
    ]
   }
  ],
  "4": [
   {
    "id": "field_4p_v2",
    "shape": [
     {
      "offset": 1,
      "count": 3
     },
     {
      "offset": 1,
      "count": 4
     },
     {
      "offset": 0,
      "count": 5
     },
     {
      "offset": 0,
      "count": 6
     },
     {
      "offset": 0,
      "count": 5
     },
     {
      "offset": 1,
      "count": 4
     },
     {
      "offset": 1,
      "count": 3
     }
    ],
    "special": [
     {
      "row": 1,
      "col": 4,
      "content": "spawn_start"
     },
     {
      "row": 2,
      "col": 2,
      "content": "player_start",
      "seat": 3
     },
     {
      "row": 2,
      "col": 5,
      "content": "player_start",
      "seat": 0
     },
     {
      "row": 3,
      "col": 1,
      "content": "spawn_start"
     },
     {
      "row": 3,
      "col": 2,
      "content": "container",
      "count": 1
     },
     {
      "row": 3,
      "col": 3,
      "content": "kelium_tile"
     },
     {
      "row": 3,
      "col": 4,
      "content": "container",
      "count": 1
     },
     {
      "row": 4,
      "col": 2,
      "content": "kelium_tile"
     },
     {
      "row": 4,
      "col": 3,
      "content": "container",
      "count": 2
     },
     {
      "row": 4,
      "col": 4,
      "content": "container",
      "count": 2
     },
     {
      "row": 4,
      "col": 5,
      "content": "kelium_tile"
     },
     {
      "row": 5,
      "col": 2,
      "content": "container",
      "count": 1
     },
     {
      "row": 5,
      "col": 3,
      "content": "kelium_tile"
     },
     {
      "row": 5,
      "col": 4,
      "content": "container",
      "count": 1
     },
     {
      "row": 5,
      "col": 5,
      "content": "spawn_start"
     },
     {
      "row": 6,
      "col": 2,
      "content": "player_start",
      "seat": 2
     },
     {
      "row": 6,
      "col": 5,
      "content": "player_start",
      "seat": 1
     },
     {
      "row": 7,
      "col": 2,
      "content": "spawn_start"
     }
    ],
    "neutrals": [
     {
      "row": 2,
      "col": 3,
      "size": "big",
      "corners": [
       1,
       2,
       3
      ]
     },
     {
      "row": 2,
      "col": 4,
      "size": "small",
      "corners": [
       5,
       6
      ]
     },
     {
      "row": 3,
      "col": 5,
      "size": "small",
      "corners": [
       4,
       5
      ]
     },
     {
      "row": 5,
      "col": 1,
      "size": "small",
      "corners": [
       1,
       2
      ]
     },
     {
      "row": 6,
      "col": 3,
      "size": "small",
      "corners": [
       2,
       3
      ]
     },
     {
      "row": 6,
      "col": 4,
      "size": "big",
      "corners": [
       4,
       5,
       6
      ]
     }
    ]
   },
   {
    "id": "field_4p_v4",
    "shape": [
     {
      "offset": 1,
      "count": 2
     },
     {
      "offset": 1,
      "count": 4
     },
     {
      "offset": 0,
      "count": 5
     },
     {
      "offset": 0,
      "count": 6
     },
     {
      "offset": 0,
      "count": 5
     },
     {
      "offset": 1,
      "count": 4
     },
     {
      "offset": 2,
      "count": 2
     }
    ],
    "special": [
     {
      "row": 1,
      "col": 2,
      "content": "container",
      "count": 2
     },
     {
      "row": 2,
      "col": 3,
      "content": "spawn_start",
      "modifier": "-1"
     },
     {
      "row": 2,
      "col": 4,
      "content": "player_start",
      "seat": 0
     },
     {
      "row": 3,
      "col": 1,
      "content": "player_start",
      "seat": 3
     },
     {
      "row": 3,
      "col": 5,
      "content": "container",
      "count": 2
     },
     {
      "row": 4,
      "col": 1,
      "content": "spawn_start"
     },
     {
      "row": 4,
      "col": 3,
      "content": "kelium_tile",
      "modifier": "x2"
     },
     {
      "row": 4,
      "col": 4,
      "content": "kelium_tile",
      "modifier": "x2"
     },
     {
      "row": 4,
      "col": 6,
      "content": "spawn_start"
     },
     {
      "row": 5,
      "col": 1,
      "content": "container",
      "count": 2
     },
     {
      "row": 5,
      "col": 5,
      "content": "player_start",
      "seat": 1
     },
     {
      "row": 6,
      "col": 3,
      "content": "player_start",
      "seat": 2
     },
     {
      "row": 6,
      "col": 4,
      "content": "spawn_start",
      "modifier": "-1"
     },
     {
      "row": 7,
      "col": 4,
      "content": "container",
      "count": 2
     }
    ],
    "neutrals": [
     {
      "row": 1,
      "col": 3,
      "size": "small",
      "corners": [
       5,
       6
      ]
     },
     {
      "row": 3,
      "col": 3,
      "size": "big",
      "corners": [
       4,
       5,
       6
      ]
     },
     {
      "row": 3,
      "col": 4,
      "size": "small",
      "corners": [
       3,
       4
      ]
     },
     {
      "row": 3,
      "col": 5,
      "size": "small",
      "corners": [
       3,
       4
      ]
     },
     {
      "row": 5,
      "col": 1,
      "size": "small",
      "corners": [
       6,
       1
      ]
     },
     {
      "row": 5,
      "col": 2,
      "size": "small",
      "corners": [
       6,
       1
      ]
     },
     {
      "row": 5,
      "col": 3,
      "size": "big",
      "corners": [
       1,
       2,
       3
      ]
     },
     {
      "row": 7,
      "col": 3,
      "size": "small",
      "corners": [
       2,
       3
      ]
     }
    ]
   }
  ]
 }
};
