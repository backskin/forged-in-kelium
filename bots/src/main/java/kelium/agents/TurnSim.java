package kelium.agents;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.engine.GameEngine;

/**
 * СИМУЛЯЦИЯ ОДНОГО ХОДА НА КОПИИ СОСТОЯНИЯ с записью всех принятых решений.
 *
 * <p>Планировщик хода задаёт ПОРЯДОК ОСНОВНЫХ ДЕЙСТВИЙ (например «сначала
 * Движение, потом Бой»), а все остальные решения хода — куда идти, чем бить,
 * какое СПЕЦ-действие сыграть — принимает политика-исполнитель. Каждый выбор
 * записывается в СЦЕНАРИЙ: список пар (вид решения, подпись выбранного
 * варианта). Лучший сценарий планировщик потом ПОВТОРЯЕТ в настоящей партии.
 *
 * <p>Ход на копии разыгрывается тем же кодом движка, что и в живой партии
 * ({@link GameEngine#simulateTurn}) — никаких упрощённых моделей.
 */
public final class TurnSim {

    private TurnSim() {
    }

    /**
     * Один записанный выбор.
     *
     * @param key устойчивый ключ варианта: для начинки-таблицы — её значимые
     *            поля (жетон, куда, строка, цель), без цены в боеприпасах и
     *            прочих подписей, которые могут разойтись из-за случайных карт
     */
    public record Step(String kind, String label, String choiceKind, String key) {
    }

    /** Ключ варианта — по начинке, а не по подписи. */
    public static String keyOf(Choice c) {
        Object p = c.payload();
        if (p == null) {
            return c.kind() + "|pass";
        }
        if (p instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder(c.kind()).append('|');
            for (String f : List.of("uid", "to", "b", "row", "target", "tcat", "kind",
                    "building", "neutral", "victim_uid", "btype", "hex", "card", "track")) {
                Object v = m.get(f);
                if (v != null) {
                    sb.append(f).append('=').append(v).append(';');
                }
            }
            return sb.length() > c.kind().length() + 1 ? sb.toString() : c.kind() + "|" + c.label();
        }
        if (p instanceof String || p instanceof Number || p instanceof Boolean || p instanceof Enum<?>) {
            return c.kind() + "|" + p;
        }
        return c.kind() + "|" + c.label();
    }

    /**
     * Итог симуляции: состояние после хода и сценарий, которым к нему пришли.
     *
     * @param working  сыгранных действий, после которых на столе что-то
     *                 изменилось (материальный отпечаток {@link Lookahead#materialSignature})
     * @param idle     сыгранных действий, после которых не изменилось ничего
     * @param hits     чьи жетоны я бил за ход: место владельца → число попаданий
     */
    public record Result(GameState after, List<Step> script, List<String> actionsPlayed,
                         int working, int idle, Map<Integer, Integer> hits) {
        public Result(GameState after, List<Step> script, List<String> actionsPlayed) {
            this(after, script, actionsPlayed, 0, 0, Map.of());
        }
    }

    /**
     * Агент-исполнитель на копии: основные действия — по заданному порядку,
     * остальное — политикой; всё записывается.
     */
    static final class Recorder extends Agent {
        private final Agent policy;
        private final Deque<String> actionOrder;
        final List<Step> script = new ArrayList<>();
        final List<String> actionsPlayed = new ArrayList<>();
        /** Отпечаток стола в миг каждого вопроса «какое действие» и был ли перед ним сыгран ход. */
        final List<Long> отпечатки = new ArrayList<>();
        final List<Boolean> сыграно = new ArrayList<>();
        final Map<Integer, Integer> удары = new java.util.HashMap<>();

        Recorder(int seat, Agent policy, List<String> actionOrder) {
            super(seat, "сценарий#" + seat);
            this.policy = policy;
            this.actionOrder = actionOrder == null ? null : new ArrayDeque<>(actionOrder);
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> ctx) {
            String kind = ctx == null ? "" : String.valueOf(ctx.getOrDefault("kind", ""));
            Choice pick = null;
            if ("action".equals(kind)) {
                отпечатки.add(Lookahead.materialSignature(state, seat));
            }
            if ("action".equals(kind) && actionOrder != null) {
                while (pick == null && !actionOrder.isEmpty()) {
                    String want = actionOrder.poll();
                    for (Choice o : options) {
                        if ("action".equals(o.kind()) && want.equals(o.payload())) {
                            pick = o;
                            break;
                        }
                    }
                }
                if (pick == null) {
                    pick = pass(options);
                } else {
                    actionsPlayed.add(String.valueOf(pick.payload()));
                }
            } else {
                pick = policy.choose(state, options, ctx);
                if ("action".equals(kind) && pick.payload() != null) {
                    actionsPlayed.add(String.valueOf(pick.payload()));
                }
            }
            script.add(new Step(kind, pick.label(), pick.kind(), keyOf(pick)));
            if ("action".equals(kind)) {
                сыграно.add(pick.payload() != null && "action".equals(pick.kind()));
            }
            return pick;
        }

        @Override
        public void observePublicEvent(Map<String, Object> event) {
            policy.observePublicEvent(event);
            if ("combat_hit".equals(event.get("type")) && event.get("seat") instanceof Number by
                    && by.intValue() == seat && event.get("victim_owner") instanceof Number vo
                    && vo.intValue() >= 0 && vo.intValue() != seat) {
                удары.merge(vo.intValue(), 1, Integer::sum);
            }
            // нейтральные постройки — под ключом −1
            Object t = event.get("type");
            if (("damage_neutral".equals(t) || "raze_neutral".equals(t))
                    && event.get("seat") instanceof Number by && by.intValue() == seat) {
                удары.merge(-1, 1, Integer::sum);
            }
        }

        /** Сколько сыгранных действий что-то сделали и сколько — ничего. */
        int[] рабочие(long конец) {
            int раб = 0;
            int пуст = 0;
            for (int i = 0; i < сыграно.size(); i++) {
                if (!сыграно.get(i)) {
                    continue;
                }
                long до = отпечатки.get(i);
                long после = i + 1 < отпечатки.size() ? отпечатки.get(i + 1) : конец;
                if (до != после) {
                    раб++;
                } else {
                    пуст++;
                }
            }
            return new int[]{раб, пуст};
        }

        private static Choice pass(List<Choice> options) {
            for (Choice o : options) {
                if ("pass".equals(o.kind()) || o.payload() == null) {
                    return o;
                }
            }
            return options.get(options.size() - 1);
        }
    }

    /**
     * Разыграть ход места {@code seat} картой {@code cardId} на копии {@code real}.
     *
     * @param actionOrder порядок основных действий ({@code null} — решает политика)
     * @param policy      исполнитель прочих решений (на копии; создаётся вызывающим)
     * @param others      модель соперников для решений, которые движок задаёт им
     */
    public static Result run(GameState real, int seat, String cardId, boolean coincided,
                             boolean bottomOpen, List<String> actionOrder, Agent policy,
                             Genome others, long seed) {
        return run(real, seat, cardId, coincided, bottomOpen, actionOrder, policy, others,
            seed, false);
    }

    /**
     * @param честно не подглядывать: перед прогоном закрытое в копии (руки
     *               соперников, порядок колод) перемешивается так, как его видит
     *               игрок за столом ({@link Доигрывание#перемешатьСкрытое})
     */
    public static Result run(GameState real, int seat, String cardId, boolean coincided,
                             boolean bottomOpen, List<String> actionOrder, Agent policy,
                             Genome others, long seed, boolean честно) {
        GameState c = real.deepCopy(seed);
        if (честно) {
            Доигрывание.перемешатьСкрытое(c, seat, new Random(seed ^ 0x2545F4914F6CDD1DL));
        }
        List<Agent> agents = new ArrayList<>();
        Recorder rec = new Recorder(seat, policy, actionOrder);
        for (int i = 0; i < c.numPlayers(); i++) {
            if (i == seat) {
                agents.add(rec);
            } else {
                agents.add(new StrategicAgent(i, new Random(seed * 31 + i + 7), others,
                    "модель"));
            }
        }
        GameEngine.bindResume(c, agents, null);
        GameEngine engine = new GameEngine(c, agents, null);
        try {
            engine.simulateTurn(seat, cardId, coincided, bottomOpen);
        } catch (RuntimeException e) {
            // Сценарий сорвался — это не находка, а брак: вызывающий увидит
            // пустой итог и не станет ему верить.
            return null;
        }
        int[] раб = rec.рабочие(Lookahead.materialSignature(c, seat));
        return new Result(c, rec.script, rec.actionsPlayed, раб[0], раб[1],
            Map.copyOf(rec.удары));
    }
}
