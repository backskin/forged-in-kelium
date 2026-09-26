package kelium.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.InteractiveAgent;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.core.UnitType;

/**
 * РЕДКИЕ РЕШЕНИЯ ДЛЯ СЪЁМКИ ЭКРАНА. Некоторые вопросы движок задаёт так
 * редко, что случайные партии до них не доходят (найм войск с заводом на
 * тесном гексе, позолота, оплата жетоном со свалки). Здесь варианты собираются
 * из живой партии ТЕМИ ЖЕ видами, подписями и данными, что в движке (ссылка на
 * место — у каждого вида), и окно показывает их своим обычным кодом.
 */
final class РедкиеРешения {

    private РедкиеРешения() {
    }

    /** Решение вида {@code kind} для места {@code seat}; {@code null} — собрать не из чего. */
    static InteractiveAgent.PendingDecision собрать(String kind, GameState s, int seat) {
        PlayerState me = s.player(seat);
        List<Choice> opts = new ArrayList<>();
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("kind", kind);
        switch (kind) {
            case "combat_victim" -> {
                // CombatResolver: «<жертва> игрока N (урон d/hp)»
                String hex = null;
                for (PlayerState o : s.players) {
                    if (o.seat == seat) {
                        continue;
                    }
                    for (UnitToken u : o.units) {
                        if (u.hexId != null && (hex == null || hex.equals(u.hexId))) {
                            hex = u.hexId;
                            opts.add(new Choice("combat_victim", u, u.type.code + " игрока "
                                + u.owner + " (урон " + u.damage + "/" + u.hp + ")"));
                        }
                    }
                    for (BuildingToken b : o.buildingsOnField()) {
                        if (hex != null && hex.equals(b.hexId)) {
                            opts.add(new Choice("combat_victim", b, b.type.code + " игрока "
                                + b.owner + " (урон " + b.damage + "/" + b.hp + ")"));
                        }
                    }
                }
                if (hex == null) {
                    return null;
                }
                ctx.put("target", hex);
            }
            case "destroyed_pay" -> {
                // Actions: «token worth N» и «кубиками трофеев»
                for (PlayerState o : s.players) {
                    if (o.seat == seat) {
                        continue;
                    }
                    for (BuildingToken b : o.buildingsOnField()) {
                        if (opts.size() < 3) {
                            // жетон со свалки на поле не стоит — копия без гекса
                            BuildingToken сСвалки = b.copy();
                            сСвалки.hexId = null;
                            opts.add(new Choice("destroyed_pay", сСвалки,
                                "token worth " + сСвалки.trophyValue()));
                        }
                    }
                }
                opts.add(new Choice("pay_cubes", null, "кубиками трофеев"));
                ctx.put("remaining", 2);
            }
            case "build_neutral" -> {
                // Effects: «нейтрал 1 сектор @hex/i», «нейтрал 2 сектора @hex/i-j»
                int n = 0;
                for (var e : s.field.hexes.entrySet()) {
                    if (e.getValue().spawnTile != null || n >= 3) {
                        continue;
                    }
                    for (int i = 0; i < 6 && n < 3; i++) {
                        if (e.getValue().sideOwner[i] == null) {
                            Map<String, Object> one = new HashMap<>();
                            one.put("hex", e.getKey());
                            one.put("sectors", List.of(i));
                            opts.add(new Choice("neutral", one, "нейтрал 1 сектор @" + e.getKey() + "/" + i));
                            Map<String, Object> two = new HashMap<>();
                            two.put("hex", e.getKey());
                            two.put("sectors", List.of(i, (i + 1) % 6));
                            opts.add(new Choice("neutral", two, "нейтрал 2 сектора @" + e.getKey()
                                + "/" + i + "-" + (i + 1) % 6));
                            n++;
                        }
                    }
                }
            }
            case "energy_or_modules" -> {
                opts.add(new Choice("energy_or_modules", "energy_swap", "Смена энергии"));
                opts.add(new Choice("energy_or_modules", "modules", "Смена модулей на планшете"));
            }
            case "exchange_where" -> {
                opts.add(new Choice("exchange_where", "science", "обмен в Науке (без шага трека)"));
                opts.add(new Choice("exchange_where", "market", "обмен на Рынке (без карты)"));
            }
            case "keep_objective" -> {
                // Arsenal2Abilities: две карты заданий, одну оставить
                List<String> колода = s.decks.get("objectives").drawPile;
                if (колода.size() < 2) {
                    return null;
                }
                String c1 = колода.get(колода.size() - 1);
                String c2 = колода.get(колода.size() - 2);
                opts.add(new Choice("keep", c1, c1));
                opts.add(new Choice("keep", c2, c2));
            }
            case "module_keep" -> {
                // Modules: два жетона из мешка, один оставить
                List<String> мешок = s.redBag;
                if (мешок == null || мешок.size() < 2) {
                    return null;
                }
                opts.add(new Choice("module_keep", мешок.get(0), мешок.get(0)));
                opts.add(new Choice("module_keep", мешок.get(1), мешок.get(1)));
                ctx.put("colour", "red");
            }
            case "module_gild_pick" -> {
                // Modules: «<id> на <код>»
                opts.add(new Choice("gild_red", UnitType.INFANTRY, "R30-1 на infantry"));
                opts.add(new Choice("gild_red", UnitType.VEHICLE, "R30-7 на vehicle"));
                opts.add(new Choice("gild_blue", BuildingType.BARRACKS, "B30-1 на barracks"));
            }
            case "module_place_blue" -> {
                // Modules: «<id>-><здание>» и «leave in reserve»
                String mod = s.blueBag != null && !s.blueBag.isEmpty() ? s.blueBag.get(0) : "B30-1";
                for (BuildingType b : List.of(BuildingType.BARRACKS, BuildingType.FACTORY,
                        BuildingType.AIRBASE, BuildingType.COMMAND_CENTER)) {
                    Map<String, Object> pl = new HashMap<>();
                    pl.put("module", mod);
                    pl.put("building", b);
                    opts.add(new Choice("blue_slot", pl, mod + "->" + b.code));
                }
                opts.add(new Choice("pass", null, "leave in reserve"));
            }
            case "pay_power" -> {
                // Power: «запитать <код> монетами (N МОН на это действие)» / «не платить»
                BuildingToken b = me.buildingsOnField().isEmpty() ? null : me.buildingsOnField().get(0);
                if (b == null) {
                    return null;
                }
                opts.add(new Choice("pay_power", Boolean.TRUE,
                    "запитать " + b.type.code + " монетами (2 МОН на это действие)"));
                opts.add(new Choice("pay_power", Boolean.FALSE, "не платить"));
                ctx.put("cost", 2);
                ctx.put("building", b.uid);
                ctx.put("type", b.type.code);
            }
            case "tower_hex" -> {
                // Actions: «tower @hex» по гексам со своими зданиями
                List<String> seen = new ArrayList<>();
                for (BuildingToken b : me.buildingsOnField()) {
                    if (!seen.contains(b.hexId)) {
                        seen.add(b.hexId);
                        opts.add(new Choice("tower_hex", b.hexId, "tower @" + b.hexId));
                    }
                }
            }
            default -> {
                return null;
            }
        }
        return opts.isEmpty() ? null : new InteractiveAgent.PendingDecision(s, opts, ctx);
    }
}
