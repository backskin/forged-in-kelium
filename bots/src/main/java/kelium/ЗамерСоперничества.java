package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.agents.BotCatalog;
import kelium.agents.Bots;
import kelium.agents.Genome;
import kelium.agents.HeuristicAgent;
import kelium.agents.Lookahead;
import kelium.agents.PlannerAgent;
import kelium.agents.PositionValue;
import kelium.agents.Угрозы;
import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.Ctx;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.engine.cards.CardRegistry;
import kelium.engine.cards.EngineCardContext;
import kelium.engine.step.Летопись;
import kelium.engine.step.Позиция;
import kelium.engine.step.Шаг;

/**
 * ЗАМЕР СОПЕРНИЧЕСТВА — играют ли боты против соперников или против таблицы
 * очков (заказ дизайнера 27.09.2026, «боты играют против соперников»).
 *
 * <p>Партии «гроссмейстеров» и «мастеров» на два, три и четыре игрока. Прибор
 * смотрит на партию сверху и видит ВСЁ, в том числе руки, — это можно: он не
 * играет, а меряет. Боты при этом видят только своё.
 *
 * <p>Мерила заказа:
 * <ol>
 *   <li>(а) снос и перенос своего ЦУ: сколько раз и было ли ЦУ под угрозой
 *       (на нём урон или чужие войска за ход дотягиваются до его гекса);</li>
 *   <li>(б) пас или пустое действие, когда было рабочее: действие, после
 *       которого на столе что-то изменилось бы. Проверяется честно — позиция
 *       переигрывается пошаговым API движка с каждым из предложенных действий,
 *       и отпечаток стола сравнивается с отпечатком после паса;</li>
 *   <li>(в) доля ходов «против лидера»: удар по жетону лидера, ход, после
 *       которого задания лидера стали дальше от выполнения, занятая перед ним
 *       ступень науки, совпадение приказа, срезавшее ему действие;</li>
 *   <li>(г) как часто лидер после 5-го раунда выигрывает;</li>
 *   <li>(д) главный источник очков победителя.</li>
 * </ol>
 * Лидер — соперник с наибольшими очками по подсчёту в миг хода.
 *
 * <p>Запуск: {@code kelium.ЗамерСоперничества [режим] [партий на состав] [потоков] [файл]},
 * режим {@code новые}, {@code прежние} или {@code турнир} (новые против прежних
 * на одних раздачах).
 */
public final class ЗамерСоперничества {

    private ЗамерСоперничества() {
    }

    /** Виды решений, на которых кончается «одно действие» при переигровке. */
    private static final java.util.Set<String> КОНЕЦ_ДЕЙСТВИЯ = java.util.Set.of(
        "action", "spec", "order_spec", "reveal_order", "blind_discard",
        "pick_container", "mass_open");

    // ======================================================================
    //  СЧЁТ ОДНОЙ ПАРТИИ
    // ======================================================================

    /** Всё, что насчитано за партию (складывается потом по всем партиям). */
    static final class Счёт {
        int партий;
        int игроков;
        // (а)
        int сносовЦу;
        int сносовБезУгрозы;
        int сносовСпасают;
        int переносовЦу;
        int переносовБезУгрозы;
        // (б)
        int решенийДействия;
        int пасов;
        int пасовПриРабочем;
        int пасовПриРабочемНеХуже;
        int пустыхДействий;
        int пустыхПриРабочем;
        int пустыхПриРабочемНеХуже;
        int[] пасовПоКругу = new int[5];
        int[] пасовПриРабочемПоКругу = new int[5];
        int[] решенийПоКругу = new int[5];
        // (в)
        int ходов;
        int ходовЛидерНеЯ;
        int ходовПротивЛидера;
        int ходовПротивЛидераЛидерНеЯ;
        int ударовПоЛидеру;
        int заданийЛидераСбито;
        int наукиЛидераЗанято;
        int приказовЛидераПерекрыто;
        int ударовВсего;
        int ударовПоНеЛидеру;
        // (г)
        int лидерПосле5;
        int лидерПосле5Выиграл;
        int ничьяЛидеров5;
        // (д)
        final Map<String, Integer> источникПобеды = new TreeMap<>();
        // очки и победы по группам (для турнира: «новые» / «прежние»)
        final Map<String, double[]> группы = new TreeMap<>();   // {мест, очков, побед}
        int раундов;
        long мс;

        void добавить(Счёт o) {
            партий += o.партий;
            сносовЦу += o.сносовЦу;
            сносовБезУгрозы += o.сносовБезУгрозы;
            сносовСпасают += o.сносовСпасают;
            переносовЦу += o.переносовЦу;
            переносовБезУгрозы += o.переносовБезУгрозы;
            решенийДействия += o.решенийДействия;
            пасов += o.пасов;
            пасовПриРабочем += o.пасовПриРабочем;
            пасовПриРабочемНеХуже += o.пасовПриРабочемНеХуже;
            пустыхДействий += o.пустыхДействий;
            пустыхПриРабочем += o.пустыхПриРабочем;
            пустыхПриРабочемНеХуже += o.пустыхПриРабочемНеХуже;
            for (int i = 0; i < 5; i++) {
                пасовПоКругу[i] += o.пасовПоКругу[i];
                пасовПриРабочемПоКругу[i] += o.пасовПриРабочемПоКругу[i];
                решенийПоКругу[i] += o.решенийПоКругу[i];
            }
            ходов += o.ходов;
            ходовЛидерНеЯ += o.ходовЛидерНеЯ;
            ходовПротивЛидера += o.ходовПротивЛидера;
            ходовПротивЛидераЛидерНеЯ += o.ходовПротивЛидераЛидерНеЯ;
            ударовПоЛидеру += o.ударовПоЛидеру;
            заданийЛидераСбито += o.заданийЛидераСбито;
            наукиЛидераЗанято += o.наукиЛидераЗанято;
            приказовЛидераПерекрыто += o.приказовЛидераПерекрыто;
            ударовВсего += o.ударовВсего;
            ударовПоНеЛидеру += o.ударовПоНеЛидеру;
            лидерПосле5 += o.лидерПосле5;
            лидерПосле5Выиграл += o.лидерПосле5Выиграл;
            ничьяЛидеров5 += o.ничьяЛидеров5;
            o.источникПобеды.forEach((k, v) -> источникПобеды.merge(k, v, Integer::sum));
            o.группы.forEach((k, v) -> {
                double[] сюда = группы.computeIfAbsent(k, x -> new double[3]);
                for (int i = 0; i < 3; i++) {
                    сюда[i] += v[i];
                }
            });
            раундов += o.раундов;
            мс += o.мс;
        }
    }

    /** Один ход игрока — что было в начале и что случилось за ход. */
    private static final class Ход {
        int место;
        int круг;
        int лидер;
        boolean лидерНеЯ;
        double заданияЛидераДо;
        int наукаЛидераДо;
        String верх;
        boolean противЛидера;
    }

    // ======================================================================
    //  НАБЛЮДАТЕЛЬ ЗА РЕШЕНИЯМИ
    // ======================================================================

    /**
     * Обёртка бота: решает сам бот, обёртка только смотрит. На решениях «какое
     * действие» переигрывает позицию с каждым предложенным вариантом.
     */
    private static final class Наблюдатель extends Agent {
        private final Agent бот;
        private final Счёт счёт;
        private Летопись летопись;
        private final Genome судья;
        /** Гекс ЦУ перед сносом своими руками — чтобы узнать перенос. */
        private String цуДоСноса;
        private boolean сносБезУгрозы;

        Наблюдатель(Agent бот, Счёт счёт, Genome судья) {
            super(бот.seat, бот.name);
            this.бот = бот;
            this.счёт = счёт;
            this.судья = судья;
        }

        @Override
        public boolean specInActionMenu() {
            return бот.specInActionMenu();
        }

        @Override
        public void observeEvent(Map<String, Object> event) {
            бот.observeEvent(event);
        }

        @Override
        public void observePublicEvent(Map<String, Object> event) {
            бот.observePublicEvent(event);
        }

        @Override
        public Choice choose(GameState s, List<Choice> options, Map<String, Object> ctx) {
            String вид = ctx == null ? "" : String.valueOf(ctx.getOrDefault("kind", ""));
            Позиция здесь = летопись == null ? null : летопись.сейчас();
            Choice выбор = бот.choose(s, options, ctx);
            try {
                if ("build_pick".equals(вид) && Угрозы.трогаетСвоёЦу(s, seat, выбор)) {
                    boolean угроза = Угрозы.цуПодУгрозой(s, seat);
                    boolean спасает = Угрозы.грозитВоеннаяПобеда(s, seat);
                    счёт.сносовЦу++;
                    if (!угроза) {
                        счёт.сносовБезУгрозы++;
                    }
                    if (спасает) {
                        счёт.сносовСпасают++;
                    }
                    цуДоСноса = Угрозы.гексЦу(s.player(seat));
                    сносБезУгрозы = !угроза;
                } else if ("cu_hex".equals(вид) && цуДоСноса != null) {
                    if (!цуДоСноса.equals(String.valueOf(выбор.payload()))) {
                        счёт.переносовЦу++;
                        if (сносБезУгрозы) {
                            счёт.переносовБезУгрозы++;
                        }
                    }
                    цуДоСноса = null;
                } else if ("action".equals(вид) && здесь != null) {
                    разобратьДействие(s, options, выбор, здесь);
                }
            } catch (RuntimeException | StackOverflowError e) {
                // прибор не должен ронять партию
            }
            return выбор;
        }

        /** (б): был ли рабочий вариант, и чем оказался выбор. */
        private void разобратьДействие(GameState s, List<Choice> options, Choice выбор,
                                       Позиция здесь) {
            boolean естьДействия = false;
            for (Choice o : options) {
                if ("action".equals(o.kind()) && o.payload() != null) {
                    естьДействия = true;
                }
            }
            if (!естьДействия) {
                return;
            }
            int пасИ = -1;
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).payload() == null) {
                    пасИ = i;
                }
            }
            if (пасИ < 0) {
                return;
            }
            int круг = Math.max(1, Math.min(4, s.circle));
            счёт.решенийДействия++;
            счёт.решенийПоКругу[круг]++;
            long[] отпечаток = new long[options.size()];
            double[] цена = new double[options.size()];
            boolean[] годен = new boolean[options.size()];
            for (int i = 0; i < options.size(); i++) {
                Choice o = options.get(i);
                if (o.payload() != null && !"action".equals(o.kind())) {
                    continue;   // спец-действие в меню хода — не про это мерило
                }
                GameState после = переиграть(здесь.применить(i, "action"), seat, s.numPlayers());
                if (после == null) {
                    continue;
                }
                годен[i] = true;
                отпечаток[i] = Lookahead.materialSignature(после, seat);
                цена[i] = PositionValue.value(после, seat, судья, null);
            }
            if (!годен[пасИ]) {
                return;
            }
            boolean рабочий = false;
            boolean рабочийНеХуже = false;
            for (int i = 0; i < options.size(); i++) {
                if (i == пасИ || !годен[i]) {
                    continue;
                }
                if (отпечаток[i] != отпечаток[пасИ]) {
                    рабочий = true;
                    if (цена[i] >= цена[пасИ] - 0.25) {
                        рабочийНеХуже = true;
                    }
                }
            }
            int взял = options.indexOf(выбор);
            if (выбор.payload() == null) {
                счёт.пасов++;
                счёт.пасовПоКругу[круг]++;
                if (рабочий) {
                    счёт.пасовПриРабочем++;
                    счёт.пасовПриРабочемПоКругу[круг]++;
                }
                if (рабочийНеХуже) {
                    счёт.пасовПриРабочемНеХуже++;
                }
            } else if (взял >= 0 && годен[взял] && отпечаток[взял] == отпечаток[пасИ]) {
                счёт.пустыхДействий++;
                if (рабочий) {
                    счёт.пустыхПриРабочем++;
                }
                if (рабочийНеХуже) {
                    счёт.пустыхПриРабочемНеХуже++;
                }
            }
        }
    }

    /**
     * ПЕРЕИГРАТЬ ОДНО ДЕЙСТВИЕ: позиция повторяется с выбранным вариантом, а
     * дальше за игрока решает «делатель» — берёт любой непустой вариант, пока
     * действие не кончится. Как только движок спрашивает следующее действие,
     * спец-действие или другого игрока — розыгрыш обрывается.
     */
    static GameState переиграть(Позиция позиция, int seat, int players) {
        List<Agent> после = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            после.add(new Делатель(i, seat));
        }
        try {
            return Шаг.прогнать(позиция, null, после);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Делает что-нибудь непустое в пределах одного действия, затем обрывает. */
    private static final class Делатель extends Agent {
        private final int чей;
        private final HeuristicAgent ум;

        Делатель(int seat, int чей) {
            super(seat, "делатель#" + seat);
            this.чей = чей;
            this.ум = new HeuristicAgent(seat, new Random(seat * 131L + 7), "balanced");
        }

        @Override
        public Choice choose(GameState s, List<Choice> options, Map<String, Object> ctx) {
            String вид = ctx == null ? "" : String.valueOf(ctx.getOrDefault("kind", ""));
            if (seat != чей || КОНЕЦ_ДЕЙСТВИЯ.contains(вид)) {
                throw new Шаг.Обрыв();
            }
            List<Choice> дело = new ArrayList<>();
            for (Choice o : options) {
                if (o.payload() != null && !"pass".equals(o.kind())) {
                    дело.add(o);
                }
            }
            if (дело.isEmpty()) {
                return options.get(0);
            }
            if (дело.size() == 1) {
                return дело.get(0);
            }
            try {
                return ум.choose(s, дело, ctx);
            } catch (RuntimeException e) {
                return дело.get(0);
            }
        }
    }

    // ======================================================================
    //  ОДНА ПАРТИЯ
    // ======================================================================

    /** Сумма прогресса заданий на руке игрока — прибор видит руки, боты нет. */
    static double прогрессЗаданий(GameState s, int seat) {
        double sum = 0;
        EngineCardContext cc = new EngineCardContext(s, seat);
        for (String cid : s.player(seat).objectiveHand) {
            try {
                var card = CardRegistry.objective(cid);
                if (card != null) {
                    double p = card.progress(cc);
                    if (!Double.isNaN(p)) {
                        sum += Math.max(0, Math.min(1, p));
                    }
                }
            } catch (RuntimeException e) {
                // карта не умеет сказать — пропускаем
            }
        }
        return sum;
    }

    /** Сколько свободных и оплачиваемых ступеней науки у игрока (открыто). */
    static int наукаДоступно(GameState s, int seat) {
        return Угрозы.наукаДоступно(s, s.player(seat));
    }

    /** Код верхнего приказа карты (null — джокер). */
    static String верх(GameState s, String cardId) {
        try {
            Map<String, Object> c = Ctx.cards(s, "orders").byId(cardId);
            return c == null || Boolean.TRUE.equals(c.get("joker")) || c.get("top") == null
                ? null : String.valueOf(c.get("top"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Сыграть одну партию.
     *
     * @param новые для каждого места — новый бот (true) или прежний (false)
     */
    static Счёт партия(int players, long seed, List<String> ids, boolean[] новые) {
        Счёт счёт = new Счёт();
        счёт.партий = 1;
        счёт.игроков = players;
        long t0 = System.nanoTime();
        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, players, seed,
            null, null);
        GameState s = Setup.buildGame(cfg);
        Genome судья = PlannerAgent.plannerDefaults("balanced", players);
        List<Agent> боты = new ArrayList<>();
        List<Наблюдатель> наблюдатели = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            Random rng = new Random(seed * 31 + i * 977L);
            Agent a = новые[i] ? BotCatalog.create(ids.get(i), i, rng, players)
                : Bots.createPrevious(ids.get(i), i, rng, players);
            Наблюдатель н = new Наблюдатель(a, счёт, судья);
            наблюдатели.add(н);
            боты.add(н);
        }
        Летопись летопись = new Летопись();
        List<Agent> играют = летопись.подключить(s, боты);
        for (Наблюдатель н : наблюдатели) {
            н.летопись = летопись;
        }
        Ход[] текущий = new Ход[1];
        Map<Integer, Ход> ходыКруга = new HashMap<>();
        int[] кругНомер = {-1};
        boolean[] раунд6 = {false};
        int[] лидер5 = {-2};
        GameEngine.playGame(s, играют, ev -> {
            String type = String.valueOf(ev.get("type"));
            try {
                switch (type) {
                    case "turn_orders" -> {
                        int st = ((Number) ev.get("seat")).intValue();
                        int круг = s.round * 10 + s.circle;
                        if (круг != кругНомер[0]) {
                            кругНомер[0] = круг;
                            ходыКруга.clear();
                        }
                        if (s.round >= 6 && !раунд6[0]) {
                            раунд6[0] = true;
                            лидер5[0] = единственныйЛидер(s);
                        }
                        Ход х = new Ход();
                        х.место = st;
                        х.круг = s.circle;
                        х.лидер = Угрозы.лидерПоОчкам(s, st);
                        х.лидерНеЯ = х.лидер >= 0
                            && Угрозы.очки(s, х.лидер) >= Угрозы.очки(s, st);
                        х.заданияЛидераДо = х.лидер < 0 ? 0 : прогрессЗаданий(s, х.лидер);
                        х.наукаЛидераДо = х.лидер < 0 ? 0 : наукаДоступно(s, х.лидер);
                        х.верх = ev.get("top") == null ? null : String.valueOf(ev.get("top"));
                        // Совпадение срезало мне действие — кто из ходивших раньше
                        // в этом круге вскрыл тот же верх и считал меня лидером?
                        if (Boolean.TRUE.equals(ev.get("coincided")) && х.верх != null) {
                            for (Ход раньше : ходыКруга.values()) {
                                if (х.верх.equals(раньше.верх) && раньше.лидер == st) {
                                    if (!раньше.противЛидера) {
                                        раньше.противЛидера = true;
                                        счёт.ходовПротивЛидера++;
                                        if (раньше.лидерНеЯ) {
                                            счёт.ходовПротивЛидераЛидерНеЯ++;
                                        }
                                    }
                                    счёт.приказовЛидераПерекрыто++;
                                }
                            }
                        }
                        текущий[0] = х;
                        ходыКруга.put(st, х);
                        счёт.ходов++;
                        if (х.лидерНеЯ) {
                            счёт.ходовЛидерНеЯ++;
                        }
                    }
                    case "combat_hit" -> {
                        Ход х = текущий[0];
                        if (х != null && ev.get("seat") instanceof Number by
                                && by.intValue() == х.место
                                && ev.get("victim_owner") instanceof Number vo
                                && vo.intValue() >= 0 && vo.intValue() != х.место) {
                            счёт.ударовВсего++;
                            if (vo.intValue() == х.лидер) {
                                счёт.ударовПоЛидеру++;
                                отметить(счёт, х);
                            } else {
                                счёт.ударовПоНеЛидеру++;
                            }
                        }
                    }
                    case "turn_end" -> {
                        Ход х = текущий[0];
                        if (х != null && х.лидер >= 0 && ev.get("seat") instanceof Number st
                                && st.intValue() == х.место) {
                            double после = прогрессЗаданий(s, х.лидер);
                            if (после < х.заданияЛидераДо - 0.01
                                    && s.player(х.лидер).objectiveHand.size() > 0) {
                                счёт.заданийЛидераСбито++;
                                отметить(счёт, х);
                            }
                            if (наукаДоступно(s, х.лидер) < х.наукаЛидераДо) {
                                счёт.наукиЛидераЗанято++;
                                отметить(счёт, х);
                            }
                            текущий[0] = null;
                        }
                    }
                    default -> { }
                }
            } catch (RuntimeException e) {
                // прибор не должен ронять партию
            }
        });
        счёт.раундов = s.round;
        List<Integer> победители = new ArrayList<>(s.winners);
        if (победители.isEmpty() && s.winner != null) {
            победители.add(s.winner);
        }
        if (лидер5[0] >= 0) {
            счёт.лидерПосле5++;
            if (победители.contains(лидер5[0])) {
                счёт.лидерПосле5Выиграл++;
            }
        } else if (лидер5[0] == -1) {
            счёт.ничьяЛидеров5++;
        }
        for (int w : победители) {
            Map<String, Integer> разбор = Scoring.scorePlayer(s, w);
            String лучший = "—";
            int макс = Integer.MIN_VALUE;
            for (var e : разбор.entrySet()) {
                if ("total".equals(e.getKey())) {
                    continue;
                }
                if (e.getValue() > макс) {
                    макс = e.getValue();
                    лучший = e.getKey();
                }
            }
            String как = s.winCondition == null ? "" : s.winCondition;
            if (!как.isEmpty() && !как.startsWith("victory") && !как.startsWith("shared")) {
                лучший = "победа: " + как;
            }
            счёт.источникПобеды.merge(лучший, 1, Integer::sum);
        }
        for (int i = 0; i < players; i++) {
            String группа = новые[i] ? "новые" : "прежние";
            double[] g = счёт.группы.computeIfAbsent(группа, k -> new double[3]);
            g[0] += 1;
            g[1] += Угрозы.очки(s, i);
            if (победители.contains(i)) {
                g[2] += 1.0 / победители.size();
            }
        }
        счёт.мс = (System.nanoTime() - t0) / 1_000_000L;
        return счёт;
    }

    private static void отметить(Счёт счёт, Ход х) {
        if (!х.противЛидера) {
            х.противЛидера = true;
            счёт.ходовПротивЛидера++;
            if (х.лидерНеЯ) {
                счёт.ходовПротивЛидераЛидерНеЯ++;
            }
        }
    }

    /** Лидер по очкам за столом; −1 — ничья наверху. */
    private static int единственныйЛидер(GameState s) {
        int best = -1;
        int bestV = Integer.MIN_VALUE;
        boolean ничья = false;
        for (PlayerState p : s.players) {
            int v = Угрозы.очки(s, p.seat);
            if (v > bestV) {
                bestV = v;
                best = p.seat;
                ничья = false;
            } else if (v == bestV) {
                ничья = true;
            }
        }
        return ничья ? -1 : best;
    }

    // ======================================================================
    //  СТЕНД
    // ======================================================================

    private static final List<String> ХАРАКТЕРЫ = Bots.ROSTER_4;

    /** Состав стола: характеры по кругу, уровни гроссмейстер и мастер вперемешку. */
    static List<String> состав(int players, int g) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            String ch = ХАРАКТЕРЫ.get((i + g) % ХАРАКТЕРЫ.size());
            int level = (g + i) % 2 == 0 ? 4 : 3;
            ids.add(ch + ":" + level);
        }
        return ids;
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8);
        String режим = args.length > 0 ? args[0] : "новые";
        int партийНаСостав = args.length > 1 ? Integer.parseInt(args[1]) : 34;
        int ядер = Runtime.getRuntime().availableProcessors();
        int потоков = args.length > 2 ? Integer.parseInt(args[2]) : Math.max(1, ядер - 2);
        потоков = Math.max(1, Math.min(потоков, Math.max(1, ядер - 2)));
        Path файл = Path.of(args.length > 3 ? args[3]
            : "reports/боты/замер-соперничества-" + режим + ".md");

        record Задача(int players, int g, long seed, List<String> ids, boolean[] новые) {
        }
        List<Задача> задачи = new ArrayList<>();
        for (int players = 2; players <= 4; players++) {
            for (int g = 0; g < партийНаСостав; g++) {
                long seed = 27_090_000L + players * 10_000L + g;
                // В ТУРНИРЕ ОДНА РАЗДАЧА ИГРАЕТСЯ ДВАЖДЫ — новые и прежние меняются
                // местами, характеры за местами те же. Так раздача и место за
                // столом сокращаются, и разница — это разница ботов.
                int раздача = "турнир".equals(режим) ? g / 2 : g;
                List<String> ids = состав(players, раздача);
                boolean[] новые = new boolean[players];
                for (int i = 0; i < players; i++) {
                    новые[i] = switch (режим) {
                        case "прежние" -> false;
                        case "турнир" -> ((i + раздача) % 2 == 0) == (g % 2 == 0);
                        default -> true;
                    };
                }
                if ("турнир".equals(режим)) {
                    seed = 27_090_000L + players * 10_000L + раздача;
                }
                задачи.add(new Задача(players, g, seed, ids, новые));
            }
        }
        out.println("Замер соперничества: режим " + режим + ", партий " + задачи.size()
            + ", потоков " + потоков + ", свод " + GameConfig.DEFAULT_RULESET);
        ExecutorService pool = Executors.newFixedThreadPool(потоков);
        Map<Integer, Счёт> поСоставу = new TreeMap<>();
        Счёт всего = new Счёт();
        List<Future<Счёт>> fs = new ArrayList<>();
        for (Задача з : задачи) {
            fs.add(pool.submit(() -> партия(з.players(), з.seed(), з.ids(), з.новые())));
        }
        long t0 = System.nanoTime();
        int готово = 0;
        for (int i = 0; i < fs.size(); i++) {
            Счёт с;
            try {
                с = fs.get(i).get();
            } catch (Exception e) {
                out.println("партия " + i + " сорвалась: " + e);
                continue;
            }
            поСоставу.computeIfAbsent(задачи.get(i).players(), k -> new Счёт()).добавить(с);
            всего.добавить(с);
            готово++;
            if (готово % 10 == 0) {
                out.printf(Locale.ROOT, "  готово %d/%d, %.0f с%n", готово, fs.size(),
                    (System.nanoTime() - t0) / 1e9);
            }
        }
        pool.shutdown();
        String md = отчёт(режим, поСоставу, всего, потоков);
        Files.createDirectories(файл.toAbsolutePath().getParent());
        Files.writeString(файл, md, StandardCharsets.UTF_8);
        out.println(md);
        out.println("записано: " + файл.toAbsolutePath());
    }

    private static String доля(int a, int b) {
        return b == 0 ? "—" : String.format(Locale.ROOT, "%.1f%%", 100.0 * a / b);
    }

    static String отчёт(String режим, Map<Integer, Счёт> поСоставу, Счёт всего, int потоков) {
        StringBuilder b = new StringBuilder();
        b.append("# Замер соперничества — ").append(режим).append("\n\n");
        b.append("Свод ").append(GameConfig.DEFAULT_RULESET).append(", партий ")
            .append(всего.партий).append(", потоков ").append(потоков)
            .append(". Состав: каратель, ловчий, снабженец, зодчий по кругу, ")
            .append("уровни гроссмейстер и мастер вперемешку.\n\n");
        b.append("| мерило | всего | 2 игрока | 3 игрока | 4 игрока |\n|---|---:|---:|---:|---:|\n");
        List<Счёт> кол = new ArrayList<>();
        кол.add(всего);
        for (int p = 2; p <= 4; p++) {
            кол.add(поСоставу.getOrDefault(p, new Счёт()));
        }
        строка(b, "партий", кол, с -> String.valueOf(с.партий));
        строка(b, "(а) снос своего ЦУ, раз", кол, с -> String.valueOf(с.сносовЦу));
        строка(b, "(а) из них без угрозы ЦУ", кол, с -> String.valueOf(с.сносовБезУгрозы));
        строка(b, "(а) из них спасали от военной победы", кол, с -> String.valueOf(с.сносовСпасают));
        строка(b, "(а) перенос своего ЦУ (снёс и поставил в другом месте)", кол,
            с -> String.valueOf(с.переносовЦу));
        строка(b, "(а) из них без угрозы ЦУ", кол, с -> String.valueOf(с.переносовБезУгрозы));
        строка(b, "(б) решений «какое действие»", кол, с -> String.valueOf(с.решенийДействия));
        строка(b, "(б) пасов", кол, с -> доля(с.пасов, с.решенийДействия));
        строка(b, "(б) пас при рабочем варианте (от всех решений)", кол,
            с -> доля(с.пасовПриРабочем, с.решенийДействия));
        строка(b, "(б) пас при рабочем варианте, который не хуже паса", кол,
            с -> доля(с.пасовПриРабочемНеХуже, с.решенийДействия));
        строка(b, "(б) пустое действие при рабочем варианте", кол,
            с -> доля(с.пустыхПриРабочем, с.решенийДействия));
        for (int k = 1; k <= 4; k++) {
            int kk = k;
            строка(b, "(б) пас при рабочем в " + k + "-м круге раунда (от решений круга)", кол,
                с -> доля(с.пасовПриРабочемПоКругу[kk], с.решенийПоКругу[kk]));
        }
        строка(b, "(в) ходов", кол, с -> String.valueOf(с.ходов));
        строка(b, "(в) ходов против лидера", кол, с -> доля(с.ходовПротивЛидера, с.ходов));
        строка(b, "(в) то же, когда лидер — не я", кол,
            с -> доля(с.ходовПротивЛидераЛидерНеЯ, с.ходовЛидерНеЯ));
        строка(b, "(в) удары по лидеру / все удары по соперникам", кол,
            с -> с.ударовПоЛидеру + " / " + с.ударовВсего + " (" + доля(с.ударовПоЛидеру, с.ударовВсего) + ")");
        строка(b, "(в) ходов, отдаливших задания лидера", кол, с -> String.valueOf(с.заданийЛидераСбито));
        строка(b, "(в) ходов, отнявших у лидера ступень науки", кол, с -> String.valueOf(с.наукиЛидераЗанято));
        строка(b, "(в) приказов лидера перекрыто", кол, с -> String.valueOf(с.приказовЛидераПерекрыто));
        строка(b, "(г) лидер после 5-го раунда выиграл", кол,
            с -> с.лидерПосле5Выиграл + " из " + с.лидерПосле5 + " (" + доля(с.лидерПосле5Выиграл, с.лидерПосле5) + ")");
        строка(b, "(г) ничья наверху после 5-го раунда", кол, с -> String.valueOf(с.ничьяЛидеров5));
        строка(b, "раундов в среднем", кол, с -> с.партий == 0 ? "—"
            : String.format(Locale.ROOT, "%.2f", (double) с.раундов / с.партий));
        строка(b, "секунд на партию (один поток)", кол, с -> с.партий == 0 ? "—"
            : String.format(Locale.ROOT, "%.1f", с.мс / 1000.0 / с.партий));
        b.append("\n## (д) Главный источник очков победителя\n\n| источник | партий |\n|---|---:|\n");
        всего.источникПобеды.entrySet().stream()
            .sorted((x, y) -> y.getValue() - x.getValue())
            .forEach(e -> b.append("| ").append(e.getKey()).append(" | ").append(e.getValue())
                .append(" (").append(доля(e.getValue(), всего.источникПобеды.values().stream()
                    .mapToInt(Integer::intValue).sum())).append(") |\n"));
        if (всего.группы.size() > 1) {
            b.append("\n## Турнир: новые против прежних (одни раздачи, места по кругу)\n\n");
            b.append("| боты | мест | очков в среднем | побед | доля побед на место |\n|---|---:|---:|---:|---:|\n");
            for (var e : всего.группы.entrySet()) {
                double[] g = e.getValue();
                b.append(String.format(Locale.ROOT, "| %s | %.0f | %.2f | %.1f | %.1f%% |%n",
                    e.getKey(), g[0], g[1] / Math.max(1, g[0]), g[2], 100.0 * g[2] / Math.max(1, g[0])));
            }
            for (int p = 2; p <= 4; p++) {
                Счёт с = поСоставу.get(p);
                if (с == null) {
                    continue;
                }
                b.append("\n").append(p).append(" игрока: ");
                for (var e : с.группы.entrySet()) {
                    double[] g = e.getValue();
                    b.append(String.format(Locale.ROOT, "%s — %.2f очка, побед %.1f из %.0f мест; ",
                        e.getKey(), g[1] / Math.max(1, g[0]), g[2], g[0]));
                }
                b.append('\n');
            }
        }
        return b.toString();
    }

    private static void строка(StringBuilder b, String имя, List<Счёт> кол,
                               java.util.function.Function<Счёт, String> f) {
        b.append("| ").append(имя);
        for (Счёт с : кол) {
            b.append(" | ").append(f.apply(с));
        }
        b.append(" |\n");
    }
}
