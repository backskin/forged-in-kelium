package kelium.agents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.Ctx;
import kelium.engine.Order;
import kelium.engine.cards.CardRegistry;
import kelium.engine.cards.EngineCardContext;

/**
 * ПЛАНИРОВЩИК — бот, который думает ХОДОМ, а не решением.
 *
 * <p>Прежние боты отвечали на каждый вопрос движка отдельно: «какое действие?»,
 * «куда идти?», «чем бить?» — и ни одно решение не знало о следующем. Отсюда
 * «рандомный порядок действий» и боеприпасы без войск: Сборка выбирала выход,
 * не зная, будет ли Бой; Бой брался, не зная, что Движение ещё не сыграно.
 *
 * <p>Здесь ход планируется ЦЕЛИКОМ и ЗАРАНЕЕ. В начале своего хода бот берёт
 * копию стола и проигрывает на ней ход несколькими способами — в разном порядке
 * действий, с разными СПЕЦ-действиями, с разбросом в мелких решениях. Каждый
 * прогон даёт СЦЕНАРИЙ (все выборы по порядку) и ПОЗИЦИЮ после хода; позиция
 * оценивается ({@link PositionValue}), лучший сценарий повторяется в живой
 * партии решение за решением. Так «порядок решает» становится не лозунгом, а
 * измеренной разницей между сценариями.
 *
 * <p>Сверх хода у бота есть НАМЕРЕНИЯ ({@link Intents}): противник, за которым
 * он ходит, задание, которое доводит, и обиды на тех, кто его бил. Они меняют
 * оценку позиции, поэтому все сценарии считаются под один и тот же замысел, и
 * замысел не меняется от хода к ходу без причины.
 *
 * <p>Приказ на круг тоже выбирается симуляцией: каждая карта руки проигрывается
 * как ход, к цене прибавляется риск совпадения и шанс открыть нижний приказ —
 * по тому, какие приказы соперники вскрывали раньше (открытая информация).
 *
 * <p>Бот читает только открытую информацию и свою руку.
 */
public class PlannerAgent extends Agent {

    /** Веса оценки и характера. */
    public final Genome genome;
    public final String character;
    private final Random rng;
    /** Сколько прогонов на каждый порядок действий (первый — без шума). */
    private final int samples;
    /** Выбирать приказ симуляцией хода (дорого), иначе — формулой. */
    private final boolean revealBySim;
    /** Исполнитель решений, которых нет в сценарии. */
    private final StrategicAgent fallback;
    private final Genome others;
    private Intents intents;

    // ---- память об открытой информации ----
    private final Map<Integer, Map<String, Integer>> topSeen = new HashMap<>();
    private GameState lastState;
    private int lastRound = -1;

    // ---- текущий сценарий ----
    private List<TurnSim.Step> script;
    private int cursor;
    private long turnKey = -1;
    private List<String> plannedActions = List.of();
    private int plannedIdx;
    private String lastRevealCard;
    private double lastPlanValue;
    private String lastPlanText = "";

    // ---- телеметрия ----
    public int plansMade;
    public int simulations;
    public int scriptMisses;
    /** Промахи сценария по видам решений — чтобы видеть, где план расходится с игрой. */
    public final Map<String, Integer> missByKind = new java.util.TreeMap<>();

    public PlannerAgent(int seat, Random rng, Genome genome, String character,
                        int samples, boolean revealBySim, int players) {
        super(seat, character + ":план");
        this.rng = rng == null ? new Random(seat) : rng;
        this.genome = genome;
        this.character = character;
        this.samples = Math.max(1, samples);
        this.revealBySim = revealBySim;
        this.fallback = new StrategicAgent(seat, new Random(this.rng.nextLong()), genome, character);
        this.others = Bots.genome("balanced", players);
        this.intents = new Intents(players, genome.get("pl.commitment", 1.0));
    }

    /** Ключи весов планировщика — их и настраивает обучение. */
    public static final List<String> PL_KEYS = List.of(
        "pl.vp", "pl.margin", "pl.objective", "pl.economy", "pl.arsenal", "pl.army",
        "pl.war", "pl.ammo", "pl.trophy", "pl.tech", "pl.caution", "pl.target_bias",
        "pl.leader_bias", "pl.commitment");

    private static final Map<String, Genome> SAVED = new java.util.concurrent.ConcurrentHashMap<>();

    /** Файл обученных весов планировщика для характера и состава. */
    public static java.nio.file.Path savedPath(String character, int players) {
        return kelium.dataio.Locations.botMemory()
            .resolve("planner_" + players + "p_" + character + ".json");
    }

    /** Забыть прочитанные с диска веса (обучение перезаписывает файлы на ходу). */
    public static void forgetSaved() {
        SAVED.clear();
    }

    /**
     * Геном характера с весами планировщика поверх обученной линии. Если на
     * диске лежит обученный файл {@code planner_<N>p_<характер>.json}, его веса
     * {@code pl.*} берутся оттуда.
     */
    public static Genome plannerGenome(String character, int players) {
        Genome g = plannerDefaults(character, players);
        Genome saved = SAVED.computeIfAbsent(players + "/" + character, k -> {
            try {
                return Genome.loadJson(savedPath(character, players));
            } catch (Exception none) {
                return Genome.defaults().with("pl.__missing", 1.0);
            }
        });
        if (saved.get("pl.__missing", 0.0) > 0) {
            return g;
        }
        for (String key : PL_KEYS) {
            double v = saved.get(key, Double.NaN);
            if (!Double.isNaN(v)) {
                g = g.with(key, v);
            }
        }
        return g;
    }

    /** Веса характера, заданные вручную (отправная точка обучения). */
    public static Genome plannerDefaults(String character, int players) {
        Genome g = Bots.genome(character, players);
        Map<String, Double> w = new java.util.LinkedHashMap<>();
        w.put("pl.vp", 1.0);
        w.put("pl.margin", 0.35);
        w.put("pl.objective", 1.15);
        w.put("pl.economy", 1.0);
        w.put("pl.arsenal", 1.0);
        w.put("pl.army", 1.05);
        w.put("pl.war", 1.1);
        w.put("pl.ammo", 1.0);
        w.put("pl.trophy", 1.0);
        w.put("pl.tech", 1.0);
        w.put("pl.caution", 1.0);
        w.put("pl.target_bias", 0.5);
        w.put("pl.leader_bias", 0.8);
        w.put("pl.commitment", 1.0);
        switch (character) {
            case "builder" -> {
                w.put("pl.objective", 1.55);
                w.put("pl.economy", 1.2);
                w.put("pl.tech", 1.3);
                w.put("pl.army", 0.85);
                w.put("pl.war", 0.75);
                w.put("pl.caution", 1.15);
                w.put("pl.target_bias", 0.3);
                w.put("pl.leader_bias", 0.5);
            }
            case "supplier" -> {
                w.put("pl.arsenal", 1.65);
                w.put("pl.economy", 1.3);
                w.put("pl.objective", 1.1);
                w.put("pl.army", 0.9);
                w.put("pl.war", 0.85);
                w.put("pl.target_bias", 0.4);
            }
            case "stalker" -> {
                w.put("pl.war", 1.3);
                w.put("pl.army", 1.1);
                w.put("pl.caution", 0.9);
                w.put("pl.tech", 0.9);
                w.put("pl.target_bias", 1.0);
                w.put("pl.leader_bias", 1.6);
                w.put("pl.commitment", 1.3);
            }
            case "punisher" -> {
                w.put("pl.war", 1.75);
                w.put("pl.army", 1.45);
                w.put("pl.ammo", 1.15);
                w.put("pl.objective", 0.9);
                w.put("pl.economy", 0.9);
                w.put("pl.arsenal", 0.9);
                w.put("pl.tech", 0.8);
                w.put("pl.caution", 0.6);
                w.put("pl.target_bias", 0.8);
                w.put("pl.commitment", 1.2);
            }
            default -> { }
        }
        for (var e : w.entrySet()) {
            if (g.get(e.getKey(), Double.NaN) != g.get(e.getKey(), Double.NaN) ) {
                // ключа нет в геноме (NaN != NaN) — берём характерный вес
                g = g.with(e.getKey(), e.getValue());
            }
        }
        return g;
    }

    /** Уровни умения: 2 — без симуляции приказа, 3 и 4 — глубже и шире. */
    public static PlannerAgent ofLevel(int level, String character, int seat, Random rng,
                                       int players) {
        Genome g = plannerGenome(character, players);
        return switch (Math.max(2, Math.min(4, level))) {
            case 2 -> new PlannerAgent(seat, rng, g, character, 5, false, players);
            case 3 -> new PlannerAgent(seat, rng, g, character, 7, true, players);
            default -> new PlannerAgent(seat, rng, g, character, 10, true, players);
        };
    }

    public Intents intents() {
        return intents;
    }

    public String lastPlan() {
        return lastPlanText;
    }

    // ======================================================================
    //  ТОЧКИ РЕШЕНИЯ
    // ======================================================================

    @Override
    public Choice choose(GameState state, List<Choice> options, Map<String, Object> ctx) {
        lastState = state;
        String kind = ctx == null ? "" : String.valueOf(ctx.getOrDefault("kind", ""));
        if (state.round != lastRound) {
            lastRound = state.round;
            intents.newRound(state, seat, genome.get("pl.leader_bias", 0.8));
            refocus(state);
        }
        if (options.size() == 1) {
            return options.get(0);
        }
        switch (kind) {
            case "reveal_order":
                return chooseReveal(state, options);
            case "blind_discard":
                return chooseDiscard(state, options);
            case "action": {
                long key = state.round * 100L + state.circle;
                if (key != turnKey) {
                    turnKey = key;
                    plan(state, options, ctx);
                }
                return follow(state, options, ctx, kind);
            }
            default:
                return follow(state, options, ctx, kind);
        }
    }

    @Override
    public void observePublicEvent(Map<String, Object> event) {
        Object type = event.get("type");
        if ("combat_hit".equals(type)) {
            if (Boolean.TRUE.equals(event.get("destroyed"))
                    && event.get("victim_owner") instanceof Integer vo && vo == seat
                    && event.get("seat") instanceof Integer by && by != seat) {
                intents.hurtBy(by, false);
            }
        } else if ("cu_destroyed".equals(type)) {
            if (event.get("seat") instanceof Integer owner && owner == seat
                    && event.get("by") instanceof Integer by) {
                intents.hurtBy(by, true);
                // Меня только что лишили ЦУ — цель пересматривается сразу.
                if (lastState != null) {
                    intents.retarget(lastState, seat, genome.get("pl.leader_bias", 0.8), true);
                }
            }
        } else if ("reveal".equals(type) && event.get("revealed") instanceof Map<?, ?> rev) {
            noteReveals(rev);
        } else if ("turn_end".equals(type) && event.get("seat") instanceof Integer st && st == seat) {
            // Ход кончился — сценарий отработал. Решения между ходами (Возврат,
            // раскладка модулей за наградой соседа) к нему не относятся.
            script = null;
            plannedActions = List.of();
        }
    }

    // ======================================================================
    //  ПЛАН ХОДА
    // ======================================================================

    @SuppressWarnings("unchecked")
    private void plan(GameState s, List<Choice> options, Map<String, Object> ctx) {
        plansMade++;
        String cardId = lastRevealCard != null && s.player(seat).orderHand
            .stream().noneMatch(c -> c.equals(lastRevealCard))
            ? lastRevealCard : guessCard(s, options);
        boolean coincided = Boolean.TRUE.equals(ctx.get("coincided"));
        boolean bottomOpen = Boolean.TRUE.equals(ctx.get("bottom_open"));
        int remaining = ctx.get("remaining") instanceof Number n ? n.intValue() : 2;
        List<String> topNames = new ArrayList<>();
        for (Choice o : options) {
            if ("action".equals(o.kind()) && o.payload() != null) {
                topNames.add(String.valueOf(o.payload()));
            }
        }
        boolean joker = topNames.size() > 4;
        List<String> bottomNames = bottomOpen ? bottomActions(s, cardId) : List.of();

        List<List<String>> sequences = sequences(s, topNames, remaining, bottomNames, joker,
            cardId, coincided, bottomOpen);
        double best = Double.NEGATIVE_INFINITY;
        TurnSim.Result bestRes = null;
        List<String> bestSeq = null;
        for (List<String> seq : sequences) {
            for (int i = 0; i < samples; i++) {
                long seed = rng.nextLong();
                TurnSim.Result r = TurnSim.run(s, seat, cardId, coincided, bottomOpen, seq,
                    policy(i, seed), others, seed);
                simulations++;
                if (r == null) {
                    continue;
                }
                double v = PositionValue.value(r.after(), seat, genome, intents)
                    + rng.nextDouble() * 0.01;
                if (v > best) {
                    best = v;
                    bestRes = r;
                    bestSeq = seq;
                }
            }
        }
        if (bestRes == null) {
            script = null;
            plannedActions = List.of();
            return;
        }
        script = bestRes.script();
        cursor = 0;
        plannedActions = bestRes.actionsPlayed();
        plannedIdx = 0;
        lastPlanValue = best;
        lastPlanText = String.join(" → ", plannedActions) + (bestSeq == null ? "" : "")
            + String.format(java.util.Locale.ROOT, " (%.2f)", best);
    }

    /** Все осмысленные порядки основных действий на этот ход. */
    private List<List<String>> sequences(GameState s, List<String> topNames, int remaining,
                                         List<String> bottomNames, boolean joker,
                                         String cardId, boolean coincided, boolean bottomOpen) {
        List<String> top = topNames;
        if (joker) {
            // БЕЗОПАСНОСТЬ: восемь действий — берём четыре лучших по одиночной
            // симуляции, из них строим пары.
            Map<String, Double> single = new HashMap<>();
            for (String a : topNames) {
                long seed = rng.nextLong();
                TurnSim.Result r = TurnSim.run(s, seat, cardId, coincided, bottomOpen,
                    List.of(a), policy(0, seed), others, seed);
                simulations++;
                single.put(a, r == null ? Double.NEGATIVE_INFINITY
                    : PositionValue.value(r.after(), seat, genome, intents));
            }
            top = new ArrayList<>(topNames);
            top.sort((a, b) -> Double.compare(single.get(b), single.get(a)));
            top = top.subList(0, Math.min(4, top.size()));
        }
        List<List<String>> tops = new ArrayList<>();
        int limit = Math.max(1, Math.min(remaining, top.size()));
        for (String a : top) {
            tops.add(List.of(a));
            if (limit >= 2) {
                for (String b : top) {
                    if (!b.equals(a)) {
                        tops.add(List.of(a, b));
                    }
                }
            }
        }
        if (!joker) {
            tops.add(List.of());
        }
        List<List<String>> out = new ArrayList<>();
        for (List<String> t : tops) {
            out.add(t);
            for (String bn : bottomNames) {
                List<String> seq = new ArrayList<>(t);
                seq.add(bn);
                out.add(seq);
            }
        }
        // ЧЕГО ПРОСИТ ЗАДАНИЕ — ПЕРВЫМ ДЕЙСТВИЕМ.
        //
        // Каждая карта задания умеет сказать, каким действием к ней приближаться
        // ({@code ObjectiveCard.suggestedAction}). Планировщик перебирал порядки
        // действий, ни разу этого не спросив: он находил ход, который хорош
        // «вообще», и только случайно — ход, который закрывает карту. Отсюда и
        // жалоба, что бот не понимает, зачем ему задание.
        //
        // Теперь названное картой действие ставится ПЕРВЫМ, а вторым идёт всё
        // остальное доступное: закрывать условие надо до того, как ход
        // израсходован. Судит по-прежнему оценка позиции — если это плохой ход,
        // сценарий просто не выиграет.
        List<String> просят = действияЗаданий(s, top);
        for (String a : просят) {
            out.add(List.of(a));
            if (limit >= 2) {
                for (String b : top) {
                    if (!b.equals(a)) {
                        out.add(List.of(a, b));
                    }
                }
            }
            for (String bn : bottomNames) {
                out.add(List.of(a, bn));
            }
        }
        // Порядки не повторяем: перебор и без того растёт как квадрат.
        List<List<String>> uniq = new ArrayList<>();
        for (List<String> seq : out) {
            if (!uniq.contains(seq)) {
                uniq.add(seq);
            }
        }
        return uniq;
    }

    /**
     * КАКИЕ ДЕЙСТВИЯ ПРОСЯТ КАРТЫ ЗАДАНИЙ, лежащие на руке, — из доступных этим
     * ходом. Сперва фокусное задание, потом остальные по цене награды.
     */
    private List<String> действияЗаданий(GameState s, List<String> доступные) {
        PlayerState p = s.player(seat);
        List<String> out = new ArrayList<>();
        List<String> рука = new ArrayList<>(p.objectiveHand);
        // Фокусное — первым: за него бот держится через раунды.
        if (intents.focusObjective != null) {
            рука.remove(intents.focusObjective);
            рука.add(0, intents.focusObjective);
        }
        EngineCardContext ctx = new EngineCardContext(s, seat);
        for (String cid : рука) {
            var card = CardRegistry.objective(cid);
            if (card == null) {
                continue;
            }
            String a;
            try {
                if (card.satisfied(ctx)) {
                    continue;      // карта готова, ей нужен СПЕЦ, а не действие
                }
                a = card.suggestedAction(ctx);
            } catch (RuntimeException notNow) {
                continue;
            }
            if (a != null && !a.isBlank() && доступные.contains(a) && !out.contains(a)) {
                out.add(a);
            }
        }
        return out;
    }

    /**
     * Политика-исполнитель для прогона номер {@code i}. Каждый прогон смотрит на
     * ход со своим уклоном — так планировщик видит РАЗНЫЕ сценарии, а не один и
     * тот же с разбросом:
     * <ul>
     *   <li>0 — чистая формула характера;</li>
     *   <li>1 — в СПЕЦ сперва ВЫПОЛНИТЬ задание;</li>
     *   <li>2 — в СПЕЦ сперва УСТАНОВИТЬ карту арсенала;</li>
     *   <li>3 — агрессия к ЦЕЛИ намерений: ходить к её жетонам, бить её,
     *       в Сборке брать войска;</li>
     *   <li>4 и дальше — то же по кругу, но с шумом.</li>
     * </ul>
     */
    private Agent policy(int i, long seed) {
        Random r = new Random(seed);
        StrategicAgent base = new StrategicAgent(seat, r, genome, character);
        Agent a = base;
        if (i >= 4) {
            a = new NoisyAgent(base, 0.2, r);
        }
        return switch (i % 5) {
            case 1 -> new Prefer(a, Map.of("spec", o -> "spec_objective".equals(o.kind())));
            case 2 -> new Prefer(a, Map.of("spec", o -> "spec_arsenal_install".equals(o.kind())));
            case 3 -> aggressive(a);
            // ХОД РАДИ ЗАДАНИЯ. У исполнителя есть наведение по заданиям — оно
            // подмешивает к каждому мелкому решению прибавку за приближение к
            // картам руки. По умолчанию эта прибавка одна из многих, и на трёх
            // картах руки она размазывается. Здесь она поднята втрое: получается
            // прогон, в котором ход целиком подчинён заданиям. Судит его та же
            // оценка позиции — если подчинение вышло себе дороже, сценарий не
            // выиграет.
            case 4 -> {
                StrategicAgent ради = new StrategicAgent(seat, r,
                    genome.with("objective.pursuit", genome.get("objective.pursuit", 3.0) * 3.0),
                    character);
                yield new Prefer(ради, Map.of("spec", o -> "spec_objective".equals(o.kind())));
            }
            default -> a;
        };
    }

    /** Уклон «война с целью»: движение к жетонам цели, удары по ней, войска в Сборке. */
    @SuppressWarnings("unchecked")
    private Agent aggressive(Agent base) {
        int target = intents.targetSeat;
        Map<String, java.util.function.Predicate<Choice>> pref = new HashMap<>();
        pref.put("assemble", o -> o.payload() instanceof Map<?, ?> m && "unit".equals(m.get("kind")));
        pref.put("attack", o -> {
            if (!(o.payload() instanceof Map<?, ?> m)) {
                return false;
            }
            Object vo = m.get("victim_owner");
            return target < 0 || (vo instanceof Number n && n.intValue() == target)
                || Boolean.TRUE.equals(m.get("neutral")) && target < 0;
        });
        pref.put("move", o -> {
            if (!(o.payload() instanceof Map<?, ?> m) || lastState == null) {
                return false;
            }
            String to = String.valueOf(m.get("to"));
            return zoneOf(lastState, target).contains(to);
        });
        return new Prefer(base, pref);
    }

    private final Map<Integer, java.util.Set<String>> zoneCache = new HashMap<>();
    private long zoneKey = -1;

    /** Гексы цели и их соседи — куда стоит идти, чтобы бить её. */
    private java.util.Set<String> zoneOf(GameState s, int target) {
        long key = s.round * 100L + s.circle;
        if (key != zoneKey) {
            zoneCache.clear();
            zoneKey = key;
        }
        return zoneCache.computeIfAbsent(target, t -> {
            java.util.Set<String> z = new java.util.HashSet<>();
            for (PlayerState p : s.players) {
                if (p.seat == seat || (t >= 0 && p.seat != t)) {
                    continue;
                }
                for (var u : p.unitsOnField()) {
                    z.add(u.hexId);
                    z.addAll(s.field.neighbors(u.hexId));
                }
                for (var b : p.buildingsOnField()) {
                    z.add(b.hexId);
                    z.addAll(s.field.neighbors(b.hexId));
                }
            }
            return z;
        });
    }

    /**
     * Исполнитель с УКЛОНОМ: для названных видов решений сперва предлагает
     * базовой политике только подходящие варианты (и пас); если она берёт пас —
     * решает по всему меню.
     */
    private static final class Prefer extends Agent {
        private final Agent base;
        private final Map<String, java.util.function.Predicate<Choice>> prefer;

        Prefer(Agent base, Map<String, java.util.function.Predicate<Choice>> prefer) {
            super(base.seat, base.name);
            this.base = base;
            this.prefer = prefer;
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> ctx) {
            String kind = ctx == null ? "" : String.valueOf(ctx.getOrDefault("kind", ""));
            java.util.function.Predicate<Choice> p = prefer.get(kind);
            if (p != null) {
                List<Choice> narrowed = new ArrayList<>();
                for (Choice o : options) {
                    if ("pass".equals(o.kind()) || o.payload() == null || p.test(o)) {
                        narrowed.add(o);
                    }
                }
                if (narrowed.size() > 1 && narrowed.size() < options.size()) {
                    Choice c = base.choose(state, narrowed, ctx);
                    if (c.payload() != null) {
                        return c;
                    }
                }
            }
            return base.choose(state, options, ctx);
        }
    }

    /** Повторить сценарий; если жизнь разошлась с планом — решить по обстановке. */
    private Choice follow(GameState s, List<Choice> options, Map<String, Object> ctx, String kind) {
        if (script != null) {
            int end = Math.min(script.size(), cursor + 4);
            for (int j = cursor; j < end; j++) {
                TurnSim.Step st = script.get(j);
                if (!st.kind().equals(kind)) {
                    continue;
                }
                for (Choice o : options) {
                    if (o.kind().equals(st.choiceKind())
                            && (o.label().equals(st.label()) || TurnSim.keyOf(o).equals(st.key()))) {
                        cursor = j + 1;
                        if ("action".equals(kind) && o.payload() != null) {
                            plannedIdx++;
                        }
                        return o;
                    }
                }
                break;   // решение того же вида, но вариантов таких нет — план разошёлся
            }
            scriptMisses++;
            missByKind.merge(kind, 1, Integer::sum);
        }
        if ("action".equals(kind)) {
            // Порядок действий держим и без сценария.
            while (plannedIdx < plannedActions.size()) {
                String want = plannedActions.get(plannedIdx++);
                for (Choice o : options) {
                    if ("action".equals(o.kind()) && want.equals(o.payload())) {
                        return o;
                    }
                }
            }
            if (!plannedActions.isEmpty()) {
                for (Choice o : options) {
                    if ("pass".equals(o.kind())) {
                        return o;   // всё задуманное сыграно
                    }
                }
            }
        }
        return fallback.choose(s, options, ctx);
    }

    // ======================================================================
    //  ПРИКАЗ И СБРОС
    // ======================================================================

    private Choice chooseReveal(GameState s, List<Choice> options) {
        if (!revealBySim) {
            Choice c = fallback.choose(s, options, Map.of("kind", "reveal_order"));
            lastRevealCard = String.valueOf(c.payload());
            return c;
        }
        Map<Choice, Double> v0 = new HashMap<>();
        for (Choice o : options) {
            v0.put(o, bestTurnValue(s, String.valueOf(o.payload()), false, false));
        }
        List<Choice> ranked = new ArrayList<>(options);
        ranked.sort((a, b) -> Double.compare(v0.get(b), v0.get(a)));
        Choice best = null;
        double bestE = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < ranked.size(); i++) {
            Choice o = ranked.get(i);
            String cid = String.valueOf(o.payload());
            double e = v0.get(o);
            if (i < 2 && !isJoker(s, cid)) {
                double pBlock = probEarlierPlays(s, topCode(s, cid));
                String bottom = bottomCode(s, cid);
                double pBottom = bottom == null ? 0 : probEarlierPlays(s, bottom);
                double vBlock = pBlock > 0.05 ? bestTurnValue(s, cid, true, false) : e;
                double vBottom = pBottom > 0.05 ? bestTurnValue(s, cid, false, true) : e;
                e = e - pBlock * Math.max(0, e - vBlock) + pBottom * Math.max(0, vBottom - e);
            }
            e += rng.nextDouble() * 0.02;
            if (e > bestE) {
                bestE = e;
                best = o;
            }
        }
        lastRevealCard = String.valueOf(best.payload());
        return best;
    }

    private Choice chooseDiscard(GameState s, List<Choice> options) {
        Choice worst = null;
        double worstV = Double.POSITIVE_INFINITY;
        for (Choice o : options) {
            String cid = String.valueOf(o.payload());
            double v = bestTurnValueCheap(s, cid);
            if (isJoker(s, cid)) {
                v += 1.0;   // джокер гибче любой карты — расставаться с ним последним
            }
            if (v < worstV) {
                worstV = v;
                worst = o;
            }
        }
        return worst == null ? options.get(0) : worst;
    }

    /** Лучшая оценка хода этой картой (перебор порядков, без шума). */
    private double bestTurnValue(GameState s, String cardId, boolean coincided, boolean bottomOpen) {
        List<String> top = topActions(s, cardId);
        boolean joker = top.size() > 4;
        List<String> bottom = bottomOpen ? bottomActions(s, cardId) : List.of();
        int remaining = coincided ? 1 : 2;
        List<List<String>> seqs = sequences(s, top, remaining, bottom, joker, cardId,
            coincided, bottomOpen);
        double best = Double.NEGATIVE_INFINITY;
        for (List<String> seq : seqs) {
            long seed = rng.nextLong();
            TurnSim.Result r = TurnSim.run(s, seat, cardId, coincided, bottomOpen, seq,
                policy(0, seed), others, seed);
            simulations++;
            if (r != null) {
                best = Math.max(best, PositionValue.value(r.after(), seat, genome, intents));
            }
        }
        return best == Double.NEGATIVE_INFINITY ? -1e6 : best;
    }

    /** Дешёвая оценка карты: политика сама выбирает порядок, один прогон. */
    private double bestTurnValueCheap(GameState s, String cardId) {
        long seed = rng.nextLong();
        TurnSim.Result r = TurnSim.run(s, seat, cardId, false, false, null,
            policy(0, seed), others, seed);
        simulations++;
        return r == null ? -1e6 : PositionValue.value(r.after(), seat, genome, intents);
    }

    // ======================================================================
    //  ОТКРЫТАЯ ИНФОРМАЦИЯ О ПРИКАЗАХ
    // ======================================================================

    private void noteReveals(Map<?, ?> revealed) {
        if (lastState == null) {
            return;
        }
        for (var e : revealed.entrySet()) {
            if (!(e.getKey() instanceof Integer st) || st == seat) {
                continue;
            }
            String code = topCode(lastState, String.valueOf(e.getValue()));
            if (code == null) {
                continue;
            }
            topSeen.computeIfAbsent(st, k -> new HashMap<>()).merge(code, 1, Integer::sum);
        }
    }

    /** Вероятность, что хоть кто-то из ходящих раньше меня в круге вскроет этот приказ. */
    private double probEarlierPlays(GameState s, String code) {
        if (code == null) {
            return 0;
        }
        double none = 1.0;
        List<Integer> order = s.seatsInOrder();
        for (int st : order) {
            if (st == seat) {
                break;
            }
            Map<String, Integer> seen = topSeen.getOrDefault(st, Map.of());
            int total = 0;
            for (int v : seen.values()) {
                total += v;
            }
            double p = (seen.getOrDefault(code, 0) + 0.6) / (total + 4 * 0.6);
            none *= 1.0 - p;
        }
        return 1.0 - none;
    }

    private Map<String, Object> orderCard(GameState s, String cardId) {
        try {
            return Ctx.cards(s, "orders").byId(cardId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean isJoker(GameState s, String cardId) {
        Map<String, Object> c = orderCard(s, cardId);
        return c != null && Boolean.TRUE.equals(c.get("joker"));
    }

    private String topCode(GameState s, String cardId) {
        Map<String, Object> c = orderCard(s, cardId);
        if (c == null || Boolean.TRUE.equals(c.get("joker"))) {
            return null;
        }
        return c.get("top") == null ? null : String.valueOf(c.get("top"));
    }

    private String bottomCode(GameState s, String cardId) {
        Map<String, Object> c = orderCard(s, cardId);
        if (c == null || c.get("bottom") == null) {
            return null;
        }
        return String.valueOf(c.get("bottom"));
    }

    private List<String> topActions(GameState s, String cardId) {
        if (isJoker(s, cardId)) {
            return kelium.engine.Actions.ALL_NAMES;
        }
        String code = topCode(s, cardId);
        return code == null ? List.of() : List.of(Order.ORDER_ACTIONS.get(Order.fromCode(code)));
    }

    private List<String> bottomActions(GameState s, String cardId) {
        String code = bottomCode(s, cardId);
        return code == null ? List.of() : List.of(Order.ORDER_ACTIONS.get(Order.fromCode(code)));
    }

    /** Карта хода не запомнена (нас не спрашивали о вскрытии) — восстановить по действиям. */
    private String guessCard(GameState s, List<Choice> options) {
        PlayerState p = s.player(seat);
        List<String> names = new ArrayList<>();
        for (Choice o : options) {
            if ("action".equals(o.kind()) && o.payload() != null) {
                names.add(String.valueOf(o.payload()));
            }
        }
        // Вскрытая карта — та, которой нет в руке среди пяти; ищем по действиям верха.
        List<String> all = new ArrayList<>(p.orderHand);
        all.addAll(p.orderPlayed);
        if (p.orderSetAside != null) {
            all.add(p.orderSetAside);
        }
        var orders = Ctx.cards(s, "orders");
        for (String cid : orders.ids()) {
            if (all.contains(cid)) {
                continue;
            }
            Map<String, Object> c = orders.byId(cid);
            if (Boolean.TRUE.equals(c.get("joker"))) {
                if (names.size() > 4 && p.orderColor == null) {
                    return cid;
                }
                continue;
            }
            String code = String.valueOf(c.get("top"));
            List<String> acts = List.of(Order.ORDER_ACTIONS.get(Order.fromCode(code)));
            if (names.size() <= 2 && acts.containsAll(names)) {
                return cid;
            }
        }
        return lastRevealCard;
    }

    // ======================================================================
    //  НАМЕРЕНИЯ
    // ======================================================================

    private void refocus(GameState s) {
        PlayerState p = s.player(seat);
        Map<String, Double> prog = new HashMap<>();
        Map<String, Double> val = new HashMap<>();
        EngineCardContext ctx = new EngineCardContext(s, seat);
        for (String cid : p.objectiveHand) {
            var card = CardRegistry.objective(cid);
            prog.put(cid, card == null ? 0.0 : PositionValue.clamp(card.progress(ctx)));
            val.put(cid, PositionValue.rewardValue(s, seat, cid) / 5.0);
        }
        intents.refocus(p.objectiveHand, prog, val);
    }
}
