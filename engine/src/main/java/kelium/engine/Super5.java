package kelium.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kelium.core.Agent;
import kelium.dataio.Ctx;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;

/**
 * СУПЕР-ЗАДАНИЯ 5.0 — «суперутиль или накопитель».
 *
 * <p>Механика по черновику дизайнера 25.08.2026 (см.
 * {@code design-docs/ЧЕРНОВИК — супер-задания 5.0 (12 карт).md}): каждому игроку
 * в подготовку втайне раздаётся ОДНА карта. Сыграть можно только одну половину:
 * <ul>
 *   <li><b>СУПЕРУТИЛЬ</b> — разовый эффект чудовищной силы. Сжигается
 *       СПЕЦ-действием в любой момент; накопитель при этом пропадает;</li>
 *   <li><b>НАКОПИТЕЛЬ</b> — победные очки в конце партии, если карта дожила до
 *       конца нетронутой. Считается по полю и открытой выкладке.</li>
 * </ul>
 *
 * <p>Прежняя конструкция (четыре ячейки, вскрытие, счётчик запуска, жетон
 * супероружия) в этом режиме не действует. Режим включается ключом
 * {@code super_objectives.mode: solo5} — старые своды не меняются.
 *
 * <p>Здесь и раздача, и все двенадцать утилей, и все двенадцать накопителей:
 * механика цельная, и растаскивать её по трём файлам значило бы получить ровно
 * тот класс ошибок «поле есть — никто не пишет», что чинился сегодня же.
 */
public final class Super5 {

    private Super5() {
    }

    /** Включён ли режим 5.0 («суперутиль или накопитель») этим сводом. */
    public static boolean on(GameState s) {
        return "solo5".equals(String.valueOf(Ctx.rules(s).get("super_objectives.mode", "")));
    }

    /**
     * Включён ли режим 6.0 («множитель в финале плюс жёсткое требование»).
     *
     * <p>ПОЧЕМУ 5.0 ОТМЕНЁН (правило дизайнера 31.08.2026). Разовый суперутиль
     * жгли сразу же и дружно: ждать его невыгодно никогда, поэтому карта уходила
     * из партии в первые ходы, и про неё забывали. Замер это и показал — 95–100%
     * карт сожжено. В 6.0 карта НЕ СЖИГАЕТСЯ ВОВСЕ: верх даёт множитель победных
     * очков в ФИНАЛЕ (значок финала вместо «всегда»), а прежний суперутиль стал
     * НАГРАДОЙ за жёсткое требование низа — то самое ограничение, которого не
     * хватало, чтобы эффектом нельзя было воспользоваться сразу.
     */
    public static boolean on6(GameState s) {
        return "solo6".equals(String.valueOf(Ctx.rules(s).get("super_objectives.mode", "")));
    }

    /** Раздать по одной карте втайне (зовёт Setup). */
    public static void deal(GameState s, List<String> ids, java.util.Random rng) {
        List<String> pool = new ArrayList<>(ids);
        java.util.Collections.shuffle(pool, rng);
        for (PlayerState p : s.players) {
            if (!pool.isEmpty()) {
                p.super5Card = pool.remove(pool.size() - 1);
            }
        }
    }

    // ==================================================================
    //  СУПЕРУТИЛИ
    // ==================================================================

    /**
     * Сжечь карту ради суперутиля. Возвращает журнал того, что произошло, —
     * он уходит в событие, потому что «эффект чудовищной силы» без следа в
     * логе неотличим от бага.
     */
    public static Map<String, Object> burn(GameState s, PlayerState p, Agent agent,
                                           java.util.function.Consumer<Map<String, Object>> emit) {
        String id = p.super5Card;
        Map<String, Object> got = new HashMap<>();
        if (id == null || p.super5Burned) {
            return got;
        }
        p.super5Burned = true;
        return выдать(s, p, agent, emit, id);
    }

    /**
     * КАРТЫ, КОТОРЫЕ ЭТОТ КОД УМЕЕТ РАЗЫГРЫВАТЬ.
     *
     * <p>Супер-задания семейства «одна карта втайне» не имеют классов-карт:
     * их половины разыгрывает этот класс, разбирая номер карты. Значит карта,
     * попавшая в данные без своей ветки здесь, МОЛЧА не делает ничего — та же
     * беда, от которой в остальных семействах сторожит «каждой карте из данных
     * есть код». Список ниже позволяет сторожить и это семейство: тест сверяет
     * номера из данных с ним.
     */
    private static final java.util.Set<String> ИЗВЕСТНЫЕ = java.util.Set.of(
        "s5_01", "s5_02", "s5_03", "s5_04", "s5_05", "s5_06",
        "s5_07", "s5_08", "s5_09", "s5_10", "s5_11", "s5_12");

    /** Умеет ли движок разыгрывать карту с этим номером. */
    public static boolean знает(String id) {
        return ИЗВЕСТНЫЕ.contains(id);
    }

    /**
     * НАГРАДА ЗА ВЫПОЛНЕННОЕ ТРЕБОВАНИЕ НИЗА (режим 6.0).
     *
     * <p>Эффекты те же, что были суперутилями в 5.0, — они и задумывались как
     * «супер-приколдес, какого обычным путём не получить». Разница в цене: в 5.0
     * их брали бесплатно и сразу, здесь за них надо выстроить жёсткое условие.
     * Карта при этом остаётся на столе и продолжает давать множитель верха.
     */
    public static Map<String, Object> наградаНиза(GameState s, PlayerState p, Agent agent,
                                                  java.util.function.Consumer<Map<String, Object>> emit) {
        if (p.super5Card == null) {
            return new HashMap<>();
        }
        return выдать(s, p, agent, emit, p.super5Card);
    }

    private static Map<String, Object> выдать(GameState s, PlayerState p, Agent agent,
                                              java.util.function.Consumer<Map<String, Object>> emit,
                                              String id) {
        Map<String, Object> got = new HashMap<>();
        switch (id) {
            case "s5_01" -> got.put("hired", смотрВойск(s, p));
            case "s5_02" -> got.put("gathered", дальнийРубеж(s, p, agent));
            case "s5_03" -> got.put("built", раскинутаяСеть(s, p, agent));
            case "s5_04" -> got.put("orders", гарнизоннаяСлужба(s, p, agent, 2));
            case "s5_05" -> got.put("cards", архивШтаба(s, p));
            case "s5_06" -> got.put("burned", оружейнаяПалата(s, p, emit));
            case "s5_07" -> got.put("actions", золотаяЖила(s, p, agent));
            case "s5_08" -> got.put("steps", перваяЛиния(s, p, agent));
            case "s5_09" -> got.put("razed", трофейныйОбоз(s, p, agent));
            case "s5_10" -> got.put("orders", штабнаяИгра(s, p, agent));
            case "s5_11" -> got.put("upgraded", неприкосновенныйЗапас(s, p, agent));
            case "s5_12" -> got.put("seal_gone", теньШтаба(p));
            default -> { }
        }
        return got;
    }

    /** s5_01: по одному жетону каждого рода бесплатно на свои гексы. */
    private static int смотрВойск(GameState s, PlayerState p) {
        int hired = 0;
        for (UnitType t : UnitType.values()) {
            UnitToken токен = null;
            for (UnitToken u : p.units) {
                if (u.hexId == null && u.type == t && !u.superUnit) {
                    токен = u;
                    break;
                }
            }
            if (токен == null) {
                continue;
            }
            String куда = гексПодЖетон(s, p, токен);
            if (куда != null) {
                токен.hexId = куда;
                hired++;
            }
        }
        return hired;
    }

    /** s5_02: собрать любые свои войска на один гекс и снять урон. */
    private static int дальнийРубеж(GameState s, PlayerState p, Agent agent) {
        List<Choice> opts = new ArrayList<>();
        for (Hex h : s.field.hexes.values()) {
            if (h.kind == kelium.core.HexKind.NORMAL) {
                opts.add(new Choice("hex", h.id, h.id));
            }
        }
        if (opts.isEmpty()) {
            return 0;
        }
        Choice ch = agent.choose(s, opts, Map.of("kind", "super5_gather"));
        String центр = (String) ch.payload();
        int moved = 0;
        for (UnitToken u : p.unitsOnField()) {
            u.damage = 0;
            if (центр.equals(u.hexId) || u.type == UnitType.TOWER) {
                continue;    // вышки неподвижны — правила движения уважаются
            }
            if (Actions.roomForUnit(s, центр, u.type)) {
                u.hexId = центр;
                moved++;
            }
        }
        return moved;
    }

    /** s5_03: три здания из запаса бесплатно, игнорируя энергию и зону. */
    private static int раскинутаяСеть(GameState s, PlayerState p, Agent agent) {
        int built = 0;
        for (int i = 0; i < 3; i++) {
            List<Choice> opts = new ArrayList<>();
            for (BuildingToken b : p.buildings) {
                if (b.hexId != null) {
                    continue;
                }
                for (Hex h : s.field.hexes.values()) {
                    if (h.kind != kelium.core.HexKind.NORMAL || h.hasNeutral()) {
                        continue;
                    }
                    if (h.fitsWithRepack(Placement.footprint(b.type), 0, 0)) {
                        opts.add(new Choice("place", new Object[]{b.uid, h.id},
                            b.type.code + "@" + h.id));
                    }
                }
            }
            if (opts.isEmpty()) {
                break;
            }
            opts.add(new Choice("pass", null, "хватит"));
            Choice ch = agent.choose(s, opts, Map.of("kind", "super5_build"));
            if (ch.payload() == null) {
                break;
            }
            Object[] pick = (Object[]) ch.payload();
            for (BuildingToken b : p.buildings) {
                if (b.uid == (Integer) pick[0]) {
                    b.hexId = (String) pick[1];
                    built++;
                    break;
                }
            }
        }
        return built;
    }

    /** s5_04 (два приказа) и часть s5_10 (один): все действия верха карт руки. */
    private static int гарнизоннаяСлужба(GameState s, PlayerState p, Agent agent, int сколько) {
        int played = 0;
        for (int i = 0; i < сколько; i++) {
            List<Choice> opts = new ArrayList<>();
            for (String cid : остатокРуки(s, p)) {
                opts.add(new Choice("order", cid, cid));
            }
            if (opts.isEmpty()) {
                break;
            }
            Choice ch = agent.choose(s, opts, Map.of("kind", "super5_order"));
            var card = Ctx.content(s).get("orders").byId(String.valueOf(ch.payload()));
            if (card == null) {
                continue;
            }
            String top = String.valueOf(((Map<String, Object>) card.get("top")).get("order"));
            for (String action : Order.ORDER_ACTIONS.getOrDefault(top, new String[0])) {
                var res = Actions.create(action, s)
                    .perform(p, new TurnContext(p.seat, 0), agent);
                if (res != null && res.ok()) {
                    s.journal.onAction(p.seat, action, res.telemetry());
                }
            }
            played++;
        }
        return played;
    }

    /** s5_05: всю витрину арсенала в руку и две карты задания. */
    private static int архивШтаба(GameState s, PlayerState p) {
        int got = 0;
        List<String> витрина = s.arsenalDisplay;
        if (витрина != null) {
            while (!витрина.isEmpty()) {
                if (!kelium.engine.Storage.takeArsenalCard(s, p, витрина.get(0))) {
                    break;                  // ячейки кончились — витрина остаётся
                }
                витрина.remove(0);
                got++;
            }
        }
        var deck = s.decks.get("objectives");
        for (int i = 0; i < 2; i++) {
            String c = deck.draw(s.rng);
            if (c != null) {
                p.objectiveHand.add(c);
                got++;
            }
        }
        return got;
    }

    /** s5_06: сжечь все установленные карты арсенала, получив их утили разом. */
    private static int оружейнаяПалата(GameState s, PlayerState p,
                                       java.util.function.Consumer<Map<String, Object>> emit) {
        int burned = 0;
        for (String cid : new ArrayList<>(p.arsenalInstalled)) {
            var card = Ctx.cards(s, "arsenal").find(cid);
            p.arsenalInstalled.remove(cid);
            if (card != null && card.get("top") instanceof Map<?, ?> t) {
                Map<String, Object> top = (Map<String, Object>) t;
                try {
                    Effects.apply(String.valueOf(top.getOrDefault("effect", "noop")), s, p.seat,
                        (Map<String, Object>) top.getOrDefault("params", Map.of()));
                } catch (Effects.EffectError e) {
                    // нечем воспользоваться — карта всё равно сожжена
                }
            }
            burned++;
        }
        return burned;
    }

    /** s5_07: каждый келемий = любое действие бесплатно. */
    private static int золотаяЖила(GameState s, PlayerState p, Agent agent) {
        int done = 0;
        while (p.resources.kelium() > 0) {
            List<Choice> opts = new ArrayList<>();
            for (String a : Actions.ALL_NAMES) {
                opts.add(new Choice("action", a, a));
            }
            opts.add(new Choice("pass", null, "хватит"));
            Choice ch = agent.choose(s, opts, Map.of("kind", "super5_kelium_action"));
            if (ch.payload() == null) {
                break;
            }
            p.resources.pay(Resource.KELIUM, 1);
            var res = Actions.create(String.valueOf(ch.payload()), s)
                .perform(p, new TurnContext(p.seat, 0), agent);
            if (res != null && res.ok()) {
                s.journal.onAction(p.seat, String.valueOf(ch.payload()), res.telemetry());
            }
            done++;
        }
        return done;
    }

    /** s5_08: четыре шага науки бесплатно (награды ячеек не выдаются). */
    private static int перваяЛиния(GameState s, PlayerState p, Agent agent) {
        int made = 0;
        for (int i = 0; i < 4; i++) {
            List<Choice> opts = new ArrayList<>();
            var caps = Ctx.rules(s).stepCapacity(s.numPlayers());
            for (String track : s.tech.tracks) {
                int step = p.techSteps.getOrDefault(track, 0);
                int to = step + 1;
                if (to > s.tech.steps) {
                    continue;
                }
                Integer cap = to - 1 < caps.size() ? caps.get(to - 1) : null;
                if (cap != null && s.tech.occupancy.get(track).get(to - 1).size() >= cap) {
                    continue;    // ячейка занята — бесплатный шаг не перепрыгивает
                }
                opts.add(new Choice("track", track, track + " -> " + to));
            }
            if (opts.isEmpty()) {
                break;
            }
            Choice ch = agent.choose(s, opts, Map.of("kind", "super5_science"));
            String track = (String) ch.payload();
            int step = p.techSteps.getOrDefault(track, 0);
            p.techSteps.put(track, step + 1);
            s.tech.moveCube(track, p.seat, step, step + 1);
            made++;
        }
        return made;
    }

    /** s5_09: уничтожить чужое здание (кроме ЦУ) без боя и забрать на место уничтоженных жетонов. */
    private static String трофейныйОбоз(GameState s, PlayerState p, Agent agent) {
        List<Choice> opts = new ArrayList<>();
        for (PlayerState враг : s.players) {
            if (враг.seat == p.seat) {
                continue;
            }
            for (BuildingToken b : враг.buildingsOnField()) {
                if (b.type != BuildingType.COMMAND_CENTER) {
                    opts.add(new Choice("victim", b, b.type.code + "@" + b.hexId
                        + " игрока " + враг.seat));
                }
            }
        }
        if (opts.isEmpty()) {
            return null;
        }
        Choice ch = agent.choose(s, opts, Map.of("kind", "super5_raze"));
        BuildingToken b = (BuildingToken) ch.payload();
        if (s.combat instanceof CombatResolver cr) {
            cr.destroy(b, p.seat);
        }
        return b.type.code;
    }

    /** s5_10: жетон первого игрока себе и немедленно один полный приказ. */
    private static int штабнаяИгра(GameState s, PlayerState p, Agent agent) {
        s.firstPlayer = p.seat;
        return гарнизоннаяСлужба(s, p, agent, 1);
    }

    /** s5_11: два добытчика или станции — на четвёртый уровень. */
    private static int неприкосновенныйЗапас(GameState s, PlayerState p, Agent agent) {
        int done = 0;
        for (int i = 0; i < 2; i++) {
            List<Choice> opts = new ArrayList<>();
            for (BuildingToken b : p.buildingsOnField()) {
                if ((b.type == BuildingType.MINER || b.type == BuildingType.POWER_PLANT)
                        && b.level != null && b.level < 4) {
                    opts.add(new Choice("up", b.uid, b.type.code + " L" + b.level));
                }
            }
            if (opts.isEmpty()) {
                break;
            }
            opts.add(new Choice("pass", null, "хватит"));
            Choice ch = agent.choose(s, opts, Map.of("kind", "super5_upgrade"));
            if (ch.payload() == null) {
                break;
            }
            for (BuildingToken b : p.buildingsOnField()) {
                if (b.uid == (Integer) ch.payload()) {
                    поднятьДоЧетвёртого(s, b);
                    done++;
                    break;
                }
            }
        }
        return done;
    }

    private static void поднятьДоЧетвёртого(GameState s, BuildingToken b) {
        int былоУровень = b.level == null ? 1 : b.level;
        if (b.type == BuildingType.MINER) {
            b.level = 4;
            b.hp = s.tokenStats.buildingHp(BuildingType.MINER, 4);
            b.energySlots = s.tokenStats.minerEnergySlots(4);
        } else {
            b.level = 4;
            b.hp = s.tokenStats.buildingHp(BuildingType.POWER_PLANT, 4);
            // Выработка на жетоне не хранится — она считается по уровню.
            // Прибавка приходит простаивающими кубиками на саму станцию, как
            // при постройке нового источника.
            b.energyIdle += Math.max(0, s.tokenStats.plantEnergyGives(4)
                - s.tokenStats.plantEnergyGives(былоУровень));
        }
    }

    /** s5_12: свой глухой жетон уходит из игры навсегда. */
    private static boolean теньШтаба(PlayerState p) {
        UnitType где = null;
        for (var e : p.redPlacements.entrySet()) {
            if (Boolean.TRUE.equals(e.getValue().get("blocks"))) {
                где = e.getKey();
                break;
            }
        }
        if (где != null) {
            p.redPlacements.remove(где);
        }
        p.super5SealRemoved = true;
        return где != null;
    }

    // ==================================================================
    //  НАКОПИТЕЛИ — очки в конце партии, если карта дожила нетронутой
    // ==================================================================

    /**
     * ВЕРХ КАРТЫ В ОЧКАХ.
     *
     * <p>В режиме 5.0 это «накопитель»: платит, только если карта дожила до конца
     * нетронутой. В режиме 6.0 это МНОЖИТЕЛЬ ФИНАЛА, и он платит ВСЕГДА: карта
     * не сжигается ни при каких условиях, а награда низа его не отменяет.
     */
    public static int stockpileVp(GameState s, int seat) {
        PlayerState p = s.player(seat);
        if (p.super5Card == null) {
            return 0;
        }
        if (p.super5Burned && !on6(s)) {
            return 0;
        }
        return switch (p.super5Card) {
            case "s5_01" -> {
                java.util.Set<UnitType> роды = new java.util.HashSet<>();
                for (UnitToken u : p.unitsOnField()) {
                    роды.add(u.type);
                }
                yield Math.min(8, 2 * роды.size());
            }
            case "s5_02" -> {
                int n = 0;
                for (UnitToken u : p.unitsOnField()) {
                    if (рядомЧужоеЗдание(s, seat, u.hexId)) {
                        n++;
                    }
                }
                yield Math.min(5, n);
            }
            case "s5_03" -> {
                java.util.Set<String> гексы = new java.util.HashSet<>();
                for (BuildingToken b : p.buildingsOnField()) {
                    гексы.add(b.hexId);
                }
                yield Math.min(6, гексы.size());
            }
            case "s5_04" -> {
                java.util.Set<String> зд = new java.util.HashSet<>();
                for (BuildingToken b : p.buildingsOnField()) {
                    зд.add(b.hexId);
                }
                int n = 0;
                for (UnitToken u : p.unitsOnField()) {
                    if (зд.remove(u.hexId)) {
                        n++;
                    }
                }
                yield Math.min(6, 2 * n);
            }
            case "s5_05" -> Math.min(6, 2 * Math.max(0, p.objectivesCompleted - 3));
            case "s5_06" -> Math.min(6, 2 * Math.max(0, p.allInstalledArsenal().size() - 2));
            case "s5_07" -> Math.min(5, p.resources.kelium());
            case "s5_08" -> {
                int n = 0;
                for (String track : s.tech.tracks) {
                    int мой = p.techSteps.getOrDefault(track, 0);
                    if (мой == 0) {
                        continue;
                    }
                    boolean выше = true;
                    for (PlayerState o : s.players) {
                        if (o.seat != seat && o.techSteps.getOrDefault(track, 0) >= мой) {
                            выше = false;
                            break;
                        }
                    }
                    if (выше) {
                        n++;
                    }
                }
                yield Math.min(6, 2 * n);
            }
            case "s5_09" -> p.super5CuEverLost ? 0 : Math.min(6, p.killsTotal);
            case "s5_10" -> Math.min(6, 2 * p.super5RoundsFirst);
            case "s5_11" -> {
                int n = 0;
                for (BuildingToken b : p.buildingsOnField()) {
                    if (b.level != null && b.level == 1) {
                        n++;
                    }
                }
                yield Math.min(6, 2 * n);
            }
            case "s5_12" -> {
                if (p.super5CuEverLost) {
                    yield 0;
                }
                int n = 3;
                for (PlayerState o : s.players) {
                    if (o.seat != seat && o.super5CuEverLost) {
                        n += 1;
                    }
                }
                yield Math.min(6, n);
            }
            default -> 0;
        };
    }

    // ==================================================================
    //  НИЗ КАРТЫ 6.0 — ЖЁСТКОЕ ТРЕБОВАНИЕ
    // ==================================================================

    /**
     * ВЫПОЛНЕНО ЛИ ЖЁСТКОЕ ТРЕБОВАНИЕ НИЗА (режим 6.0).
     *
     * <p>Требования нарочно тяжелее обычных заданий и НЕ совпадают с тем, за что
     * платит множитель верха: если бы совпадали, карта платила бы дважды за одно
     * и то же, и выбора внутри карты не осталось бы. Каждое требование — либо
     * состояние поля, которое надо выстроить нарочно, либо счётчик партии.
     *
     * <p>ЧЕРНОВИК СОСТАВА: сами требования подобраны под темы карт и ждут ревью
     * дизайнера; механика от их точных чисел не зависит.
     */
    public static boolean требованиеВыполнено(GameState s, int seat) {
        PlayerState p = s.player(seat);
        if (p.super5Card == null) {
            return false;
        }
        switch (p.super5Card) {
            case "s5_01": {
                // Три РАЗНЫХ рода на одном гексе — смотр в прямом смысле.
                Map<String, java.util.Set<UnitType>> поГексам = new HashMap<>();
                for (UnitToken u : p.unitsOnField()) {
                    поГексам.computeIfAbsent(u.hexId,
                        k -> new java.util.HashSet<>()).add(u.type);
                }
                for (java.util.Set<UnitType> в : поГексам.values()) {
                    if (в.size() >= 3) {
                        return true;
                    }
                }
                return false;
            }
            case "s5_02": {
                // Своё войско рядом с ЧУЖИМ ЦУ — самое опасное место на поле.
                for (UnitToken u : p.unitsOnField()) {
                    for (String nb : s.field.neighbors(u.hexId)) {
                        for (PlayerState o : s.players) {
                            if (o.seat == seat) {
                                continue;
                            }
                            for (BuildingToken b : o.buildingsOnField()) {
                                if (b.type == BuildingType.COMMAND_CENTER
                                        && nb.equals(b.hexId)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
                return false;
            }
            case "s5_03": {
                // Здания на пяти разных гексах — сеть, а не куча.
                java.util.Set<String> гексы = new java.util.HashSet<>();
                for (BuildingToken b : p.buildingsOnField()) {
                    гексы.add(b.hexId);
                }
                return гексы.size() >= 5;
            }
            case "s5_04": {
                // Три гекса, где стоят одновременно здание и войско.
                java.util.Set<String> зд = new java.util.HashSet<>();
                for (BuildingToken b : p.buildingsOnField()) {
                    зд.add(b.hexId);
                }
                int n = 0;
                for (UnitToken u : p.unitsOnField()) {
                    if (зд.remove(u.hexId)) {
                        n++;
                    }
                }
                return n >= 3;
            }
            case "s5_05":
                return p.objectivesCompleted >= 4;
            case "s5_06":
                return p.allInstalledArsenal().size() >= 3;
            case "s5_07":
                return p.resources.kelium() >= 5;
            case "s5_08": {
                // Быть выше всех сразу на ДВУХ треках.
                int n = 0;
                for (String track : s.tech.tracks) {
                    int мой = p.techSteps.getOrDefault(track, 0);
                    if (мой == 0) {
                        continue;
                    }
                    boolean выше = true;
                    for (PlayerState o : s.players) {
                        if (o.seat != seat && o.techSteps.getOrDefault(track, 0) >= мой) {
                            выше = false;
                            break;
                        }
                    }
                    if (выше) {
                        n++;
                    }
                }
                return n >= 2;
            }
            case "s5_09":
                return p.killsTotal >= 4 && !p.super5CuEverLost;
            case "s5_10":
                return p.super5RoundsFirst >= 2;
            case "s5_11": {
                // Четыре здания первого уровня — широкая дешёвая сеть.
                int n = 0;
                for (BuildingToken b : p.buildingsOnField()) {
                    if (b.level != null && b.level == 1) {
                        n++;
                    }
                }
                return n >= 4;
            }
            case "s5_12": {
                // Твоё ЦУ цело, а у соперника уже снесли: война идёт, но не у тебя.
                if (p.super5CuEverLost) {
                    return false;
                }
                for (PlayerState o : s.players) {
                    if (o.seat != seat && o.super5CuEverLost) {
                        return true;
                    }
                }
                return false;
            }
            default:
                return false;
        }
    }

    // ==================================================================
    //  Мелкая механика
    // ==================================================================

    private static boolean рядомЧужоеЗдание(GameState s, int seat, String hex) {
        if (hex == null) {
            return false;
        }
        for (String nb : s.field.neighbors(hex)) {
            for (PlayerState o : s.players) {
                if (o.seat == seat) {
                    continue;
                }
                for (BuildingToken b : o.buildingsOnField()) {
                    if (nb.equals(b.hexId)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String гексПодЖетон(GameState s, PlayerState p, UnitToken u) {
        for (BuildingToken b : p.buildingsOnField()) {
            if (Actions.roomForUnit(s, b.hexId, u.type)) {
                return b.hexId;
            }
        }
        return null;
    }

    /** Карты приказов руки, не вскрытые в этом раунде. */
    private static List<String> остатокРуки(GameState s, PlayerState p) {
        List<String> все = new ArrayList<>();
        for (var e : Ctx.content(s).get("orders").entries) {
            if (p.orderColor != null && p.orderColor.equals(e.get("color"))) {
                все.add(String.valueOf(e.get("id")));
            }
        }
        все.removeAll(p.orderPlayed);
        все.remove(p.orderSetAside);
        return все;
    }
}
