package kelium.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.core.TurnJournal;
import kelium.core.Agent;
import kelium.core.Choice;

/**
 * Реестр немедленных эффектов карт (контейнеры/маркет/арсенал/задания).
 *
 * <p>Порт из forge/engine/effects.py. Эффект — это операция, изменяющая
 * состояние игрока/поля и возвращающая небольшую карту телеметрии. Эффекты,
 * требующие выбора агента (move_unit, place_damage), берут активного агента из
 * {@code state.agents} и предлагают объекты Choice, чтобы боты управляли ими
 * единообразно.
 */
public final class Effects {

    private Effects() {
    }

    /** Ошибка применения эффекта (например, неизвестный id). */
    public static final class EffectError extends RuntimeException {
        public EffectError(String msg) {
            super(msg);
        }
    }

    private static int asInt(Object o) {
        return ((Number) o).intValue();
    }

    private static Agent agentFor(GameState s, int seat) {
        if (s.agents == null || seat >= s.agents.size()) {
            return null;
        }
        return s.agents.get(seat);
    }

    /**
     * Применить эффект {@code eid} для места {@code seat} с параметрами
     * {@code params}. Возвращает карту фактически произошедшего (телеметрия).
     */
    public static Map<String, Object> apply(String eid, GameState s, int seat, Map<String, Object> params) {
        if (eid == null) {
            eid = "noop";
        }
        Map<String, Object> p = params != null ? params : Map.of();
        return switch (eid) {
            case "gain" -> gain(s, seat, p);
            case "heal_one" -> healOne(s, seat, p);
            case "heal_all_own" -> healAllOwn(s, seat, p);
            case "heal_hex" -> healHex(s, seat, p);
            case "free_action" -> freeAction(s, seat, p);
            case "move_unit" -> moveUnit(s, seat, p);
            case "deploy_units" -> deployUnits(s, seat, p);
            case "place_damage" -> placeDamage(s, seat, p);
            case "grab_containers" -> grabContainers(s, seat, p);
            case "noop" -> Map.of("noop", p.getOrDefault("note", "unimplemented"));
            // E1: неизвестный эффект — ГРОМКАЯ ошибка, не тихий noop; карты с
            // такими эффектами отсеиваются из колод на сетапе (см. Setup).
            default -> throw new EffectError("нереализованный эффект: " + eid);
        };
    }

    /** E1/E2: реализован ли эффект с данным id (для отсева карт на сетапе). */
    public static boolean isImplemented(String eid) {
        if (eid == null) {
            return false;
        }
        return switch (eid) {
            case "gain", "heal_one", "heal_all_own", "heal_hex", "free_action",
                 "move_unit", "deploy_units", "place_damage", "grab_containers" -> true;
            default -> false;   // включая "noop" — карта-заглушка не должна попасть в колоду
        };
    }

    // -- получение ресурсов ---------------------------------------------------
    static Map<String, Object> gain(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        Map<String, Object> got = new HashMap<>();
        if (p.containsKey("coin")) {
            int n = asInt(p.get("coin"));
            pl.resources.add(Resource.COIN, n);
            got.put("coin", n);
        }
        if (p.containsKey("debris")) {
            got.put("debris", Storage.addDebrisCapped(s, pl, asInt(p.get("debris"))));
        }
        if (p.containsKey("kelium")) {
            got.put("kelium", Storage.addKeliumCapped(s, pl, asInt(p.get("kelium"))));
        }
        if (p.containsKey("ammo")) {
            got.put("ammo", Storage.addAmmoCapped(s, pl, asInt(p.get("ammo"))));
        }
        if (p.containsKey("containers")) {
            int n = asInt(p.get("containers"));
            got.put("containers", Storage.addContainersCapped(s, pl, n, "карта/эффект"));
        }
        if (p.containsKey("objective_cards")) {
            int want = asInt(p.get("objective_cards"));
            int drawn = 0;
            for (int i = 0; i < want; i++) {
                String c = s.decks.get("objectives").draw(s.rng);
                if (c == null) {
                    break;
                }
                pl.objectiveHand.add(c);
                drawn++;
            }
            got.put("objective_cards", drawn);
        }
        if (p.containsKey("arsenal")) {
            int want = asInt(p.get("arsenal"));
            int drawn = 0;
            for (int i = 0; i < want; i++) {
                String c = s.decks.get("arsenal").draw(s.rng);
                if (c == null) {
                    break;
                }
                pl.arsenalHand.add(c);
                drawn++;
            }
            got.put("arsenal", drawn);
        }
        Object module = p.get("module");
        if ("attack".equals(module)) {
            // «Модули 2.0»: с мешками жетон тянется случайно, а не выбирается
            String drew = Modules.awardModule(s, pl, "red");
            got.put("module", drew == null ? "attack" : drew);
        } else if ("assembly".equals(module)) {
            String drew = Modules.awardModule(s, pl, "blue");
            got.put("module", drew == null ? "assembly" : drew);
        } else if ("choice".equals(module)) {
            // ВЫБОР ЦВЕТА МЕШКА за игроком (покупка жетона на карте рынка). Сам
            // жетон внутри цвета всё равно тянется случайно — мешок есть мешок.
            String colour = pl.redModules <= pl.blueModules ? "red" : "blue";
            Agent ag = agentFor(s, seat);
            if (ag != null) {
                Choice pick = ag.choose(s, List.of(
                    new Choice("module_bag", "red", "жетон АТАКИ из красного мешка"),
                    new Choice("module_bag", "blue", "жетон СБОРКИ из синего мешка")),
                    Map.of("kind", "module_bag"));
                if (pick != null && pick.payload() != null) {
                    colour = String.valueOf(pick.payload());
                }
            }
            String drew = Modules.awardModule(s, pl, colour);
            got.put("module", drew == null ? colour : drew);
        }
        // ПОЛОВИНОК МОДУЛЕЙ БОЛЬШЕ НЕТ (решение дизайнера 13.08.2026). Механика
        // «две половинки складываются в жетон» отменена целиком: она требовала
        // отдельного учёта на планшете, а выгоды не давала. Всё, что раньше давало
        // половинку, теперь тянет ЦЕЛЫЙ жетон модуля из мешка — так же, как награда
        // трека или задания. Старые карты с ключом module_half продолжают работать
        // без правки данных, поэтому ключ читается, но означает целый жетон.
        Object half = p.get("module_half");
        if (half != null) {
            String colour = "assembly".equals(half.toString()) ? "blue"
                : "attack".equals(half.toString()) ? "red"
                : pl.redModules <= pl.blueModules ? "red" : "blue";
            String drew = Modules.awardModule(s, pl, colour);
            got.put("module", drew == null ? colour : drew);
        }
        if (Boolean.TRUE.equals(p.get("gild_module"))) {
            if (pl.redModules + pl.blueModules > pl.goldModules) {
                pl.goldModules += 1;
                got.put("gild_module", true);
            }
        }
        // storage_token из эффектов ОТМЕНЁН (правило 2026-08-11): жетон хранилища
        // выдаёт ТОЛЬКО зелёный трек. Старые карты с этим эффектом дают полжетона
        // сборки взамен (чтобы эффект не был пустым).
        if (Boolean.TRUE.equals(p.get("storage_token"))) {
            String drew = Modules.awardModule(s, pl, "blue");
            got.put("module", drew == null ? "blue" : drew);
        }
        return got;
    }

    // -- лечение --------------------------------------------------------------
    static Map<String, Object> healOne(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        List<Token> dmgd = damagedTokens(pl);
        if (dmgd.isEmpty()) {
            return Map.of("healed", 0);
        }
        dmgd.sort((a, b) -> Integer.compare(damageOf(b), damageOf(a)));
        Token tok = dmgd.get(0);
        int removed;
        if ("all".equals(p.get("amount"))) {
            removed = damageOf(tok);
            setDamage(tok, 0);
        } else {
            removed = Math.min(1, damageOf(tok));
            setDamage(tok, damageOf(tok) - removed);
        }
        return Map.of("healed", removed);
    }

    static Map<String, Object> healAllOwn(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        int healed = 0;
        for (Token t : damagedTokens(pl)) {
            healed += damageOf(t);
            setDamage(t, 0);
        }
        if (p != null && !p.isEmpty()) {
            gain(s, seat, p);
        }
        return Map.of("healed_total", healed);
    }

    static Map<String, Object> healHex(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        Map<String, List<Token>> byHex = new HashMap<>();
        for (Token t : damagedTokens(pl)) {
            String h = t.hexId();
            if (h != null) {
                byHex.computeIfAbsent(h, k -> new ArrayList<>()).add(t);
            }
        }
        if (byHex.isEmpty()) {
            return Map.of("healed", 0);
        }
        String best = null;
        int bestDmg = -1;
        for (var e : byHex.entrySet()) {
            int sum = 0;
            for (Token t : e.getValue()) {
                sum += damageOf(t);
            }
            if (sum > bestDmg) {
                bestDmg = sum;
                best = e.getKey();
            }
        }
        int healed = 0;
        for (Token t : byHex.get(best)) {
            healed += damageOf(t);
            setDamage(t, 0);
        }
        Map<String, Object> got = new HashMap<>();
        got.put("healed", healed);
        got.put("hex", best);
        return got;
    }

    // -- под-действия ---------------------------------------------------------
    static Map<String, Object> freeAction(GameState s, int seat, Map<String, Object> p) {
        String name = (String) p.get("action");
        Agent agent = agentFor(s, seat);
        Map<String, Object> got = new HashMap<>();
        got.put("free_action", name);
        if (name == null || agent == null || !Actions.ALL_NAMES.contains(name)) {
            got.put("ran", false);
            return got;
        }
        // E4: бесплатное действие с карты — самостоятельное разрешение со своим
        // контекстом (наценки внутри него считаются с нуля — это дар карты, а
        // не продолжение хода), но ЖУРНАЛ и телеметрия обязаны его видеть.
        TurnContext ctx = new TurnContext(seat, 0);
        var res = Actions.create(name, s).perform(s.player(seat), ctx, agent);
        if (s.journal instanceof TurnJournal tj && res != null && res.ok()) {
            tj.onAction(seat, name, res.telemetry());
        }
        got.put("ran", res != null && res.ok());
        if (res != null) {
            got.put("detail", res.detail());
        }
        return got;
    }

    static Map<String, Object> moveUnit(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        Agent agent = agentFor(s, seat);
        int steps = p.containsKey("hexes") ? asInt(p.get("hexes")) : 1;
        int moved = 0;
        for (int step = 0; step < steps; step++) {
            List<Choice> opts = new ArrayList<>();
            for (UnitToken u : pl.unitsOnField()) {
                // СКОРОСТЬ 0 — НЕ ДВИГАЕТСЯ НИЧЕМ. Вышка ЦУ — дот: она стоит
                // там, где поставлена. Раньше эффект карты («переброска») этого
                // не проверял и таскал вышки по полю (баг найден дизайнером
                // 12.08.2026); Движение и манёвр скорость учитывали, а карты нет.
                Integer airOverride = Passives.aircraftSpeedOverride(s, seat);
                int speed = airOverride != null && u.type == kelium.core.UnitType.AIRCRAFT
                    ? airOverride : kelium.engine.Speed.of(s, pl.seat, u.type);
                if (speed <= 0) {
                    continue;
                }
                for (String nb : s.field.neighbors(u.hexId)) {
                    // E3: эффект карты обязан соблюдать ПРАВИЛА ПРОХОДИМОСТИ
                    // (запретные гексы, грядки, чужие здания, 2 сектора технике).
                    if (!Actions.MovementAction.canEnterHex(s, u, nb, seat)) {
                        continue;
                    }
                    Map<String, Object> mp = new HashMap<>();
                    mp.put("uid", u.uid);
                    mp.put("to", nb);
                    opts.add(new Choice("move", mp, u.type.code + "->" + nb));
                }
            }
            if (opts.isEmpty()) {
                break;
            }
            opts.add(new Choice("pass", null, "остановиться"));
            Choice ch = agent != null
                ? agent.choose(s, opts, Map.of("kind", "move"))
                : opts.get(opts.size() - 1);
            if (ch.payload() == null) {
                break;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> mp = (Map<String, Object>) ch.payload();
            int uid = asInt(mp.get("uid"));
            for (UnitToken u : pl.units) {
                if (u.uid == uid) {
                    String wasAt = u.hexId;
                    boolean wasInside = u.inside();     // снять ДО хода
                    u.setHexId((String) mp.get("to"));   // выводит из здания
                    // E3: печатный контейнер срабатывает так же, как при Движении
                    PrintedContainers.onUnitMoved(s, pl, wasAt, u.hexId, u.type, wasInside);
                    break;
                }
            }
            moved++;
        }
        return Map.of("moved", moved);
    }

    static Map<String, Object> deployUnits(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        int count = p.containsKey("count") ? asInt(p.get("count")) : 1;
        int placed = 0;
        for (BuildingToken b : pl.buildingsOnField()) {
            if (placed >= count) {
                break;
            }
            UnitType ut = Actions.ASSEMBLY_UNIT.get(b.type);
            if (ut == null || !b.powered()) {
                continue;
            }
            // Личный запас по роду (4 жетона), а не общий предел на цвет — см.
            // TokenStats.unitStock.
            if (pl.unitsOfKind(ut) < s.tokenStats.unitStock(ut)) {
                // Номер в запасе рода задаёт трофейный оборот жетона.
                UnitToken u = s.tokenStats.makeUnit(ut, seat, Placement.nextUid(s),
                    pl.unitsOfKind(ut));
                u.hexId = b.hexId;
                pl.units.add(u);
                // Разворот жетона — тоже НАКРЫТИЕ печатной ячейки.
                PrintedContainers.onUnitPlaced(s, pl, b.hexId, ut);
                placed++;
            }
        }
        return Map.of("deployed", placed);
    }

    static Map<String, Object> placeDamage(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        Agent agent = agentFor(s, seat);
        boolean finishOff = Boolean.TRUE.equals(p.get("finish_off"));
        java.util.Set<String> ownHexes = new java.util.HashSet<>();
        for (UnitToken u : pl.unitsOnField()) {
            ownHexes.add(u.hexId);
        }
        List<Token> victims = new ArrayList<>();
        for (PlayerState opp : s.players) {
            if (opp.seat == seat) {
                continue;
            }
            List<Token> toks = new ArrayList<>();
            toks.addAll(opp.unitsOnField());
            toks.addAll(opp.buildingsOnField());
            for (Token t : toks) {
                boolean adj = false;
                for (String h : ownHexes) {
                    if (t.hexId().equals(h) || s.field.neighbors(h).contains(t.hexId())) {
                        adj = true;
                        break;
                    }
                }
                if (!adj) {
                    continue;
                }
                if (finishOff) {
                    if (damageOf(t) >= Passives.effectiveHp(s, t) - 1) {
                        victims.add(t);
                    }
                } else {
                    victims.add(t);
                }
            }
        }
        if (victims.isEmpty()) {
            return Map.of("damaged", 0);
        }
        List<Choice> opts = new ArrayList<>();
        for (Token v : victims) {
            opts.add(new Choice("dmg", uidOf(v), "hit"));
        }
        Choice ch = agent != null ? agent.choose(s, opts, Map.of("kind", "place_damage")) : opts.get(0);
        int uid = (Integer) ch.payload();
        Token v = victims.get(0);
        for (Token t : victims) {
            if (uidOf(t) == uid) {
                v = t;
                break;
            }
        }
        setDamage(v, damageOf(v) + 1);
        boolean destroyed = damageOf(v) >= Passives.effectiveHp(s, v);
        if (destroyed && s.combat != null) {
            ((CombatResolver) s.combat).destroy(v, seat);
        }
        Map<String, Object> got = new HashMap<>();
        got.put("damaged", 1);
        got.put("destroyed", destroyed);
        return got;
    }

    static Map<String, Object> grabContainers(GameState s, int seat, Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        int count = p.containsKey("count") ? asInt(p.get("count")) : 1;
        // adjacent_only (пассив grab_adjacent_container): только гексы своего
        // присутствия и их соседи, не всё поле.
        java.util.Set<String> allowed = null;
        if (Boolean.TRUE.equals(p.get("adjacent_only"))) {
            allowed = new java.util.HashSet<>();
            for (BuildingToken b : pl.buildingsOnField()) {
                allowed.add(b.hexId);
                allowed.addAll(s.field.neighbors(b.hexId));
            }
            for (UnitToken u : pl.unitsOnField()) {
                allowed.add(u.hexId);
                allowed.addAll(s.field.neighbors(u.hexId));
            }
        }
        int got = 0;
        for (Hex h : s.field.hexes.values()) {
            if (got >= count) {
                break;
            }
            if (allowed != null && !allowed.contains(h.id)) {
                continue;
            }
            // «Собрать контейнеры с поля» — эффекта больше нет: жетонов на поле
            // не бывает. Оставляем выдачу ИЗ ЗАПАСА, чтобы карты с таким верхом
            // не превратились в пустышку.
            if (h.containerCell >= 0 && got < count) {
                got += Storage.addContainersCapped(s, pl, 1, "карта «собрать контейнеры»");
            }
        }
        return Map.of("grabbed", got);
    }

    // -- вспомогательные ------------------------------------------------------
    private static List<Token> damagedTokens(PlayerState pl) {
        List<Token> out = new ArrayList<>();
        for (UnitToken u : pl.units) {
            if (u.damage > 0) {
                out.add(u);
            }
        }
        for (BuildingToken b : pl.buildings) {
            if (b.damage > 0) {
                out.add(b);
            }
        }
        return out;
    }

    private static int damageOf(Token t) {
        return t instanceof UnitToken u ? u.damage : ((BuildingToken) t).damage;
    }

    private static void setDamage(Token t, int v) {
        if (t instanceof UnitToken u) {
            u.damage = v;
        } else {
            ((BuildingToken) t).damage = v;
        }
    }

    private static int uidOf(Token t) {
        return t instanceof UnitToken u ? u.uid : ((BuildingToken) t).uid;
    }
}
