package kelium.agents;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.Order;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.dataio.Ctx;
import kelium.engine.Placement;

/**
 * HeuristicAgent — настраиваемый бот со стратегическим «характером». Порт из
 * forge/agents/heuristic.py.
 *
 * <p>Бот получает в каждой точке решения список опций (Choice) и оценивает
 * каждую, выбирая лучшую (ничьи разрешает ГСЧ по сиду). Поведение задаётся
 * весами {@code w} (числовые приоритеты) и «характером» (aggressor / defender /
 * economist / balanced) — набором весов и порогов. Три характера адекватно
 * реагируют друг на друга: агрессор идёт в атаку, защитник копит оборону и
 * контратакует, экономист тихо растёт, но огрызается при нападении.
 */
public class HeuristicAgent extends Agent {

    /** Базовые веса — «сбалансированный» характер. */
    public static final Map<String, Double> DEFAULT_WEIGHTS = new HashMap<>();

    /** Характеры: дельты к весам + пороги поведения. */
    public static final Map<String, Map<String, Double>> PERSONALITIES = new HashMap<>();

    static {
        DEFAULT_WEIGHTS.put("action.build", 7.0);
        DEFAULT_WEIGHTS.put("action.assembly", 6.5);
        DEFAULT_WEIGHTS.put("action.mining", 6.0);
        DEFAULT_WEIGHTS.put("action.energy_swap", 6.0);
        DEFAULT_WEIGHTS.put("action.movement", 5.0);
        DEFAULT_WEIGHTS.put("action.combat", 5.5);
        DEFAULT_WEIGHTS.put("action.science", 4.0);
        DEFAULT_WEIGHTS.put("action.market", 2.0);
        DEFAULT_WEIGHTS.put("action.pass", 0.0);
        DEFAULT_WEIGHTS.put("aggression", 1.0);
        DEFAULT_WEIGHTS.put("military_build", 1.0);
        // ПОДГАДЫВАНИЕ ЧУЖОГО ПРИКАЗА: 0 — не смотреть на соперников вовсе,
        // 1 — учитывать риск совпадения и шанс открыть низ (12.08.2026).
        DEFAULT_WEIGHTS.put("read_opponent", 1.0);

        PERSONALITIES.put("aggressor", Map.of(
            "action.combat", 9.0, "action.movement", 8.0, "action.assembly", 8.0,
            "action.build", 7.0, "aggression", 2.5, "military_build", 2.5));
        PERSONALITIES.put("defender", Map.of(
            "action.build", 8.0, "action.assembly", 7.0, "action.combat", 6.0,
            "action.movement", 4.0, "aggression", 1.2, "military_build", 1.8));
        PERSONALITIES.put("economist", Map.of(
            "action.mining", 9.0, "action.build", 7.0, "action.science", 6.0,
            "action.market", 4.0, "action.combat", 3.0, "action.movement", 3.0,
            "aggression", 0.5, "military_build", 0.6));
        PERSONALITIES.put("balanced", Map.of());
    }

    public final String personality;
    /** Веса поведения. Доступны подклассам (например {@link StrategicAgent}). */
    protected final Map<String, Double> w;
    protected final Random rng;

    public HeuristicAgent(int seat, Random rng, String personality) {
        super(seat, personality + "#" + seat);
        this.personality = personality;
        this.w = new HashMap<>(DEFAULT_WEIGHTS);
        this.w.putAll(PERSONALITIES.getOrDefault(personality, Map.of()));
        this.rng = rng != null ? rng : new Random();
    }

    /**
     * Конструктор для подкласса-стратега: явные веса (геном) + свои имя/характер.
     * Недостающие ключи добираются из {@link #DEFAULT_WEIGHTS}.
     */
    protected HeuristicAgent(int seat, Random rng, String name, String personality,
                             Map<String, Double> weights) {
        super(seat, name);
        this.personality = personality;
        this.w = new HashMap<>(DEFAULT_WEIGHTS);
        if (weights != null) {
            this.w.putAll(weights);
        }
        this.rng = rng != null ? rng : new Random();
    }

    /** Фабрика: создать эвристического бота заданного характера. */
    public static HeuristicAgent makePersonality(int seat, String name, Random rng) {
        return new HeuristicAgent(seat, rng, name);
    }

    protected double wget(String k) {
        return w.getOrDefault(k, 1.0);
    }

    @Override
    public Choice choose(GameState state, List<Choice> options, Map<String, Object> context) {
        String kind = context != null ? String.valueOf(context.getOrDefault("kind", "")) : "";
        List<Choice> opts = new ArrayList<>(options);
        java.util.function.BiFunction<GameState, Choice, Double> scorer = scorerFor(kind, context);
        if (scorer == null) {
            List<Choice> reals = new ArrayList<>();
            for (Choice o : opts) {
                if (!"pass".equals(o.kind()) && o.payload() != null) {
                    reals.add(o);
                }
            }
            return reals.isEmpty() ? opts.get(0) : reals.get(rng.nextInt(reals.size()));
        }
        double best = Double.NEGATIVE_INFINITY;
        List<Choice> top = new ArrayList<>();
        for (Choice o : opts) {
            double sc = scorer.apply(state, o);
            if (sc > best) {
                best = sc;
                top.clear();
                top.add(o);
            } else if (sc == best) {
                top.add(o);
            }
        }
        return top.get(rng.nextInt(top.size()));
    }

    protected java.util.function.BiFunction<GameState, Choice, Double> scorerFor(String kind, Map<String, Object> ctx) {
        return switch (kind) {
            case "reveal_order" -> (s, o) -> scoreReveal(s, o);
            case "action" -> (s, o) -> scoreAction(s, o);
            case "build_pick" -> (s, o) -> scoreBuildPick(s, o);
            case "build_hex" -> (s, o) -> scoreBuildHex(s, o, ctx);
            case "build_facing" -> (s, o) -> scoreBuildFacing(s, o, ctx);
            case "assemble" -> (s, o) -> scoreAssemble(s, o);
            case "move" -> (s, o) -> scoreMove(s, o);
            case "combat_source" -> (s, o) -> scoreCombatSource(s, o);
            case "combat_target" -> (s, o) -> scoreCombatTarget(s, o);
            case "attack" -> (s, o) -> scoreAttack(s, o);
            case "pay_power" -> (s, o) -> scorePayPower(s, o, ctx);
            case "module_place_red" -> (s, o) -> scoreModuleRed(s, o);
            case "module_place_blue" -> (s, o) -> scoreModuleBlue(s, o);
            // K3: выбор гекса-исхода Смены энергии — ценим по числу простаивающих
            // кубиков его источников (их можно пустить в дело); наценка штрафует.
            case "energy_hex" -> (s, o) -> {
                if (o.payload() == null && !"energy_storage".equals(o.kind())) {
                    return 0.5;   // pass
                }
                double surcharge = ctx != null && ctx.get("surcharge") instanceof Number n
                    ? n.doubleValue() : 0.0;
                if ("energy_storage".equals(o.kind())) {
                    return 1.2;
                }
                String hid = (String) o.payload();
                int idle = 0;
                for (BuildingToken b : s.player(seat).buildingsOnField()) {
                    if (hid.equals(b.hexId)
                            && (b.type == BuildingType.POWER_PLANT
                                || b.type == BuildingType.COMMAND_CENTER)) {
                        idle += b.energyIdle;
                    }
                }
                // есть недозапитанные потребители — простаивающие кубики ценны
                int hungry = 0;
                for (BuildingToken b : s.player(seat).buildingsOnField()) {
                    hungry += Math.max(0, b.energySlots - b.energyPlaced);
                }
                double v = Math.min(idle, hungry) * 1.5 - surcharge * 1.2;
                return v > 0 ? 1.0 + v : 0.3;
            };
            // K3: раскладка кубика — приоритет ЦУ > добытчики > военные > прочее
            // (та же логика, что была зашита в движок, теперь решает бот).
            case "energy_place" -> (s, o) -> {
                if (o.payload() == null) {
                    return 0.2;   // оставить простаивать — почти всегда хуже
                }
                int uid = ((Number) o.payload()).intValue();
                for (BuildingToken b : s.player(seat).buildingsOnField()) {
                    if (b.uid == uid) {
                        double base = switch (b.type) {
                            case COMMAND_CENTER -> 4.0;
                            case MINER -> 3.5;
                            case BARRACKS, FACTORY, AIRBASE -> 2.5;
                            default -> 1.0;
                        };
                        // добить здание до запитанности ценнее, чем начать новое
                        if (b.energyPlaced + 1 >= b.energySlots) {
                            base += 1.0;
                        }
                        return base;
                    }
                }
                return 0.5;
            };
            // Сторона жетона хранилища (навсегда): энергия при дефиците, иначе ячейка.
            case "storage_side" -> (s, o) ->
                "+1_energy".equals(o.payload())
                    ? (spareEnergy(s, s.player(seat)) <= 0 ? 3.0 : 1.0)
                    : 2.0;
            // Слепой сброс (правило дизайнера): лишаемся НАИМЕНЕЕ нужного
            // приказа на раунд — обратная оценка ценности розыгрыша.
            case "blind_discard" -> (s, o) -> Math.max(0.1, 12.0 - scoreReveal(s, o));
            // Выбор жертвы (K4): добить раненого > дожать повреждённого > ЦУ/здание.
            case "combat_victim" -> (s, o) -> {
                Token t = (Token) o.payload();
                int dmg = t instanceof UnitToken u ? u.damage : ((BuildingToken) t).damage;
                int hp = kelium.engine.Passives.effectiveHp(s, t);
                double v = 1.0 + dmg * 0.8;
                if (dmg + 1 >= hp) {
                    v += 5.0;   // этот удар добивает
                }
                if (t instanceof BuildingToken b) {
                    v += b.type == BuildingType.COMMAND_CENTER ? 3.0 : 1.0;
                }
                return v;
            };
            // Выбор нейтрала под снос (K4): ближе к сносу > богаче наградой.
            case "neutral_victim" -> (s, o) -> {
                kelium.core.Hex.NeutralBuilding nb =
                    (kelium.core.Hex.NeutralBuilding) o.payload();
                return 4.0 - nb.hp + (nb.big ? 0.5 : 0.0);
            };
            // Выбор трека науки (K2): ценим ПО шага, жетоны на шагах 2-3 и
            // вершину (3 ПО + супер-арсенал). Раньше это решал движок.
            case "sci_track" -> (s, o) -> {
                if ("pass".equals(o.kind())) {
                    return 0.4;
                }
                // Вариант несёт ТРЕК И ЦЕЛЕВОЙ ШАГ: с 13.08.2026 ячейки можно
                // ПЕРЕПРЫГИВАТЬ, то есть уйти сразу дальше, заплатив за все
                // пройденные шаги. Бонусы перепрыгнутых ячеек не достаются, а вот
                // победные очки в конце считаются за все пройденные шаги — поэтому
                // прыжок ценится по НАКОПЛЕННЫМ очкам, а бонус — только за ту
                // ячейку, куда встал.
                Object[] pick = (Object[]) o.payload();
                String track = (String) pick[0];
                int reached = (Integer) pick[1];
                int step = s.player(seat).techSteps.getOrDefault(track, 0);
                List<Integer> stepVp = Ctx.rules(s)
                    .getIntList("tech.step_vp_cumulative");
                double vpGain = 0;
                for (int to = step + 1; to <= reached; to++) {
                    vpGain += to - 1 < stepVp.size() ? stepVp.get(to - 1) : 0;
                }
                double v = 1.0 + vpGain * 2.0;
                if (reached == 2 || reached == 3) {
                    v += 1.2;   // жетон модуля на ячейке приземления
                }
                if (reached == 4) {
                    v += 2.5;   // вершина: 3 ПО + карта супер-арсенала
                }
                // Прыжок ценен ещё и тем, что обгоняет столпившихся впереди, но
                // стоит дороже: за каждый перепрыгнутый шаг платится полная цена.
                int jumped = reached - step - 1;
                if (jumped > 0) {
                    v -= 0.4 * jumped;
                }
                return v;
            };
            // Оплата трофеями (K5): минимизируем сгорающий излишек — жетон,
            // который покрывает остаток с наименьшей переплатой; иначе крупный.
            case "trophy_pay" -> (s, o) -> {
                int remaining = ctx != null && ctx.get("remaining") instanceof Number n
                    ? n.intValue() : 1;
                int v = ((kelium.core.Token) o.payload()).trophyValue();
                return v >= remaining ? 10.0 - (v - remaining) : 1.0 + v * 0.1;
            };
            // ПРАВИЛО 4 (2026-08-15): движок предлагает это, ТОЛЬКО когда без
            // выброса поступление всё равно обрежется нехваткой места (см.
            // Storage.offerStorageDiscard) — значит "pass" здесь означает
            // «пусть лучше обрежется входящее», а не «мне ничего не мешает».
            // Пока needed > 0 — выгоднее выбросить САМЫЙ ДЕШЁВЫЙ по очкам кубик
            // (учитывая ЖИВОЙ курс рулсета: на варианте «келемий = 0 ПО» бот не
            // должен по инерции беречь келемий, раз он обесценен).
            case "storage_discard" -> (s, o) -> {
                int needed = ctx != null && ctx.get("needed") instanceof Number n
                    ? n.intValue() : 0;
                if (needed <= 0) {
                    return "pass".equals(o.kind()) ? 10.0 : 0.5;
                }
                if ("pass".equals(o.kind())) {
                    return 0.0;   // не мешать нужному выбросу, пока needed > 0
                }
                return 10.0 - storageDiscardCost(s, (kelium.core.Resource) o.payload());
            };
            case "mine" -> (s, o) -> scoreMine(s, o);
            case "market" -> (s, o) -> scoreMarket(s, o);
            case "sci_exchange" -> (s, o) -> scoreSciExchange(s, o);
            // B9: движок присылает context kind = "open_container" (Choice.kind
            // опций = container_variant) — скорер обязан висеть на контексте.
            case "open_container" -> (s, o) -> scoreContainerVariant(s, o);
            case "spec" -> (s, o) -> scoreSpec(s, o);
            // ПОДСУНУТЬ КАРТУ ПОД ПЛАНШЕТ ради символа — свободное решение, но
            // не бесплатное по смыслу: карта арсенала уходит из руки, контейнер
            // не будет вскрыт ради содержимого. Значит подсовывать стоит только
            // под требование своего супер задания.
            case "tuck" -> (s, o) -> scoreTuck(s, o);
            case "mass_open" -> (s, o) -> scoreSpec(s, o);   // те же виды опций
            default -> null;
        };
    }

    // ================= анализ обстановки =================================
    private boolean threatened(GameState state) {
        PlayerState me = state.player(seat);
        Set<String> myHexes = new HashSet<>();
        for (Token t : allOnField(me)) {
            myHexes.add(t.hexId());
        }
        for (PlayerState pl : state.players) {
            if (pl.seat == seat) {
                continue;
            }
            for (UnitToken u : pl.unitsOnField()) {
                if (myHexes.contains(u.hexId)) {
                    return true;
                }
                for (String nb : state.field.neighbors(u.hexId)) {
                    if (myHexes.contains(nb)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Расстояние до ближайшего вражеского жетона — тем же поиском, которым
     * ходит движок. Раньше здесь лежала построчная копия чужого обхода, к тому
     * же не знавшая правил проходимости.
     */
    private Integer nearestEnemyDist(GameState state, String fromHex) {
        Set<String> enemyHexes = new HashSet<>();
        for (PlayerState pl : state.players) {
            if (pl.seat == seat) {
                continue;
            }
            for (Token t : allOnField(pl)) {
                enemyHexes.add(t.hexId());
            }
        }
        return kelium.engine.Movement.distance(state, fromHex, enemyHexes);
    }

    private boolean enemyAdjacent(GameState state) {
        PlayerState me = state.player(seat);
        for (UnitToken u : me.unitsOnField()) {
            if (adjacentTargetValue(state, u.hexId) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Категории целей, которые юнит МОЖЕТ бить: ОСН + ВТР (с учётом красного модуля). */
    protected java.util.Set<kelium.core.Target> attackCategories(GameState state, UnitToken u) {
        java.util.Set<kelium.core.Target> cats = new java.util.HashSet<>();
        if (state.player(seat).board.troop.dualCell()) {
            // БОЙ 2.0: универсальная ячейка достаёт любую категорию всегда.
            cats.addAll(java.util.List.of(kelium.core.Target.values()));
            return cats;
        }
        kelium.core.Target[] rows = state.player(seat).board.troop.attacks(u.type);
        if (rows == null) {
            return cats;
        }
        cats.add(rows[0]);
        Map<String, Object> mod = kelium.engine.Modules.redModuleOn(state.player(seat), u.type);
        if (mod == null) {
            cats.add(rows[1]);
        } else {
            for (String code : (String[]) mod.get("targets")) {
                cats.add(kelium.core.Target.fromCode(code));
            }
        }
        return cats;
    }

    /** Категория цели — свойство самого жетона ({@link Token#category()}). */
    protected static kelium.core.Target categoryOfToken(Token t) {
        return t.category();
    }

    /**
     * Ценность целей, СМЕЖНЫХ с гексом, которые юниты С ЭТОГО гекса реально
     * ПРОБИВАЮТ по таблице атак (с учётом красных модулей); нейтралы требуют
     * строки «здания-вышки». 0 = бой отсюда будет ПУСТЫМ. Ключ к живой войне:
     * и выбор действия «Бой», и выбор источника смотрят на эту величину.
     */
    protected double adjacentTargetValue(GameState state, String src) {
        // Если бой уже связан с резолвером — спросить у НЕГО (та же логика,
        // что в бою: закрытые гексы, скрытые юниты, стоимость, модули).
        if (state.combat instanceof kelium.engine.CombatResolver cr) {
            return cr.attackableValue(seat, src);
        }
        List<UnitToken> mine = new java.util.ArrayList<>();
        for (UnitToken u : state.player(seat).unitsOnField()) {
            if (src.equals(u.hexId)) {
                mine.add(u);
            }
        }
        if (mine.isEmpty()) {
            return 0;
        }
        java.util.Set<kelium.core.Target> cats = new java.util.HashSet<>();
        for (UnitToken u : mine) {
            cats.addAll(attackCategories(state, u));
        }
        double v = 0;
        for (String nb : state.field.neighbors(src)) {
            for (PlayerState pl : state.players) {
                if (pl.seat == seat) {
                    continue;
                }
                for (Token t : allOnField(pl)) {
                    if (nb.equals(t.hexId()) && cats.contains(categoryOfToken(t))) {
                        v += 1.0;
                        if (t instanceof BuildingToken b && b.type == BuildingType.COMMAND_CENTER) {
                            v += 2.0;
                        }
                    }
                }
            }
            if (state.field.get(nb).hasNeutral()
                    && cats.contains(kelium.core.Target.BUILDINGS_TOWERS)) {
                v += 0.8;
            }
        }
        return v;
    }

    /**
     * Платить ли монетами за недостающую энергию (только Разработка, только на
     * ЭТО действие). Логика живого игрока: плата разумна, когда здание реально
     * что-то произведёт и когда деньги не последние — иначе лучше потерпеть и
     * довезти кубик Сменой энергии, после чего платить больше не придётся.
     */
    private double scorePayPower(GameState state, Choice o, Map<String, Object> ctx) {
        if (!Boolean.TRUE.equals(o.payload())) {
            return 1.0;   // «не платить» — нейтральный вариант
        }
        PlayerState me = state.player(seat);
        int cost = ctx != null && ctx.get("cost") instanceof Number n ? n.intValue() : 99;
        int coins = me.resources.coin();
        if (coins < cost) {
            return 0.0;
        }
        String type = ctx != null ? String.valueOf(ctx.get("type")) : "";
        // Что даст запитанное здание прямо сейчас — за пустую работу не платим.
        boolean worth;
        if ("miner".equals(type)) {
            boolean kelRoom = kelium.engine.Storage.keliumMax(me) > me.resources.kelium();
            boolean contRoom = kelium.engine.Storage.containerCapacity(state, me) > me.containers;
            worth = kelRoom || contRoom;
        } else {
            worth = kelium.engine.Storage.ammoMax(me) > me.resources.ammo();
        }
        if (!worth) {
            return 0.0;
        }
        // 5 монет = 1 ПО, поэтому монета дорога: платим охотно за дешёвое
        // включение и неохотно за дорогое, и тем охотнее, чем больше денег.
        double afford = (double) (coins - cost) / 3.0;
        return Math.max(0.2, 3.2 - 1.1 * cost + Math.min(afford, 2.0));
    }

    /**
     * ПЕРЕНОС уже стоящего здания. Раньше все варианты переноса получали ровно
     * 1.0 — больше, чем пас (0.1). Из-за этого бот с пустым кошельком КАЖДЫЙ ход
     * бесплатно мотал ЦУ туда-обратно между двумя гексами: ход за ходом, партию
     * за партией, ничего не меняя. Перенос осмыслен только тогда, когда новое
     * место ЛУЧШЕ старого; иначе он хуже паса.
     */
    private double scoreMovePick(GameState state, Map<String, Object> spec) {
        PlayerState me = state.player(seat);
        int uid = spec.get("uid") instanceof Number n ? n.intValue() : -1;
        int cost = spec.get("cost") instanceof Number c ? c.intValue() : 0;
        BuildingToken b = null;
        for (BuildingToken x : me.buildingsOnField()) {
            if (x.uid == uid) {
                b = x;
                break;
            }
        }
        if (b == null) {
            return 0.05;
        }
        if (b.type == BuildingType.MINER) {
            // добытчик переносим ТОЛЬКО с выработанной жилы на живую
            var live = Plan.liveTileHexes(state);
            boolean nowUseless = !Plan.touchesLiveTile(state, b.hexId, live);
            boolean somewhereBetter = false;
            for (String hid : kelium.engine.Placement.buildableHexes(state, seat)) {
                if (!hid.equals(b.hexId) && Plan.touchesLiveTile(state, hid, live)) {
                    somewhereBetter = true;
                    break;
                }
            }
            return nowUseless && somewhereBetter ? 6.0 - 0.3 * cost : 0.05;
        }
        // ЦУ и прочие здания: своё место уже выбрано, таскать их незачем.
        // (Перенос ЦУ бесплатен, но бесплатный бессмысленный ход — всё равно
        // потерянная операция Стройки.)
        return 0.05;
    }

    /**
     * Есть ли на моих гексах место хоть под одно новое наземное войско.
     * Без этой проверки Сборка игралась «в никуда»: здание запитано, а поставить
     * произведённое некуда — действие сгорало вхолостую (42% розыгрышей).
     */
    private boolean anyHexHasRoom(GameState state, PlayerState me) {
        java.util.Set<String> hexes = new java.util.LinkedHashSet<>();
        for (BuildingToken b : me.buildingsOnField()) {
            if (b.hexId != null) {
                hexes.add(b.hexId);
            }
        }
        for (String hid : hexes) {
            kelium.core.Hex h = state.field.get(hid);
            if (h == null) {
                continue;
            }
            int veh = 0;
            int single = 0;
            for (PlayerState p : state.players) {
                for (UnitToken u : p.unitsOnField()) {
                    if (hid.equals(u.hexId) && u.type != UnitType.AIRCRAFT) {
                        if (u.type == UnitType.VEHICLE) {
                            veh++;
                        } else {
                            single++;
                        }
                    }
                }
            }
            if (h.fitsWithRepack(1, veh, single)) {
                return true;
            }
        }
        return false;
    }

    /** Стоит ли хоть один мой юнит рядом с целью, которую он ПРОБИВАЕТ. */
    protected boolean combatOpportunity(GameState state) {
        for (UnitToken u : state.player(seat).unitsOnField()) {
            if (adjacentTargetValue(state, u.hexId) > 0) {
                return true;
            }
        }
        return false;
    }

    // ================= выбор приказа (reveal) ============================
    @SuppressWarnings("unchecked")
    private double scoreReveal(GameState state, Choice o) {
        PlayerState me = state.player(seat);
        Map<String, Object> card = Ctx.cards(state, "orders").byId((String) o.payload());
        if (Boolean.TRUE.equals(card.get("joker"))) {
            return 6.0;
        }
        Order top = Order.fromCode((String) card.get("top"));
        double val = 0;
        for (String a : Order.ORDER_ACTIONS.get(top)) {
            val += w.getOrDefault("action." + a, 1.0);
        }
        // ИНДИКАТОРЫ ЗАДАНИЙ (заказ дизайнера 17.08.2026, продолжение пункта 3
        // плана: «привести всё к тому, чтобы собирать войска под конкретную
        // цель»). Раньше бот выбирал верх приказа только по своей общей пользе
        // и не видел, что именно ЭТОТ верх открывает действия, которыми ПРЯМО
        // СЕЙЧАС закрывается карта задания в руке — задания жглись куда чаще,
        // чем выполнялись (см. javadoc ObjectiveHints). Бонус считает движок,
        // а не бот: ObjectiveHints уже знает, каким действием закрывается
        // разрыв каждой карты.
        val += objectiveActionBonus(state, Order.ORDER_ACTIONS.get(top));
        int nUnits = me.unitsOnField().size();
        int nMil = 0;
        for (BuildingToken b : me.buildingsOnField()) {
            if (b.type == BuildingType.BARRACKS || b.type == BuildingType.FACTORY
                    || b.type == BuildingType.AIRBASE) {
                nMil++;
            }
        }
        if (top == Order.OPERATION && nUnits >= 1 && (threatened(state) || wget("aggression") >= 2.0)) {
            val += 6.0 * wget("aggression");
        }
        if (top == Order.DEVELOPMENT && nMil >= 1) {
            val += 3.0;
        }
        if (top == Order.INFRASTRUCTURE && nMil == 0) {
            val += 3.0;
        }
        // Есть трофеи для трат на треки науки -> охотнее берём Приобретения.
        if (top == Order.ACQUISITIONS && trophyPool(me) >= 1) {
            val += 4.0;
        }
        // ЦЕПОЧКА РАУНДА (порядок розыгрыша карт внутри раунда!):
        // 1) ОПЕРАЦИЯ РАНЬШЕ, пока есть чем и кого бить — успеем сдать добычу;
        // 2) захватил жетоны -> НЕМЕДЛЕННО Приобретения (наука), пока трофеи
        //    не вернулись владельцам в Возврат;
        // 3) армии нет, а бить хочется -> сперва Разработка (сборка).
        if (top == Order.OPERATION && combatOpportunity(state)
                && me.resources.ammo() >= 1) {
            val += 7.0;
        }
        if (top == Order.ACQUISITIONS && me.trophySpacePoints() > 0) {
            val += 8.0 + me.trophySpacePoints();
        }
        if (top == Order.DEVELOPMENT && nMil >= 1 && nUnits < 3
                && wget("aggression") >= 0.8) {
            val += 3.0;
        }

        // ПОДГАДАТЬ ЧУЖОЙ ПРИКАЗ (вопрос дизайнера 12.08.2026: «а учитывают ли
        // боты, что можно сыграть другой приказ, чтобы не заблочиться?»). Раньше —
        // нет: карта выбиралась только по своей пользе. Правило совпадения режет
        // верх с двух действий до одного, а нижняя половина, наоборот, ОТКРЫВАЕТСЯ,
        // если этот приказ сверху вскрыл кто-то другой. Значит оба конца карты
        // зависят от чужого выбора, и его надо прикидывать.
        //
        // Считаем по ОТКРЫТОЙ информации: состав цветной колоды одинаков и
        // известен, разыгранные карты лежат в открытую. Значит остаток руки
        // соперника выводится вычитанием. Отложенную вслепую карту НЕ учитываем —
        // это скрытая информация, подглядывать нельзя.
        double read = wget("read_opponent");   // 0 = не подгадывать вовсе
        double riskTop = read * chanceSomeoneReveals(state, top);
        double perAction = val / Math.max(1, Order.ORDER_ACTIONS.get(top).length);
        val -= riskTop * perAction;                       // потеря второго действия

        Object bottomCode = card.get("bottom");
        if (bottomCode != null) {
            Order bo = Order.fromCode(bottomCode.toString());
            double openChance = chanceSomeoneReveals(state, bo);
            double bottomValue = 0;
            for (String a : Order.ORDER_ACTIONS.get(bo)) {
                bottomValue += w.getOrDefault("action." + a, 1.0);
            }
            // низ даёт ОДНО действие — берём среднюю пользу действия этого приказа
            val += read * openChance * bottomValue
                / Math.max(1, Order.ORDER_ACTIONS.get(bo).length);
        }
        return val;
    }

    /**
     * НАСКОЛЬКО ВЕРХ ПРИКАЗА ПРИБЛИЖАЕТ ЗАДАНИЯ В РУКЕ. Спрашивает
     * {@link kelium.engine.ObjectiveHints#forHand} с УЖЕ ОГРАНИЧЕННЫМ набором
     * действий — теми двумя, что даёт этот конкретный верх — и суммирует
     * ценность карт, у которых при таком ограничении нашёлся план (готовых
     * учитывать не нужно: они не зависят от выбора действия, их и так закроет
     * СПЕЦ). {@code actionsLeft=2}: столько действий у верха приказа всегда
     * (см. {@link Order#ORDER_ACTIONS}), а точнее для оценки заранее не нужно —
     * это прикидка «стоит ли вообще идти в эту сторону», а не гарантия плана.
     *
     * <p>Доля, а не полная цена: одна карта задания — не повод бросить всю
     * остальную стратегию ради нужного действия, но при прочих равных верх,
     * который её продвигает, должен перевешивать тот, что не продвигает ничего.
     */
    private double objectiveActionBonus(GameState state, String[] actions) {
        PlayerState me = state.player(seat);
        if (me.objectiveHand.isEmpty() || state.journal == null) {
            return 0;
        }
        List<String> avail = List.of(actions);
        double bonus = 0;
        for (kelium.engine.ObjectiveHints.Hint h
                : kelium.engine.ObjectiveHints.forHand(state, seat, state.journal, avail, 2)) {
            if (h.reachable()) {
                bonus += h.maxValue() * 0.25;
            }
        }
        return bonus;
    }

    /**
     * Вероятность, что ХОТЯ БЫ ОДИН соперник вскроет сверху этот приказ.
     *
     * <p>Остаток руки соперника = все карты его цвета минус уже разыгранные (и то,
     * и другое — открытая информация). Вероятность, что он вскроет приказ X,
     * считаем как долю таких карт в остатке; вероятность «хоть кто-то» —
     * через произведение обратных.
     */
    private double chanceSomeoneReveals(GameState state, Order order) {
        double none = 1.0;
        for (PlayerState p : state.players) {
            if (p.seat == seat || p.orderColor == null) {
                continue;
            }
            int left = 0;
            int match = 0;
            for (Map<String, Object> card : Ctx.cards(state, "orders").entries) {
                if (!p.orderColor.equals(String.valueOf(card.get("deck")))) {
                    continue;
                }
                String id = String.valueOf(card.get("id"));
                if (p.orderPlayed.contains(id)) {
                    continue;                     // эту он уже вскрыл в этом раунде
                }
                left++;
                Object topCode = card.get("top");
                if (topCode != null && Order.fromCode(topCode.toString()) == order) {
                    match++;
                }
            }
            if (left > 0) {
                none *= 1.0 - (double) match / left;
            }
        }
        return 1.0 - none;
    }

    /** Трофейный пул игрока = очки жетонов на трофейном поле + чёрные кубы. */
    private static int trophyPool(PlayerState me) {
        return me.trophySpacePoints() + me.resources.debris();
    }

    // ================= выбор действия ===================================
    private double scoreAction(GameState state, Choice o) {
        if ("pass".equals(o.kind())) {
            return -1.0;
        }
        String name = (String) o.payload();
        double base = w.getOrDefault("action." + name, 1.0);
        PlayerState me = state.player(seat);
        int nUnits = me.unitsOnField().size();
        if ("combat".equals(name)) {
            // Бой имеет смысл только когда он НЕ пустой: юнит в контакте с
            // целью И есть боеприпасы хотя бы на одну атаку.
            if (me.resources.ammo() < 1 || !combatOpportunity(state)) {
                return 0.1;
            }
            return base + 4.0 * wget("aggression") + 2.0;
        }
        if ("movement".equals(name)) {
            if (nUnits == 0) {
                return 0.1;
            }
            // Армия есть, но в контакте никого — сближение важнее всего.
            double closeIn = !combatOpportunity(state) && nUnits >= 2 ? 2.0 : 0.0;
            return base + 2.0 * wget("aggression") + closeIn;
        }
        if ("assembly".equals(name)) {
            // Запитанного военного здания мало: если и войско поставить некуда,
            // и боеприпас положить некуда — действие сгорит впустую.
            boolean poweredMil = false;
            for (BuildingToken b : me.buildingsOnField()) {
                if (b.powered() && (b.type == BuildingType.BARRACKS || b.type == BuildingType.FACTORY
                        || b.type == BuildingType.AIRBASE || b.type == BuildingType.COMMAND_CENTER)) {
                    poweredMil = true;
                    break;
                }
            }
            // Разработка позволяет докупить недостающую энергию монетами
            // (одноразово, на это действие) — значит здание «включаемо», если
            // денег хватает хотя бы на одну ячейку.
            boolean payable = false;
            for (BuildingToken b : me.buildingsOnField()) {
                if (Plan.isMilitary(b.type) && !b.powered()
                        && me.resources.coin() >= b.energySlots - b.energyPlaced) {
                    payable = true;
                    break;
                }
            }
            if (!poweredMil && !payable) {
                return 0.1;
            }
            boolean ammoRoom = kelium.engine.Storage.ammoMax(me) > me.resources.ammo();
            boolean unitRoom = anyHexHasRoom(state, me);
            if (!ammoRoom && !unitRoom) {
                return 0.2;
            }
            // АРМИЯ НУЖНА, А СБОРКУ НЕ БЕРУТ. Замер 12.08.2026: Сборка
            // предлагалась 19 раз за партию на игрока, а выбиралась 2.6 — боты
            // почти всегда играли Добычу из того же приказа, и войск на поле не
            // набиралось даже на кулак из двух жетонов. Пока подвижных войск
            // мало, Сборка стоит выше добычи келемия, которого и так девать некуда.
            int mobile = 0;
            for (UnitToken u : me.unitsOnField()) {
                if (u.type != UnitType.TOWER) {
                    mobile++;
                }
            }
            double need = mobile >= 4 ? 0.0 : (4 - mobile) * 1.6;
            return base + (unitRoom ? need : 0.0);
        }
        if ("mining".equals(name)) {
            // Добыча имеет смысл, когда есть ЗАПИТАННЫЙ добытчик и есть куда
            // класть: келемий — в хранилище, контейнер — в свой лимит.
            boolean poweredMiner = false;
            boolean liveVein = false;
            java.util.Set<String> live = Plan.liveTileHexes(state);
            for (BuildingToken b : me.buildingsOnField()) {
                if (b.type == BuildingType.MINER && b.powered()) {
                    poweredMiner = true;
                    if (Plan.touchesLiveTile(state, b.hexId, live)) {
                        liveVein = true;
                    }
                }
            }
            boolean minerPayable = false;
            for (BuildingToken b : me.buildingsOnField()) {
                if (b.type == BuildingType.MINER && !b.powered()
                        && me.resources.coin() >= b.energySlots - b.energyPlaced
                        && Plan.touchesLiveTile(state, b.hexId, live)) {
                    minerPayable = true;
                    break;
                }
            }
            if (!poweredMiner && !minerPayable) {
                return 0.1;
            }
            if (minerPayable) {
                liveVein = true;
            }
            boolean kelRoom = kelium.engine.Storage.keliumMax(me) > me.resources.kelium();
            boolean contRoom = kelium.engine.Storage.containerCapacity(state, me) > me.containers;
            if (liveVein && kelRoom) {
                return base + 3.0;
            }
            return contRoom ? base : 0.2;
        }
        if ("market".equals(name)) {
            // Продавать нечего — маркет пустой ход. Келемий нужен ЛЮБОЙ сделке.
            if (me.resources.kelium() <= 0) {
                return 0.15;
            }
            // чем острее нужда в деньгах/патронах, тем ценнее размен
            double need = (me.resources.coin() <= 2 ? 2.5 : 0.0)
                + (me.resources.ammo() <= 1 ? 1.5 : 0.0);
            return base + need;
        }
        // Наука ценна ТОЛЬКО когда есть чем платить (трофеи) и есть открытый шаг.
        // Тогда поднимаем её высоко: треки — крупный источник ПО (до 7 за трек).
        if ("science".equals(name)) {
            int pool = trophyPool(me);
            if (pool <= 0 || !hasAffordableTechStep(state, me, pool)) {
                return 0.1;
            }
            // ЦЕПОЧКА ВОЙНЫ: захваченные жетоны на трофейном поле ВЕРНУТСЯ
            // владельцам в конце раунда — их надо СДАТЬ В НАУКУ СЕЙЧАС.
            // Несданный трофей = бой был напрасным.
            double urgency = me.trophySpacePoints() > 0 ? 6.0 + me.trophySpacePoints() : 0.0;
            return Math.max(base, 8.0) + Math.min(pool, 6) + urgency;
        }
        return base;
    }

    /** Есть ли вообще незанятый шаг трека (по вместимости), куда ещё можно расти. */
    private boolean hasOpenTechStep(GameState state, PlayerState me) {
        var rs = Ctx.rules(state);
        List<Integer> caps = rs.stepCapacity(state.numPlayers());
        for (String track : state.tech.tracks) {
            int step = me.techSteps.getOrDefault(track, 0);
            if (step >= state.tech.steps) {
                continue;
            }
            Integer cap = caps.get(step);
            if (cap == null || state.tech.occupancy.get(track).get(step).size() < cap) {
                return true;
            }
        }
        return false;
    }

    /** Есть ли открытый (по вместимости) шаг трека, доступный за трофейный пул. */
    private boolean hasAffordableTechStep(GameState state, PlayerState me, int pool) {
        var rs = Ctx.rules(state);
        List<Integer> costs = rs.getIntList("tech.step_cost_trophy");
        List<Integer> caps = rs.stepCapacity(state.numPlayers());
        for (String track : state.tech.tracks) {
            int step = me.techSteps.getOrDefault(track, 0);
            if (step >= state.tech.steps) {
                continue;
            }
            Integer cap = caps.get(step);
            if (cap != null && state.tech.occupancy.get(track).get(step).size() >= cap) {
                continue;
            }
            if (pool >= costs.get(step)) {
                return true;
            }
        }
        return false;
    }

    /** Как выше, но шаг должен давать ПОЛОЖИТЕЛЬНЫЕ ПО (шаг 1 = 0 ПО не считаем). */
    private boolean hasAffordableVpTechStep(GameState state, PlayerState me, int pool) {
        var rs = Ctx.rules(state);
        List<Integer> costs = rs.getIntList("tech.step_cost_trophy");
        List<Integer> stepVp = rs.getIntList("tech.step_vp_cumulative");
        List<Integer> caps = rs.stepCapacity(state.numPlayers());
        for (String track : state.tech.tracks) {
            int step = me.techSteps.getOrDefault(track, 0);
            if (step >= state.tech.steps) {
                continue;
            }
            Integer cap = caps.get(step);
            if (cap != null && state.tech.occupancy.get(track).get(step).size() >= cap) {
                continue;
            }
            // ближайший ПО-положительный шаг на этом треке и суммарная цена до него
            int cum = 0;
            for (int s = step; s < state.tech.steps; s++) {
                cum += costs.get(s);
                if (stepVp.get(s) > 0) {
                    if (pool >= cum) {
                        return true;
                    }
                    break;
                }
            }
        }
        return false;
    }

    // ================= постройка ========================================
    /**
     * Порядок постройки базы С УЧЁТОМ ЭНЕРГИИ. Ключевой урок: военное здание
     * бесполезно, пока его нечем запитать (незапитанное здание не собирает
     * войска) — это была причина, почему боты не воевали и не набирали ПО.
     * Поэтому здание строится ТОЛЬКО когда хватает свободной энергии его
     * запитать; иначе приоритет отдаётся энергостанции. Затем — добытчики (под
     * экономику), затем военные здания (по характеру), затем прочее.
     */
    @SuppressWarnings("unchecked")
    private double scoreBuildPick(GameState state, Choice o) {
        if ("pass".equals(o.kind())) {
            return 0.1;
        }
        // B10: снос своего здания — payload=uid (Integer), не спец-Map. Эвристика
        // почти никогда не сносит (ниже паса), сохраняя возможность для RL.
        if ("demolish_pick".equals(o.kind())) {
            return 0.05;
        }
        Map<String, Object> spec = (Map<String, Object>) o.payload();
        String label = String.valueOf(spec.getOrDefault("label", ""));
        if ("move_pick".equals(o.kind())) {
            return scoreMovePick(state, spec);
        }
        PlayerState me = state.player(seat);
        int nPlants = 0;
        int nMiners = 0;
        int nMil = 0;
        for (BuildingToken b : me.buildings) {
            if (b.type == BuildingType.POWER_PLANT) {
                nPlants++;
            } else if (b.type == BuildingType.MINER) {
                nMiners++;
            } else if (b.type == BuildingType.BARRACKS || b.type == BuildingType.FACTORY
                    || b.type == BuildingType.AIRBASE) {
                nMil++;
            }
        }
        int spareEnergy = spareEnergy(state, me);
        double base = 1.0;
        // Уровень (1..4) для добытчиков/станций — выше уровень = больше ячеек
        // склада (L3/L4 дают по 2), больше выработки и VP-звезда на L4. Это
        // прямой рычаг под НАКОПЛЕНИЕ КЕЛЕМИЯ (1 ПО за штуку, но упирается в
        // склад) — поэтому слегка поощряем более высокий уровень.
        int lvl = spec.get("level") instanceof Number ln ? ln.intValue() : 0;
        double lvlBonus = lvl >= 3 ? 1.5 : 0.0;
        if (label.startsWith("plant")) {
            // Энергостанции критичны: строим их, пока энергии в обрез
            // (нужна под добытчики И под военные здания).
            base = (spareEnergy <= 0 ? 12.0 : (nPlants <= nMiners ? 7.0 : 4.0)) + lvlBonus;
        } else if (label.startsWith("miner")) {
            // Добытчики — источник келемия (=ПО) и складских ячеек: держим
            // высокий аппетит, пока их меньше, чем станций, дающих им энергию.
            base = (nMiners < nPlants + 1 ? 8.0 : 4.0) + lvlBonus;
        } else if (label.equals("barracks") || label.equals("factory") || label.equals("airbase")) {
            int need = militarySlots(state, label);
            if (spareEnergy < need) {
                // Запитать нечем, и монетой ячейку НЕ закрыть (такого правила
                // нет). Но здание можно построить впрок: недостающие кубики
                // придут со следующей станцией и лягут Сменой энергии. Ценим
                // тем выше, чем ближе энергия: не хватает 1 кубика — почти
                // норм, не хватает 3 (авиабаза с нуля) — рано.
                int gap = need - Math.max(0, spareEnergy);
                base = Math.max(0.4, 2.2 - 0.8 * gap);
            } else {
                double want = 3.0 + 4.0 * wget("military_build");
                base = want - 1.5 * nMil;
            }
        }
        int cost = spec.get("cost") instanceof Number n ? n.intValue() : 0;
        return base - 0.1 * cost;
    }

    /** Сколько ячеек энергии требует военное здание данного вида. */
    /**
     * Сколько энергии требует военное здание — ПО ДАННЫМ игры, а не по
     * зашитым в бота числам: дизайнер меняет энергоёмкость в файлах, и бот
     * обязан считать так же, как движок.
     */
    private static int militarySlots(GameState state, String label) {
        try {
            return state.tokenStats.buildingEnergySlots(
                kelium.core.BuildingType.fromCode(label), null);
        } catch (RuntimeException e) {
            return 1;
        }
    }

    /**
     * Свободная энергия игрока = суммарная выработка (энергостанции + ЦУ +
     * жетон хранилища) минус суммарная потребность зданий-потребителей на поле.
     * Показывает, хватит ли энергии запитать ещё одно здание.
     */
    protected int spareEnergy(GameState state, PlayerState me) {
        int supply = 0;
        int demand = 0;
        for (BuildingToken b : me.buildingsOnField()) {
            if (b.type == BuildingType.POWER_PLANT) {
                // Через движок, а не по номиналу уровня: станция вне ЖЁЛТОЙ
                // ЯЧЕЙКИ даёт 1 кубик, каким бы ни был её уровень.
                supply += kelium.engine.Power.plantOutput(state, b);
            } else if (b.type == BuildingType.COMMAND_CENTER) {
                supply += state.tokenStats.buildingEnergyGives(BuildingType.COMMAND_CENTER);
                demand += b.energySlots;
            } else {
                demand += b.energySlots;
            }
        }
        for (String tok : me.storageTokens) {
            if ("+1_energy".equals(tok)) {
                supply += 1;
            }
        }
        return supply - demand;
    }

    /**
     * Выбор гекса под здание. КРИТИЧНО для ДОБЫТЧИКА: он добывает келемий только
     * с СОСЕДНЕЙ грядки (тайла зарождения). Ставим добытчик впритык к грядке с
     * келемием — иначе он добывает 0 (частая причина пустой экономики → пустых
     * трофеев → пустой науки). Для прочих зданий гекс безразличен.
     */
    private double scoreBuildHex(GameState state, Choice o, Map<String, Object> ctx) {
        if ("pass".equals(o.kind())) {
            return 0.1;
        }
        String btype = ctx != null ? String.valueOf(ctx.get("btype")) : "";
        String hid = (String) o.payload();
        if ("miner".equals(btype)) {
            // добытчик на самой грядке или впритык к ней — большой бонус
            if (gridWithKeliumAt(state, hid)) {
                return 10.0;
            }
            for (String nb : state.field.neighbors(hid)) {
                if (gridWithKeliumAt(state, nb)) {
                    return 10.0;
                }
            }
            return 1.0;
        }
        if ("power_plant".equals(btype)) {
            // ЖЁЛТАЯ ЯЧЕЙКА: только на ней станция даёт свой номинал. Гекс, где
            // она уже накрыта чужим зданием или стенкой нейтрала, для станции
            // почти бесполезен — там она выдаст 1 кубик любого уровня.
            kelium.core.Hex h = state.field.get(hid);
            if (h == null || h.energyCell < 0) {
                return 1.0;   // поле без разметки — правило не действует
            }
            return h.sideOwner[h.energyCell] == null ? 9.0 : 0.5;
        }
        if ("factory".equals(btype) || "airbase".equals(btype) || "barracks".equals(btype)) {
            // УДАРНОЕ ЗДАНИЕ СТАВИМ БЛИЖЕ К ПРОТИВНИКУ. Раньше этот метод не
            // различал гексы вообще (плоская 1.0) — бот мог поставить завод в
            // дальнем углу своей зоны, и весь набег потом уходил на марш через
            // полполя вместо боя (замечание дизайнера 16.08.2026: реальные
            // партии держат базы в 2-3 гексах друг от друга — тянуться некуда).
            int dist = distanceToNearestEnemy(state, hid);
            if (dist < 0) {
                return 1.0;   // соперник не найден на поле — оценивать нечем
            }
            // ближе — лучше; авиабаза бьёт с шагом 2 (сама долетает дальше),
            // поэтому ей чуть меньше важна вплотную-близость, чем заводу.
            double farPenalty = "airbase".equals(btype) ? 0.6 : 1.0;
            return 8.0 - farPenalty * dist;
        }
        return 1.0;
    }

    /**
     * Кратчайшее расстояние от гекса {@code hid} до ближайшего гекса с чужим
     * живым войском или зданием. −1, если на поле нет ни одного видимого
     * соперника (самое начало партии).
     */
    private int distanceToNearestEnemy(GameState state, String hid) {
        Set<String> enemyHexes = new HashSet<>();
        for (PlayerState p : state.players) {
            if (p.seat == seat) {
                continue;
            }
            for (UnitToken u : p.units) {
                if (u.hexId != null && u.alive()) {
                    enemyHexes.add(u.hexId);
                }
            }
            for (BuildingToken b : p.buildingsOnField()) {
                enemyHexes.add(b.hexId);
            }
        }
        if (enemyHexes.isEmpty()) {
            return -1;
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        Map<String, Integer> dist = new HashMap<>();
        dist.put(hid, 0);
        queue.add(hid);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            int d = dist.get(cur);
            if (enemyHexes.contains(cur)) {
                return d;
            }
            for (String nb : state.field.neighbors(cur)) {
                if (!dist.containsKey(nb)) {
                    dist.put(nb, d + 1);
                    queue.add(nb);
                }
            }
        }
        return -1;
    }

    /**
     * ПОВОРОТ здания (какими стенками оно встаёт). Стенка — это одновременно и
     * лопата добытчика, и труба роста зоны стройки: копать и расти можно ТОЛЬКО
     * через свою стенку. Поэтому:
     * <ul>
     *   <li>добытчик — стенка к живой грядке важнее всего (иначе он добывает 0);</li>
     *   <li>любое здание — стенки в поле (есть сосед), а не в пустоту за краем:
     *       так растёт зона стройки;</li>
     *   <li>мелочью учитываем свободные соседние гексы (место под рост) и не
     *       садимся на ячейку с печатным контейнером — он под жетоном пропадает.</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private double scoreBuildFacing(GameState state, Choice o, Map<String, Object> ctx) {
        List<Integer> sides = (List<Integer>) o.payload();
        if (sides == null || sides.isEmpty()) {
            return 0.1;
        }
        String hid = ctx != null ? String.valueOf(ctx.get("hex")) : null;
        String btype = ctx != null ? String.valueOf(ctx.get("btype")) : "";
        kelium.core.Hex h = hid == null ? null : state.field.get(hid);
        if (h == null) {
            return 1.0;
        }
        boolean minerOnGrid = "miner".equals(btype) && gridWithKeliumAt(state, hid);
        double v = 1.0;
        for (int side : sides) {
            String nb = h.neighborBySide[side];
            if (nb == null) {
                continue;                   // стенка в пустоту за краем поля
            }
            v += 0.6;                       // стенка в поле — зона стройки растёт
            kelium.core.Hex n = state.field.get(nb);
            if (gridWithKeliumAt(state, nb)) {
                if ("miner".equals(btype) && !minerOnGrid) {
                    v += 8.0;               // лопата смотрит в жилу
                } else {
                    // не-добытчик, занявший окно к грядке, закрывает его
                    // навсегда: встать на саму грядку нельзя, а копать можно
                    // только своей стенкой отсюда.
                    v -= 2.0;
                }
            }
            if (n != null && !n.hasNeutral() && n.spawnTile == null && n.freeSectors() > 0) {
                v += 0.3;                   // есть куда расширяться
            }
            if (h.containerCell == side) {
                v -= 0.5;                   // накрываем печатный контейнер
            }
            if ("power_plant".equals(btype) && h.energyCell == side) {
                // ЖЁЛТАЯ ЯЧЕЙКА: станция мимо неё даёт 1 кубик вместо номинала —
                // самый дорогой поворот в игре, дороже любой стенки в поле.
                v += 12.0;
            }
        }
        return v;
    }

    /** Есть ли на гексе живой источник келемия (грядка с kelium > 0, не убрана). */
    private boolean gridWithKeliumAt(GameState state, String hexId) {
        kelium.core.Hex h = state.field.get(hexId);
        return h != null && h.spawnTile != null
            && h.spawnTile.kelium > 0;   // B1: оборот тоже добываем
    }

    // ================= сборка ===========================================
    @SuppressWarnings("unchecked")
    private double scoreAssemble(GameState state, Choice o) {
        if (o.payload() == null) {
            return 0.2;   // пропуск здания — почти всегда хуже производства
        }
        PlayerState me = state.player(seat);
        int nUnits = me.unitsOnField().size();
        int ammo = me.resources.ammo();
        double agg = wget("aggression");
        int ammoTarget = 4 + (int) (2 * agg) + nUnits / 4;
        Map<String, Object> pl = (Map<String, Object>) o.payload();
        if ("ammo".equals(pl.get("kind"))) {
            if (ammo < ammoTarget) {
                return 4.0 + (ammoTarget - ammo) * 0.5;
            }
            return 0.5;
        }
        if (ammo < 2 && agg >= 1.0) {
            return 1.0;
        }
        double scarce = nUnits < 6 ? 3.0 : 1.5;
        double value = scarce * (0.8 + agg);

        // ДОСЯГАЕМОСТЬ: вышка неподвижна (скорость 0) — она никогда не дойдёт до
        // врага. Замер 12.08.2026: на 11.9 действий Движения выходило 13.9 шагов,
        // потому что подвижных жетонов у бота почти нет, зато вышек по 1–3. Пока
        // вышек хватает на обороне, производим ПОДВИЖНЫЕ войска, а этот выход
        // ценим дешевле — тогда действие Движения двигает кого-то реального.
        UnitType made = producedUnit(state, pl);
        if (made == UnitType.TOWER) {
            int towers = 0;
            int mobile = 0;
            for (UnitToken u : me.unitsOnField()) {
                if (u.type == UnitType.TOWER) {
                    towers++;
                } else {
                    mobile++;
                }
            }
            boolean threatened = threatened(state);
            if (towers >= 1 && !threatened) {
                value *= 0.35;                 // одна вышка на обороне уже есть
            }
            if (towers > mobile) {
                value *= 0.5;                  // войско, которое не ходит, копится
            }
        }
        return value;
    }

    /** Какое войско выйдет из этого здания при Сборке (null — это выход БПР). */
    @SuppressWarnings("unchecked")
    private UnitType producedUnit(GameState state, Map<String, Object> pl) {
        if (!"unit".equals(pl.get("kind")) || !(pl.get("building") instanceof Number n)) {
            return null;
        }
        for (BuildingToken b : state.player(seat).buildingsOnField()) {
            if (b.uid == n.intValue()) {
                return switch (b.type) {
                    case BARRACKS -> UnitType.INFANTRY;
                    case FACTORY -> UnitType.VEHICLE;
                    case AIRBASE -> UnitType.AIRCRAFT;
                    case COMMAND_CENTER -> UnitType.TOWER;
                    default -> null;
                };
            }
        }
        return null;
    }

    // ================= движение =========================================
    @SuppressWarnings("unchecked")
    private double scoreMove(GameState state, Choice o) {
        double agg = wget("aggression");
        if ("pass".equals(o.kind())) {
            return agg >= 1.0 ? 0.2 : 0.6;
        }
        Map<String, Object> pl = (Map<String, Object>) o.payload();
        String dest = (String) pl.get("to");
        Integer d = nearestEnemyDist(state, dest);
        if (d == null) {
            return 0.6;
        }
        // d==1 — идеальная ДИСТАНЦИЯ УДАРА (сосед врага, можно атаковать).
        // d==0 — встать НА гекс врага: с него по этому врагу бить нельзя (бой
        // требует цель на СОСЕДНЕМ гексе), поэтому это хуже, чем d==1.
        if (d == 1) {
            return 12.0 * (0.6 + agg);
        }
        if (d == 0) {
            return 4.0;
        }
        // ДОСЯГАЕМОСТЬ: шаг ИЗ ДВУХ гексов в один — самый ценный после выхода на
        // дистанцию удара, потому что следующим же действием можно бить. Раньше
        // все дальние шаги стоили почти одинаково, и войска оставались «в двух
        // гексах» до конца партии (замер 12.08.2026: 127 из 334 жетонов).
        Integer from = nearestEnemyDist(state, fromHexOf(state, pl));
        double closer = from != null && d < from ? 2.5 : 0.0;   // реально сближаемся
        // КУЛАК, А НЕ РАСТОПЫРЕННЫЕ ПАЛЬЦЫ (замечание дизайнера 12.08.2026).
        // Замер: максимум своих войск на одном гексе — 1.25, то есть жетоны
        // ползли к врагу порознь и умирали порознь. Идти НА ГЕКС, где уже стоит
        // своё войско, ценнее: с одного гекса бьют все, кто на нём стоит, и по
        // одиночке их не выбить.
        double group = friendsAt(state, dest) * (d <= 2 ? 3.0 : 1.2);
        if (d == 1) {
            return 12.0 * (0.6 + agg) + group;
        }
        if (d == 2) {
            return 7.0 * (0.6 + agg) + closer + group;
        }
        return 3.0 + (agg * 4.0) / d + closer + group;
    }

    /** Идёт ли этот игрок первым по победным очкам (по нему бьём охотнее). */
    private boolean isLeader(GameState state, int who) {
        int best = -1;
        int bestSeat = -1;
        for (PlayerState p : state.players) {
            int vp = kelium.engine.Scoring.scorePlayer(state, p.seat).getOrDefault("total", 0);
            if (vp > best) {
                best = vp;
                bestSeat = p.seat;
            }
        }
        return bestSeat == who;
    }

    /** Сколько СВОИХ войск уже стоит на этом гексе (кулак собирается тут). */
    private int friendsAt(GameState state, String hexId) {
        if (hexId == null) {
            return 0;
        }
        int n = 0;
        for (UnitToken u : state.player(seat).unitsOnField()) {
            if (hexId.equals(u.hexId) && u.type != UnitType.TOWER) {
                n++;
            }
        }
        return n;
    }

    /** С какого гекса идёт это перемещение (нужно, чтобы понять, сближаемся ли). */
    @SuppressWarnings("unchecked")
    private String fromHexOf(GameState state, Map<String, Object> pl) {
        if (!(pl.get("uid") instanceof Number n)) {
            return null;
        }
        for (UnitToken u : state.player(seat).unitsOnField()) {
            if (u.uid == n.intValue()) {
                return u.hexId;
            }
        }
        return null;
    }

    // ================= бой ==============================================
    private double scoreCombatSource(GameState state, Choice o) {
        if ("pass".equals(o.kind())) {
            return (wget("aggression") >= 1.0 || threatened(state)) ? 0.2 : 1.2;
        }
        // Бить можно только по СОСЕДЯМ источника: гекс без смежных целей —
        // гарантированно пустой бой, хуже паса.
        String src = (String) o.payload();
        double tv = adjacentTargetValue(state, src);
        if (tv <= 0) {
            return 0.05;
        }
        return (1.0 + tv) * (0.8 + 0.6 * wget("aggression"));
    }

    private double scoreCombatTarget(GameState state, Choice o) {
        String hx = (String) o.payload();
        double score = 0.0;
        for (PlayerState pl : state.players) {
            if (pl.seat == seat) {
                continue;
            }
            boolean leader = isLeader(state, pl.seat);
            for (Token t : allOnField(pl)) {
                if (!hx.equals(t.hexId())) {
                    continue;
                }
                score += 1.0;
                if (t instanceof BuildingToken b) {
                    // СНОС ЗДАНИЯ — ЭТО НЕ ТОЛЬКО ТРОФЕЙ, ЭТО УДАР ПО ЧУЖИМ ОЧКАМ
                    // (замечание дизайнера 12.08.2026). Здание даёт владельцу ПО
                    // (4 здания = 1 ПО) и работает на его экономику или армию:
                    // добытчик кормит келемием, станция запитывает, казарма и
                    // завод делают войска. Убрать здание — уменьшить ЕГО шансы,
                    // а не только пополнить свои трофеи.
                    score += switch (b.type) {
                        case COMMAND_CENTER -> 5.0;      // путь к военной победе
                        case MINER, POWER_PLANT -> 2.5;  // экономика противника
                        case BARRACKS, FACTORY, AIRBASE -> 2.0;   // его армия
                        default -> 1.0;
                    };
                    if (leader) {
                        score += 1.5;                    // по лидеру бьём охотнее
                    }
                }
            }
        }
        // Нейтральная постройка: снос даёт трофеи+контейнер и открывает гекс.
        if (state.field.get(hx).hasNeutral()) {
            score += 1.5;
        }
        return score + 1.0;
    }

    @SuppressWarnings("unchecked")
    private double scoreAttack(GameState state, Choice o) {
        if ("pass".equals(o.kind())) {
            return 0.3;
        }
        Map<String, Object> pl = (Map<String, Object>) o.payload();
        int ammo = pl.get("ammo") instanceof Number n ? n.intValue() : 1;
        return 2.0 + (2 - ammo);
    }

    // ================= прочие ===========================================
    @SuppressWarnings("unchecked")
    private double scoreModuleRed(GameState state, Choice o) {
        if ("pass".equals(o.kind())) {
            return 0.2;
        }
        PlayerState me = state.player(seat);
        Map<String, Object> pl = (Map<String, Object>) o.payload();
        UnitType ut = (UnitType) pl.get("unit");
        String mod = (String) pl.get("module");
        int cnt = 0;
        for (UnitToken u : me.unitsOnField()) {
            if (u.type == ut) {
                cnt++;
            }
        }
        double v = 1.0 + cnt;
        // Модуль с целью «здания-вышки» — самый ценный охват: здания у врага
        // есть всегда, а бить их умеет почти никто (война упирается в это).
        // Модуль С МЕШКА (R1-x/R2-x) не найти в старом хардкоде M1-M4 — бонус
        // немо для него не срабатывал НИ РАЗУ на актуальных данных партии
        // (designer поймал 16.08.2026). Смотрим сперва в реальный набор
        // жетона, легаси-хардкод — только запасной путь для старых M1-M4.
        boolean hitsBuildings;
        var tok = kelium.engine.ModuleSets.token(kelium.engine.ModuleSets.of(state), mod);
        if (tok != null) {
            hitsBuildings = tok.targets().contains("buildings_towers")
                || tok.gold().contains("buildings_towers");
        } else {
            kelium.core.Target[] pair = kelium.engine.Modules.RED_MODULES.get(mod);
            hitsBuildings = pair != null && (pair[0] == kelium.core.Target.BUILDINGS_TOWERS
                || pair[1] == kelium.core.Target.BUILDINGS_TOWERS);
        }
        if (hitsBuildings) {
            v += 2.5;
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    private double scoreModuleBlue(GameState state, Choice o) {
        if ("pass".equals(o.kind())) {
            return 0.2;
        }
        PlayerState me = state.player(seat);
        Map<String, Object> pl = (Map<String, Object>) o.payload();
        BuildingType bt = (BuildingType) pl.get("building");
        String mod = (String) pl.get("module");
        boolean built = false;
        for (BuildingToken b : me.buildingsOnField()) {
            if (b.type == bt) {
                built = true;
                break;
            }
        }
        if (!built) {
            return 0.5;
        }
        Map<String, Object> spec = kelium.engine.Modules.BLUE_MODULES.get(mod);
        int units = spec.get("units") instanceof Number n ? n.intValue() : 1;
        int ammo = spec.get("ammo") instanceof Number n ? n.intValue() : 1;
        double v = 2.0;
        // Юнитовые жетоны (C3/C4) — на ударные здания, когда армия нужна;
        // патронные (C1/C2) — на ЦУ (он всегда запитан и по умолчанию даёт БП).
        if (units >= 2 && (bt == BuildingType.FACTORY || bt == BuildingType.BARRACKS
                || bt == BuildingType.AIRBASE)) {
            v += 1.5 + 0.5 * wget("aggression");
        }
        // ЦУ ДЕЛАЕТ ВЫШКИ — они неподвижны. Замер 12.08.2026: треть синих
        // модулей боты кладут на ЦУ, то есть множитель сборки уходит на войска,
        // которые никуда не пойдут. Пока подвижных войск мало, множитель нужен
        // казарме, заводу и авиабазе.
        if (bt == BuildingType.COMMAND_CENTER && units >= 2) {
            int mobile = 0;
            for (UnitToken u : me.unitsOnField()) {
                if (u.type != UnitType.TOWER) {
                    mobile++;
                }
            }
            v -= mobile >= 4 ? 0.0 : 2.0;
        }
        if (ammo >= 2 && bt == BuildingType.COMMAND_CENTER) {
            v += 1.5;
        }
        return v;
    }

    /**
     * Цена выброса ОДНОГО кубика ресурса из хранилища, в очках-эквиваленте —
     * чем дешевле, тем охотнее выбрасываем именно этот тип первым (правило 4).
     * Читает ЖИВЫЕ курсы рулсета, а не константы: на варианте «келемий = 0 ПО»
     * (economy.kelium_per_vp = 0) келемий здесь стоит 0 и выбрасывается свободно,
     * хотя обычно он самый дорогой ресурс в игре.
     */
    private double storageDiscardCost(GameState state, kelium.core.Resource r) {
        var econ = Ctx.rules(state).economy();
        return switch (r) {
            case KELIUM -> {
                if (!econ.containsKey("kelium_per_vp")) {
                    yield ((Number) econ.getOrDefault("kelium_vp_each", 0)).doubleValue();
                }
                int per = ((Number) econ.get("kelium_per_vp")).intValue();
                yield per > 0 ? 1.0 / per : 0.0;
            }
            case DEBRIS -> {
                // ЛИБО-ЛИБО, КАК В Scoring.scorePlayer — не оба курса сразу.
                // Найдено и исправлено 18.08.2026 вместе с тем же дублированием
                // в самом подсчёте очков: бот складывал 1/trophy_per_vp И
                // debris_storage_vp_per_unit, оценивая обломок в 1.333 ПО вместо
                // одного курса (0.333 или 0.5, смотря какой ключ считается).
                if (econ.containsKey("debris_storage_vp_per_unit")) {
                    yield ((Number) econ.get("debris_storage_vp_per_unit")).doubleValue();
                }
                int per = ((Number) econ.getOrDefault("trophy_per_vp", 0)).intValue();
                yield per > 0 ? 1.0 / per : 0.0;
            }
            // Боеприпас не даёт очков напрямую, но нужен для боя/лишних ходов —
            // небольшая утилитарная цена вместо нуля, чтобы не выбрасывался
            // бездумно первым же при равенстве с обесцененным келемием/обломком.
            case AMMO -> 0.2 * wget("aggression");
            default -> 0.0;
        };
    }

    private double scoreMine(GameState state, Choice o) {
        if (o.payload() == null) {
            return 0.2;   // пропуск добытчика — почти всегда хуже добычи
        }
        return "kelium".equals(o.payload()) ? 3.0 : 1.0;
    }

    /**
     * Выбор ВАРИАНТА вскрываемого контейнера. Ценим по тому, что вариант даёт:
     * келемий дороже всего (он же победное очко), трофей — топливо треков,
     * мелочь (монеты/патроны) дёшево. Жетон модуля — крупная разовая прибавка,
     * позолота слабее. Мгновенные эффекты (бесплатное действие, лечение,
     * переброска, высадка, урон) ценятся тем выше, чем агрессивнее характер.
     */
    @SuppressWarnings("unchecked")
    private double scoreContainerVariant(GameState state, Choice o) {
        if ("pass".equals(o.kind())) {
            return 0.1;
        }
        Object[] pair = (Object[]) o.payload();
        Map<String, Object> variant = (Map<String, Object>) pair[1];
        Map<String, Object> params = variant.get("params") instanceof Map<?, ?> m
            ? (Map<String, Object>) m : Map.of();
        double val = 0.0;
        val += num(params, "kelium") * 3.0 + num(params, "trophy") * 1.0;
        val += num(params, "coin") * 0.2 + num(params, "ammo") * 0.3;
        val += num(params, "objective_cards") * 1.0 + num(params, "containers") * 1.5;
        if (params.containsKey("module") || params.containsKey("module_half")) {
            val += 4.0;
        }
        if (params.containsKey("gild_module")) {
            val += 3.0;
        }
        Object eff = variant.get("effect");
        if ("free_action".equals(eff) || "heal_one".equals(eff) || "move_unit".equals(eff)
                || "deploy_units".equals(eff) || "place_damage".equals(eff)) {
            val += 0.8 + 0.6 * wget("aggression");
        }
        return val;
    }

    /**
     * МАРКЕТ. Печатные обмены (1 келемий -> 3 монеты / 2 боеприпаса / 2 карты
     * задания / кубик навсегда в ячейку энергии) доступны сколько угодно раз за
     * действие; уникальное предложение карты — один раз.
     *
     * <p>Раньше печатный обмен оценивался константой 0.6, а «не торговать» — 1.0:
     * бот попросту НЕ ПРОДАВАЛ келемий и всю партию сидел без денег. Теперь
     * каждый обмен оценивается по НУЖДЕ: пустой кошелёк, пустой патронташ, забитый
     * склад и незапитанное здание — всё это причины продать.
     */
    @SuppressWarnings("unchecked")
    private double scoreMarket(GameState state, Choice o) {
        PlayerState me = state.player(seat);
        int kel = me.resources.kelium();
        if ("pass".equals(o.kind())) {
            // Келемий сам по себе очковый, поэтому «не торговать» — достойный
            // вариант. Но при пустом кошельке он хуже любой сделки.
            return me.resources.coin() <= 1 ? 0.4 : 2.0;
        }
        if ("market_rate".equals(o.kind())) {
            Map<String, Object> pl = (Map<String, Object>) o.payload();
            String what = String.valueOf(pl.get("what"));
            int amount = pl.get("amount") instanceof Number n ? n.intValue() : 1;
            int coins = me.resources.coin();
            int ammo = me.resources.ammo();
            // склад забит — келемий всё равно некуда девать, продаём охотнее
            double full = kel >= kelium.engine.Storage.keliumMax(me) ? 1.5 : 0.0;
            return switch (what) {
                // монета — валюта стройки: при нуле в кошельке это лучший ход
                case "coin" -> (coins <= 1 ? 6.0 : coins <= 3 ? 4.0 : coins <= 6 ? 2.0 : 0.8)
                    + 0.2 * amount + full;
                // боеприпас — топливо войны, нужен когда есть кем воевать
                case "ammo" -> (me.unitsOnField().size() >= 2 && ammo <= 2 ? 4.5 : 1.0) + full;
                // карты заданий — источник очков и монет, но рука ограничена
                case "objective_cards" -> (me.objectiveHand.size() <= 1 ? 3.5 : 1.0) + full;
                // кубик навсегда: дорого (келемий = очки), но чинит энергию насовсем
                default -> (spareEnergy(state, me) < 0 ? 3.0 : 0.6) + full;
            };
        }
        // ---- уникальное предложение карты маркета ----
        Map<String, Object> pl = (Map<String, Object>) o.payload();
        Map<String, Object> offer = (Map<String, Object>) pl.get("offer");
        Map<String, Object> params = offer.get("params") instanceof Map<?, ?> m
            ? (Map<String, Object>) m : Map.of();
        int pool = trophyPool(me);
        double trophyW = hasAffordableVpTechStep(state, me, pool + 4) ? 1.2 : 0.1;
        int nUnits = me.unitsOnField().size();
        int ammoTarget = 4 + (int) (2 * wget("aggression")) + nUnits / 4;
        double ammoW = nUnits >= 2 && me.resources.ammo() < ammoTarget ? 0.9 : 0.3;
        int coins = me.resources.coin();
        double coinW = coins <= 1 ? 2.0 : coins <= 3 ? 1.3 : coins <= 6 ? 0.7 : 0.25;
        double contW = me.containers >= 3 ? 0.4 : 0.8;
        double val = 0.5;
        val += num(params, "trophy") * trophyW + num(params, "coin") * coinW;
        val += num(params, "ammo") * ammoW + num(params, "containers") * contW;
        val += num(params, "objective_cards") * 1.0;
        // "module" (ЦЕЛЫЙ жетон из мешка) — формат с 13.08.2026, "module_half"
        // остался только в старых записях контейнеров. Раньше здесь проверялся
        // ТОЛЬКО module_half/gild_module — «Оружейная ярмарка» рынка
        // (params: {module: choice}) получала 0 дополнительной ценности, и бот
        // в упор не видел единственный ДЕШЁВЫЙ путь к модулю (1 келемий, без
        // предварительной войны) — designer поймал 16.08.2026 при разборе,
        // почему модуль добывается так поздно.
        if (params.containsKey("module") || params.containsKey("module_half")
                || params.containsKey("gild_module")) {
            val += 3.0;
        }
        Object eff = offer.get("effect");
        if ("free_action".equals(eff) || "heal_all_own".equals(eff)) {
            val += 1.5;
        }
        return val;
    }

    /**
     * ВЕЧНЫЕ ОБМЕНЫ НАУКИ. Раньше здесь стояли константы «пас 5.0, позолота 2.0,
     * всё прочее 0.1», и бот не брал обмены НИ РАЗУ за 500 партий (замер
     * {@code kelium.BoardsProbe}). Отчасти это верно — трофей на треке стоит
     * дороже, чем монета, — но как ПРАВИЛО это ошибка, потому что забывает главное:
     *
     * <p><b>несданные трофейные жетоны В ВОЗВРАТ ВОЗВРАЩАЮТСЯ ВЛАДЕЛЬЦАМ.</b>
     * То есть «копить на трек» иногда означает «потерять». Если пул трофеев уже
     * больше, чем нужно на очередной шаг, излишек надо тратить — он всё равно
     * пропадёт, и монета за него это чистая прибыль.
     *
     * <p>Логика теперь такая: обмен ценится ровно настолько, насколько он НЕ мешает
     * шагу по треку. Съел трофеи, из-за которых шаг стал недоступен — плохо;
     * потратил излишек, который иначе сгорит, — хорошо.
     */
    private double scoreSciExchange(GameState state, Choice o) {
        PlayerState me = state.player(seat);
        int pool = me.trophySpacePoints() + me.resources.debris();
        int cheapest = cheapestNextStepCost(state, me);
        boolean stepAffordable = cheapest > 0 && pool >= cheapest;
        // ТРОФЕИ, КОТОРЫЕ ТОЧНО ПРОПАДУТ: только ЖЕТОНЫ на трофейном поле
        // возвращаются владельцам в Возврат. Чёрные кубы (resources.trophy)
        // остаются у игрока и ждут следующего раунда, их «спасать» не надо.
        int doomed = me.trophySpacePoints();
        // Трофеи ЗАСТРЯЛИ, если на шаг по треку их не хватает даже все вместе.
        boolean stuck = cheapest > 0 && pool < cheapest;
        // СКОЛЬКО АРСЕНАЛА УЖЕ ЕСТЬ (рука + установленные, слотов установки 3).
        // Замер 14.08.2026 (kelium.ExchangeProbe): обмен «2 трофея -> арсенал»
        // взят 24 раза за 300 партий (0.08 за партию) — практически никогда,
        // хотя доступен куда чаще, чем сборка карты арсенала. Причина была ниже:
        // пас стоял константой 8.0, а обмен при непустом треке падал до 0.2 —
        // арсенал не мог перевесить пас, сколько бы карт ни было пусто.
        int arsenalHave = me.arsenalHand.size() + me.arsenalInstalled.size();
        boolean wantArsenal = arsenalHave <= 2;
        if ("pass".equals(o.kind())) {
            // Трофей на треке обычно стоит дороже монеты или карты — пас почти
            // всегда верный ответ. Первая версия правки была слишком щедрой, и
            // мирные линии просели с 16.4 до 12.6 ПО за партию (замер лигой) —
            // обмены съедали трофеи, которые нужны были трекам В СЛЕДУЮЩЕЙ Науке
            // этого же раунда. Поэтому вне тупика пас остаётся сильным по
            // умолчанию — НО не настолько, чтобы арсенал с пустой рукой не мог
            // его перевесить: gate ниже (stepStillOk) и так не даёт обмену
            // испортить шаг по треку, второй барьер здесь был лишним.
            return stuck && doomed > 0 ? 1.0 : (wantArsenal ? 5.0 : 8.0);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> ex = o.payload() instanceof Map<?, ?> m
            ? (Map<String, Object>) m : Map.of();
        String id = String.valueOf(ex.get("id"));
        int give = ex.get("give") instanceof Number n ? n.intValue() : 1;
        // Останется ли после обмена на шаг по треку.
        boolean stepStillOk = cheapest <= 0 || pool - give >= cheapest;
        if (stepAffordable && !stepStillOk) {
            return 0.1;   // обмен съедает шаг — почти всегда хуже
        }
        double base = switch (id) {
            // Позолота даёт победное очко напрямую — единственный курс, который
            // не уступает треку, и его стоит брать даже не в тупике.
            case "gild" -> 6.0;
            // Арсенал ценен САМ ПО СЕБЕ, не только как спасение обречённых
            // трофеев: это карты, доступные независимо от Сборки. Вес убывает,
            // когда рука+установленные уже полны (слотов установки 3) — незачем
            // копить сверх того, что реально сыграть.
            case "draw_arsenal" -> wantArsenal ? 7.0 : 2.0;
            case "move_module" -> 2.0;
            default -> 1.5;   // трофей в монету — самый слабый курс
        };
        if (!stuck) {
            // Трек ещё доступен — раньше здесь всё, кроме позолоты, падало до
            // 0.2: арсенал не мог конкурировать с пасом ни при каких условиях.
            // Теперь конкурирует, если карт мало.
            return switch (id) {
                case "gild", "draw_arsenal" -> base;
                default -> 0.2;
            };
        }
        // Трофеи застряли: обмен спасает то, что иначе вернётся владельцам.
        // Тем ценнее, чем больше жетонов под угрозой.
        return base + Math.min(3.0, doomed * 0.8) + (give <= doomed ? 1.0 : 0.0);
    }

    /**
     * Стоимость СЛЕДУЮЩЕГО шага самого дешёвого доступного трека (или −1, если
     * шагать некуда). Нужно, чтобы решать, мешает обмен треку или нет.
     */
    private int cheapestNextStepCost(GameState state, PlayerState me) {
        List<Integer> costs = Ctx.rules(state).getIntList("tech.step_cost_trophy");
        int best = -1;
        for (String track : state.tech.tracks) {
            int step = me.techSteps.getOrDefault(track, 0);
            if (step >= state.tech.steps) {
                continue;   // трек пройден
            }
            int cost = costs.get(Math.min(step, costs.size() - 1));
            if (best < 0 || cost < best) {
                best = cost;
            }
        }
        return best;
    }

    private double scoreSpec(GameState state, Choice o) {
        return switch (o.kind()) {
            case "pass" -> 0.2;
            case "spec_super_deploy" -> 100.0;
            case "spec_objective" -> scoreObjectiveComplete(state, (String) o.payload());
            case "spec_objective_burn" -> scoreObjectiveBurn(state, (String) o.payload());
            case "spec_super" -> 3.0;
            case "spec_arsenal_install" -> scoreArsenalInstall(state, (String) o.payload());
            case "spec_arsenal_burn" -> scoreArsenalBurn(state, (String) o.payload());
            // Массовое вскрытие (СПЕЦ): ценнее, когда копятся контейнеры и
            // закрытые карты — места под планшетом ограничены.
            case "spec_container" -> 1.8 + 0.6 * state.player(seat).containers
                + 0.3 * state.player(seat).arsenalHand.size();
            case "mass_container" -> 2.2;
            // СИМВОЛЫ СУПЕР ЗАДАНИЯ (модуль включён в правилах 1.7.0). Вскрытие
            // подложенной карты стоит СПЕЦ, поэтому его ценность равна тому,
            // приближает ли символ требование карты. Без этой оценки механика
            // мертва: боты не вскрывали НИ РАЗУ, и супер задания перестали
            // закрываться вовсе (замер 13.08.2026).
            case "spec_symbol_reveal" -> scoreSymbolReveal(state, (String) o.payload());
            case "spec_super_check" -> 8.0;
            // ВАРИАНТ ОТ КАРТЫ АРСЕНАЛА. Бот не знает, что это за карта, и знать не
            // должен: он спрашивает у самой способности, какое узкое место она
            // расшивает и насколько сильно (Hint), и сверяет с тем, что жмёт у него
            // сейчас. Так новая карта становится видимой боту без правки оценщика —
            // до этого новое спец-действие висело в меню и не выбиралось НИ РАЗУ.
            default -> kelium.engine.ability.Abilities.isAbilityChoice(o)
                ? scoreAbilityOption(state, o) : 1.0;
        };
    }

    /**
     * ВЫПОЛНИТЬ ЗАДАНИЕ (СПЕЦ уже готов — движок не предложил бы {@code
     * spec_objective}, не будь оно {@link kelium.engine.ObjectiveHints.Hint#ready}).
     * Раньше все ГОТОВЫЕ карты получали одну и ту же оценку 5.0, и если готово
     * сразу несколько — какую сыграть решал ГСЧ, а не реальная цена награды.
     */
    private double scoreObjectiveComplete(GameState state, String cid) {
        if (cid == null || state.journal == null) {
            return 5.0;
        }
        kelium.engine.ObjectiveHints.Hint h =
            kelium.engine.ObjectiveHints.forCard(state, seat, state.journal, cid, List.of(), 0);
        return h == null ? 5.0 : 4.0 + h.maxValue() * 0.6;
    }

    /**
     * СЖЕЧЬ ВЕРХ КАРТЫ ЗАДАНИЯ вместо выполнения низа. Заказ дизайнера
     * 17.08.2026 (пункт 3 плана): движок теперь знает, ГОТОВА ли эта же карта
     * ПРЯМО СЕЙЧАС ({@link kelium.engine.ObjectiveHints}) — и жечь готовую
     * карту почти всегда расточительно: то же самое СПЕЦ-действие вместо утиля
     * верха отдало бы полную награду низа. Замер каталога 1.6.0 без этой
     * проверки: 8.25 сожжённых карт на 1.18 выполненных из 6.5 полученных —
     * ровно симптом отсутствия этого сравнения.
     */
    private double scoreObjectiveBurn(GameState state, String cid) {
        double base = 1.5;   // утиль верха — печатное значение не переоценивается заново
        if (cid != null && state.journal != null) {
            kelium.engine.ObjectiveHints.Hint h =
                kelium.engine.ObjectiveHints.forCard(state, seat, state.journal, cid, List.of(), 0);
            if (h != null && h.ready()) {
                base -= h.maxValue() * 0.5;
            }
        }
        return base;
    }

    /** Ценность варианта, пришедшего от способности, по её собственной подсказке. */
    private double scoreAbilityOption(GameState state, Choice o) {
        String id = String.valueOf(o.payload());
        var ability = kelium.engine.ability.Abilities.byId(id);
        if (ability == null) {
            return 1.0;
        }
        return Bottlenecks.value(state, seat, ability.hint(),
            wget("aggression"), wget("economy"));
    }

    /**
     * Установить карту арсенала = получить ПОСТОЯННУЮ способность (нижняя часть).
     * Ценность зависит от типа пассивки и текущей стратегии бота. Слотов всего 3,
     * поэтому за них есть конкуренция со сжиганием.
     */
    @SuppressWarnings("unchecked")
    /**
     * СТОИТ ЛИ ПОДСУНУТЬ карту под планшет. Смотрим на требование своего супер
     * задания: нужен ли ещё хоть один символ. Символ карты арсенала известен, а
     * контейнер подсовывается «не глядя» — там ставка на удачу, и цена ниже.
     */
    private double scoreTuck(GameState s, Choice o) {
        if (o.payload() == null) {
            return 0.5;                      // «ничего не подсовывать»
        }
        var p = s.player(seat);
        if (p.superObjective == null) {
            return 0.1;
        }
        java.util.List<String> need = kelium.engine.Symbols.required(s, p.superObjective);
        if (need.isEmpty()) {
            return 0.1;
        }
        java.util.List<String> open = kelium.engine.Symbols.revealed(s, p);
        java.util.List<String> hidden = kelium.engine.Symbols.hiddenForms(s, p);
        // Чего ещё не хватает с учётом уже подложенного (открытого и закрытого).
        java.util.Map<String, Integer> have = new java.util.HashMap<>();
        for (String f : open) {
            have.merge(f, 1, Integer::sum);
        }
        for (String f : hidden) {
            have.merge(f, 1, Integer::sum);
        }
        java.util.Map<String, Integer> want = new java.util.HashMap<>();
        for (String f : need) {
            want.merge(f, 1, Integer::sum);
        }
        int missing = 0;
        for (var e : want.entrySet()) {
            missing += Math.max(0, e.getValue() - have.getOrDefault(e.getKey(), 0));
        }
        if (missing == 0) {
            return 0.2;                      // набор уже собран, беречь карты
        }
        if ("tuck_container".equals(o.kind())) {
            return 1.6;                      // ставка не глядя: символ может не подойти
        }
        var marking = kelium.engine.Symbols.of(s);
        String form = marking.ofArsenal(String.valueOf(o.payload()));
        if (form == null) {
            return 0.2;
        }
        int stillNeed = want.getOrDefault(form, 0) - have.getOrDefault(form, 0);
        return stillNeed > 0 ? 4.0 : 0.3;
    }

    /**
     * НАСКОЛЬКО ПОЛЕЗНО ВСКРЫТЬ подложенную карту: если её символ нужен супер
     * заданию и такого символа ещё не хватает — очень полезно, иначе почти нет.
     *
     * <p>Символы — сет-коллекшн: вскрывать без надобности значит потратить СПЕЦ
     * и ничего не получить, поэтому оценка смотрит именно на НЕДОСТАЮЩЕЕ.
     */
    private double scoreSymbolReveal(GameState s, String cardId) {
        var p = s.player(seat);
        if (p.superObjective == null) {
            return 0.3;
        }
        java.util.List<String> need = kelium.engine.Symbols.required(s, p.superObjective);
        if (need.isEmpty()) {
            return 0.3;
        }
        var marking = kelium.engine.Symbols.of(s);
        String form = null;
        for (var t : p.tucked) {
            if (t.cardId.equals(cardId) && !t.revealed) {
                form = "container".equals(t.kind) ? marking.ofContainer(t.cardId)
                    : marking.ofArsenal(t.cardId);
            }
        }
        if (form == null) {
            return 0.3;
        }
        java.util.List<String> open = kelium.engine.Symbols.revealed(s, p);
        long have = open.stream().filter(form::equals).count();
        long want = need.stream().filter(form::equals).count();
        return want > have ? 6.0 : 0.4;
    }

    private double scoreArsenalInstall(GameState state, String cid) {
        Map<String, Object> card = Ctx.cards(state, "arsenal").find(cid);
        if (card == null) {
            return 2.0;
        }
        Map<String, Object> bottom = card.get("bottom") instanceof Map<?, ?> m
            ? (Map<String, Object>) m : Map.of();
        String passive = String.valueOf(bottom.get("passive"));
        double base = 2.0;
        // Боевые/экономические постоянки ценнее для соответствующих характеров.
        switch (passive) {
            case "buildings_plus1_hp" -> base = 2.2 + 1.0 * wget("defense");
            case "bonus_trophy_on_kill", "ammo_on_kill" -> base = 2.2 + 1.2 * wget("aggression");
            case "anti_armor_minus1_ammo", "first_attack_minus1_ammo",
                 "no_second_battle_surcharge" -> base = 2.0 + 1.0 * wget("aggression");
            case "plus1_storage_cell" -> base = 2.4 + 1.0 * wget("economy");
            case "extraction_flip_bonus_trophy" -> base = 2.0 + 0.8 * wget("economy");
            default -> base = 2.0;
        }
        // Если все 3 слота заняты — установка вытеснит старую карту, чуть дешевле.
        for (PlayerState p : state.players) {
            if (p.seat == seat && p.arsenalInstalled.size() >= 3) {
                base -= 0.6;
            }
        }
        return base;
    }

    /**
     * Сжечь карту арсенала = МГНОВЕННЫЙ эффект (верхняя часть), карта уходит в
     * сброс. Выгодно, когда верх даёт ресурсы/бесплатное действие прямо сейчас,
     * особенно если ресурс дефицитен (боеприпасы для войны, монеты, трофеи).
     */
    @SuppressWarnings("unchecked")
    private double scoreArsenalBurn(GameState state, String cid) {
        Map<String, Object> card = Ctx.cards(state, "arsenal").find(cid);
        if (card == null) {
            return 2.0;
        }
        Map<String, Object> top = card.get("top") instanceof Map<?, ?> m
            ? (Map<String, Object>) m : Map.of();
        String effect = String.valueOf(top.get("effect"));
        Map<String, Object> params = top.get("params") instanceof Map<?, ?> m
            ? (Map<String, Object>) m : Map.of();
        double val = 1.0;
        if ("gain".equals(effect)) {
            val += num(params, "trophy") * 1.3 + num(params, "ammo") * 0.6
                 + num(params, "coin") * 0.25 + num(params, "objective_cards") * 0.8;
        } else if ("free_action".equals(effect)) {
            String act = String.valueOf(params.get("action"));
            val += switch (act) {
                case "combat" -> 1.5 + 1.2 * wget("aggression");
                case "mining" -> 1.4 + 1.0 * wget("economy");
                case "science" -> 1.6;
                case "build" -> 1.2;
                default -> 1.0;
            };
        } else if ("place_damage".equals(effect)) {
            val += 1.5 + 1.2 * wget("aggression");
        } else if ("move_unit".equals(effect) || "heal_hex".equals(effect)) {
            val += 1.0;
        }
        return val;
    }

    private static double num(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static List<Token> allOnField(PlayerState pl) {
        List<Token> out = new ArrayList<>();
        out.addAll(pl.unitsOnField());
        out.addAll(pl.buildingsOnField());
        return out;
    }
}
