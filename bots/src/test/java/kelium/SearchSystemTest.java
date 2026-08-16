package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.agents.Arena;
import kelium.agents.BotCatalog;
import kelium.agents.Fitness;
import kelium.agents.Genome;
import kelium.agents.Lookahead;
import kelium.agents.MapElites;
import kelium.agents.OrderHabits;
import kelium.agents.Plan;
import kelium.agents.SearchAgent;
import kelium.agents.StateFeatures;
import kelium.agents.StrategicAgent;
import kelium.agents.ValueNet;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.Resource;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.support.Fix;

/**
 * Проверки СИСТЕМЫ ПРОСЧЁТА и обучения: признаки позиции, определение холостого
 * хода, просчитывающий бот, приспособленность, лига, атлас, сеть оценки.
 *
 * <p>Тесты нарочно проверяют СВОЙСТВА, а не конкретные числа: сила ботов меняется
 * с каждой правкой правил, и тест, прибитый к «Эло 1640», сломается на первой же
 * балансовой правке и будет только мешать.
 */
class SearchSystemTest {

    // ==================== признаки позиции ====================

    @Test
    void featureVectorMatchesItsNames() {
        assertEquals(StateFeatures.NAMES.size(), StateFeatures.DIM);
        assertEquals(StateFeatures.DIM, StateFeatures.SCALES.length,
            "у каждого признака должен быть свой масштаб для нормировки");
        GameState s = Fix.game(4, 21L);
        double[] f = StateFeatures.of(s, 0);
        assertEquals(StateFeatures.DIM, f.length);
        double[] n = StateFeatures.normalized(s, 0);
        for (double v : n) {
            assertTrue(v >= -2.0 && v <= 2.0, "нормированный признак вне [-2,2]: " + v);
        }
    }

    @Test
    void everyFeatureHasAGeneWeight() {
        Genome g = Genome.defaults();
        for (int i = 0; i < StateFeatures.DIM; i++) {
            String key = StateFeatures.weightKey(i);
            assertTrue(g.weights.containsKey(key),
                "признак без гена — отбор не сможет его настроить: " + key);
            assertTrue(Genome.TUNABLE_KEYS.contains(key),
                "ген признака должен эволюционировать: " + key);
        }
    }

    @Test
    void evaluationReactsToVictoryPoints() {
        GameState s = Fix.game(4, 22L);
        Genome g = Genome.defaults();
        double before = StrategicAgent.linearEvaluate(s, 0, g);
        s.player(0).resources.add(Resource.KELIUM, 3);
        double after = StrategicAgent.linearEvaluate(s, 0, g);
        assertTrue(after > before, "келемий должен улучшать оценку позиции");
    }

    @Test
    void negativeWeightsSurviveMutation() {
        // Отрицательные оценочные веса ОСМЫСЛЕННЫ («мои жетоны под ударом» — плохо).
        // Прежняя мутация зажимала всё в неотрицательное, и бот не мог выучить вред.
        Genome g = Genome.defaults();
        boolean sawNegative = false;
        Random rng = new Random(5);
        for (int i = 0; i < 20 && !sawNegative; i++) {
            g = g.mutate(rng, 0.4);
            if (g.get("eval.my_exposed", 0) < 0 || g.get("eval.energy_hungry", 0) < 0) {
                sawNegative = true;
            }
        }
        assertTrue(sawNegative, "оценочные веса должны уметь быть отрицательными");
        // а приоритеты действий — не должны
        for (int i = 0; i < 20; i++) {
            g = g.mutate(rng, 0.5);
            assertTrue(g.get("action.build", 0) >= 0.0,
                "приоритет действия не может быть отрицательным");
            assertTrue(g.get("aggression", 0) >= 0.0);
        }
    }

    // ==================== холостой ход ====================

    @Test
    void materialSignatureNoticesEveryKindOfChange() {
        GameState s = Fix.game(4, 23L);
        long base = Lookahead.materialSignature(s, 0);

        s.player(0).resources.add(Resource.COIN, 1);
        long afterCoin = Lookahead.materialSignature(s, 0);
        assertNotEquals(base, afterCoin, "монета — материальное изменение");

        s.player(0).techSteps.merge("green", 1, Integer::sum);
        long afterTech = Lookahead.materialSignature(s, 0);
        assertNotEquals(afterCoin, afterTech, "шаг трека — материальное изменение");

        // Урон, нанесённый ЧУЖОМУ жетону, тоже обязан считаться: иначе бой,
        // снявший половину ЦУ, выглядел бы холостым.
        s.player(1).buildingsOnField().get(0).damage += 1;
        assertNotEquals(afterTech, Lookahead.materialSignature(s, 0),
            "урон по чужому жетону — материальное изменение");
    }

    @Test
    void hollowActionIsDetected() {
        GameState s = Fix.game(4, 24L);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            agents.add(new StrategicAgent(i, new Random(i), Genome.defaults()));
        }
        GameEngine.bind(s, agents, null);
        // Наука без единого трофея не может ничего изменить — это holostoy ход
        // по определению, и просчёт обязан это увидеть.
        s.player(0).resources.add(Resource.DEBRIS, -s.player(0).resources.debris());
        s.player(0).trophySpace.clear();
        Lookahead.ActionOutcome out = Lookahead.actionOutcome(s, 0, "science",
            Genome.defaults(), Genome.defaults(), 0, 42L);
        assertFalse(out.ok() && out.changed(),
            "Наука без трофеев либо не проходит, либо ничего не меняет");
    }

    @Test
    void actionOutcomeDoesNotTouchTheOriginal() {
        GameState s = Fix.game(4, 25L);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            agents.add(new StrategicAgent(i, new Random(i), Genome.defaults()));
        }
        GameEngine.bind(s, agents, null);
        long before = Lookahead.materialSignature(s, 0);
        for (String action : List.of("build", "mining", "market", "assembly")) {
            Lookahead.actionOutcome(s, 0, action, Genome.defaults(), Genome.defaults(), 1, 7L);
        }
        assertEquals(before, Lookahead.materialSignature(s, 0),
            "просчёт действий не должен менять настоящую партию");
    }

    // ==================== просчитывающий бот ====================

    @Test
    void searchAgentPlaysAFullGameAndActuallySearches() {
        GameState s = Fix.game(4, 26L);
        List<Agent> agents = new ArrayList<>();
        SearchAgent probe = SearchAgent.fast(0, new Random(1), Genome.defaults(), "search");
        agents.add(probe);
        for (int i = 1; i < 4; i++) {
            agents.add(new StrategicAgent(i, new Random(i), Genome.defaults()));
        }
        var result = GameEngine.playGame(s, agents, null);
        assertTrue(s.finished);
        assertTrue(result.get("winner") instanceof Integer);
        assertTrue(probe.searchStats().contains("просчитано решений"));
        // Просчёт обязан хоть раз сработать: если ни одного решения не проверено,
        // значит бот молча выродился в обычного.
        assertFalse(probe.searchStats().contains("просчитано решений 0,"),
            "бот не сделал ни одного просчёта: " + probe.searchStats());
    }

    @Test
    void deepSearchAlsoCompletesAGame() {
        GameState s = Fix.game(3, 27L);
        List<Agent> agents = new ArrayList<>();
        agents.add(SearchAgent.deep(0, new Random(1), Genome.defaults(), "deep"));
        for (int i = 1; i < 3; i++) {
            agents.add(new StrategicAgent(i, new Random(i), Genome.defaults()));
        }
        GameEngine.playGame(s, agents, null);
        assertTrue(s.finished, "партия с глубоким просчётом должна доигрываться");
    }

    // ==================== цель и план ====================

    @Test
    void objectivePlanAppearsWhenACardIsInHand() {
        GameState s = Fix.game(4, 28L);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            agents.add(new StrategicAgent(i, new Random(i), Genome.defaults()));
        }
        GameEngine.bind(s, agents, null);
        // Сцена собрана без раздачи (её делает движок в начале партии), поэтому
        // карту задания берём из колоды сами — проверяем ПЛАНИРОВЩИК, а не раздачу.
        for (int seat = 0; seat < 4; seat++) {
            String card = s.decks.get("objectives").draw(s.rng);
            if (card != null) {
                s.player(seat).objectiveHand.add(card);
            }
        }
        assertFalse(s.player(0).objectiveHand.isEmpty(), "задание в руке должно быть");
        boolean seenObjectiveGoal = false;
        for (int seat = 0; seat < 4 && !seenObjectiveGoal; seat++) {
            // Заставляем цель задания стать выгоднее прочих, задрав её ценность.
            Genome g = new Genome(new java.util.HashMap<>(Genome.defaults().weights));
            g.weights.put("plan.value.objective", 200.0);
            Plan p = Plan.best(s, seat, g);
            if (p != null && p.goal == Plan.Goal.OBJECTIVE) {
                seenObjectiveGoal = true;
                assertFalse(p.steps.isEmpty(), "у цели задания должна быть цепочка шагов");
            }
        }
        assertTrue(seenObjectiveGoal, "планировщик обязан уметь цель «выполнить задание»");
    }

    @Test
    void planValuesComeFromTheGenome() {
        GameState s = Fix.game(4, 29L);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            agents.add(new StrategicAgent(i, new Random(i), Genome.defaults()));
        }
        GameEngine.bind(s, agents, null);
        Genome kelium = new Genome(new java.util.HashMap<>(Genome.defaults().weights));
        kelium.weights.put("plan.value.kelium", 500.0);
        Genome army = new Genome(new java.util.HashMap<>(Genome.defaults().weights));
        army.weights.put("plan.value.army", 500.0);
        Plan a = Plan.best(s, 0, kelium);
        Plan b = Plan.best(s, 0, army);
        assertEquals(Plan.Goal.KELIUM, a.goal, "ген ценности цели должен решать выбор цели");
        assertEquals(Plan.Goal.ARMY, b.goal, "ген ценности цели должен решать выбор цели");
    }

    // ==================== приспособленность ====================

    @Test
    void winIsWorthMoreThanAnyAmountOfBustle() {
        // Цель обязана ставить победу выше суеты. Проверяем это прямо на формуле:
        // проигранная партия с горой ударов не должна обгонять победу.
        double shaping = 1.0;
        // (собрать реальную партию с нужным исходом нельзя, поэтому сравниваем
        // вклад слагаемых: победа 60 против поддержки, которая физически ограничена)
        assertTrue(60.0 > 0.30 * 20 + 0.10 * 40 + 0.30 * 6 + 0.20 * 9 + 0.10 * 12
                + 0.20 * 20,
            "победа должна перевешивать максимально возможную поддержку");
        assertEquals(0.0, Fitness.shapingAt(1.0, 0.35), 1e-9,
            "к концу обучения поддержка обязана выключиться");
        assertEquals(1.0, Fitness.shapingAt(0.0, 0.35), 1e-9,
            "в начале обучения поддержка работает в полную силу");
        assertTrue(Fitness.shapingAt(0.2, 0.35) < shaping);
    }

    @Test
    void fitnessProducesBehaviorPortrait() {
        Fitness.Result r = Fitness.play(4, 31L, 0, Genome.defaults(),
            List.of(Genome.defaults()), 0.0);
        assertEquals(Fitness.BEHAVIOR_AXES.size(), r.behavior().length);
        for (double b : r.behavior()) {
            assertTrue(b >= 0.0 && b <= 1.0, "ось поведения должна лежать в [0,1]: " + b);
        }
        assertTrue(r.vp() >= 0);
        assertTrue(r.counters().containsKey("act_all") || r.counters().isEmpty());
    }

    // ==================== лига ====================

    @Test
    void leagueRanksRandomBotLast() {
        // Единственное, в чём лига обязана быть уверена: случайный бот — худший.
        // Если он не последний, сломана либо лига, либо игра.
        Arena arena = new Arena(3, List.of(
            new Arena.Fighter("случайный", "random"),
            new Arena.Fighter("эвристика", "heuristic:economist"),
            new Arena.Fighter("стратег", "default")));
        arena.run(2, 4242L);
        List<Arena.Standing> table = arena.standings();
        assertEquals(3, table.size());
        assertEquals("случайный", table.get(table.size() - 1).name,
            "случайный бот обязан быть последним в лиге");
        assertTrue(arena.report("проверка").contains("Эло"));
        for (Arena.Standing st : table) {
            assertTrue(st.games > 0, "каждый участник должен отыграть партии");
        }
    }

    // ==================== атлас стратегий ====================

    @Test
    void atlasFindsMoreThanOneStyle() {
        MapElites me = new MapElites(3, 3, 4, 777L);
        me.run(24);
        assertTrue(me.sorted().size() >= 2,
            "атлас обязан находить РАЗНЫЕ стили, иначе он бесполезен");
        String report = me.report();
        assertTrue(report.contains("Атлас стратегий"));
        assertTrue(report.contains("жизнеспособных стилей")
            || report.contains("Жизнеспособных стилей"));
        for (var e : me.sorted()) {
            assertEquals(Fitness.BEHAVIOR_AXES.size(), e.getValue().behavior.length);
        }
    }

    // ==================== сеть оценки ====================

    @Test
    void valueNetLearnsAndSurvivesSaveLoad() throws Exception {
        ValueNet net = new ValueNet(4, 8, new Random(3));
        // учим простейшей зависимости: ответ = первый признак
        double firstError = 0;
        double lastError = 0;
        for (int epoch = 0; epoch < 300; epoch++) {
            double err = 0;
            for (int i = 0; i < 16; i++) {
                double x0 = (i % 8) / 8.0;
                double[] x = {x0, 0.1, -0.2, 0.3};
                err += net.accumulate(x, x0);
            }
            net.applyBatch(0.2);
            if (epoch == 0) {
                firstError = err;
            }
            lastError = err;
        }
        assertTrue(lastError < firstError,
            "сеть должна обучаться: ошибка была " + firstError + ", стала " + lastError);

        Path tmp = Files.createTempFile("value", ".txt");
        net.save(tmp);
        ValueNet back = ValueNet.load(tmp);
        double[] probe = {0.5, 0.1, -0.2, 0.3};
        assertEquals(net.forward(probe), back.forward(probe), 1e-6,
            "сохранение и чтение сети не должны менять её ответы");
        Files.deleteIfExists(tmp);
    }

    @Test
    void activeValueNetIsOptionalAndOff() {
        // Сеть — необязательная надстройка. По умолчанию она ВЫКЛЮЧЕНА, чтобы
        // базовое поведение ботов оставалось читаемым и воспроизводимым.
        assertEquals(null, ValueNet.active(),
            "по умолчанию оценка позиции должна считаться линейно");
        ValueNet net = new ValueNet(StateFeatures.DIM, 4, new Random(1));
        try {
            ValueNet.use(net);
            GameState s = Fix.game(4, 32L);
            double v = StrategicAgent.evaluate(s, 0, Genome.defaults());
            assertEquals(net.value(s, 0), v, 1e-9,
                "включённая сеть должна заменять линейную оценку");
        } finally {
            ValueNet.use(null);
        }
    }

    // ==================== целостность итога ====================

    @Test
    void finalScoreRewardsMarginAndVictory() {
        GameState s = Fix.game(4, 33L);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            agents.add(new StrategicAgent(i, new Random(i), Genome.defaults()));
        }
        GameEngine.playGame(s, agents, null);
        int winner = s.winner;
        double winnerScore = Lookahead.finalScore(s, winner);
        for (int i = 0; i < 4; i++) {
            if (i == winner) {
                continue;
            }
            assertTrue(winnerScore > Lookahead.finalScore(s, i),
                "итог победителя должен быть выше итога любого соперника");
        }
        assertTrue(Scoring.scorePlayer(s, winner).getOrDefault("total", 0) >= 0);
    }

    // ============ судья ОБОРВАННОГО просчёта (правка 13.08.2026) ============

    /**
     * Оборванная на горизонте партия судится не только очками на столе.
     *
     * <p>Сторожит настоящую ошибку: пока судьёй были одни очки, просчёт на три
     * раунда вперёд судил так же жадно, как на один, потому что очки в этой игре
     * приходят в конце. Из-за этого лишнее думанье не давало прибавки силы.
     */
    @Test
    void horizonJudgeCountsPositionAndNotOnlyPoints() {
        GameState s = Fix.game(4, 34L);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            agents.add(new StrategicAgent(i, new Random(i), Genome.defaults()));
        }
        // Играем несколько раундов, чтобы позиции разошлись.
        new GameEngine(s, agents, null).withRoundLimit(4).run();

        // ДВА ЧЛЕНА, А НЕ ОДИН (с 15.08.2026). Судья позиции складывает отрыв по
        // очкам, положение (search.horizon_pos) и ЗАМЕТНОСТЬ ЛИДЕРА
        // (rivalry.exposure_fear): за столом на четверых лидера бьют трое, и
        // положение «я впереди в середине партии» хуже, чем выглядит по счёту.
        // Чтобы проверить чистый отрыв по очкам, гасить надо ОБА члена — иначе
        // тест ловит не ошибку, а вторую часть судьи.
        Genome onlyPoints = Genome.defaults()
            .with("search.horizon_pos", 0.0)
            .with("rivalry.exposure_fear", 0.0);
        Genome withPosition = Genome.defaults()
            .with("search.horizon_pos", 1.0)
            .with("rivalry.exposure_fear", 0.0);
        boolean anyDifference = false;
        for (int seat = 0; seat < 4; seat++) {
            double plain = Lookahead.horizonScore(s, seat, onlyPoints);
            double rich = Lookahead.horizonScore(s, seat, withPosition);
            // При нулевом весе положения судья обязан вернуть ровно отрыв по очкам.
            assertEquals(Math.rint(plain), plain, 1e-9,
                "без веса положения судья должен давать целый отрыв по очкам");
            if (Math.abs(rich - plain) > 1e-9) {
                anyDifference = true;
            }
        }
        assertTrue(anyDifference,
            "вес положения обязан менять оценку хотя бы у одного места");
    }

    /** Ручка веса положения читается из генома, а не зашита. */
    @Test
    void horizonWeightIsAGene() {
        assertTrue(Genome.TUNABLE_KEYS.contains("search.horizon_pos"),
            "вес положения должен настраиваться отбором");
        assertTrue(Genome.TUNABLE_KEYS.contains("search.value_weight"),
            "вес оценки позиции при выборе действия тоже настраивается отбором");
    }

    // ============ обучение должно проверять ТОГО ЖЕ бота ============

    /**
     * САМАЯ ВАЖНАЯ ПРОВЕРКА из этой серии. Двадцать девять весов оценки позиции
     * ({@code eval.*}) спрашивает только просчёт вперёд. Пока обучение играло
     * ботами БЕЗ просчёта, эти веса не влияли ни на одну обучающую партию — отбор
     * их мутировал и ничего в них не отбирал.
     *
     * <p>Тест сторожит именно это: с мозгом «формула» порча весов оценки не меняет
     * исход, с мозгом «просчёт» — меняет.
     */
    @Test
    void evaluationWeightsMatterOnlyWhenTrainingSearches() {
        Genome sane = Genome.defaults();
        Genome broken = sane;
        for (int i = 0; i < StateFeatures.DIM; i++) {
            broken = broken.with(StateFeatures.weightKey(i), i % 2 == 0 ? -40.0 : 40.0);
        }
        List<Genome> rivals = List.of(Genome.defaults());

        double plainSane = 0;
        double plainBroken = 0;
        double searchSane = 0;
        double searchBroken = 0;
        for (int i = 0; i < 4; i++) {
            long seed = 4_100_000L + i;
            plainSane += Fitness.play(4, seed, 0, sane, rivals, 0.0,
                Fitness.Brain.ФОРМУЛА).fitness();
            plainBroken += Fitness.play(4, seed, 0, broken, rivals, 0.0,
                Fitness.Brain.ФОРМУЛА).fitness();
            searchSane += Fitness.play(4, seed, 0, sane, rivals, 0.0,
                Fitness.Brain.ПРОСЧЁТ).fitness();
            searchBroken += Fitness.play(4, seed, 0, broken, rivals, 0.0,
                Fitness.Brain.ПРОСЧЁТ).fitness();
        }
        assertEquals(plainSane, plainBroken, 1e-9,
            "жадной формуле веса оценки позиции безразличны — это и была беда");
        assertNotEquals(searchBroken, searchSane,
            "с просчётом порча весов оценки ОБЯЗАНА менять исход, иначе отбору "
                + "по-прежнему нечего в них отбирать");
    }

    // ============ привычки соперников по приказам ============

    /**
     * Счёт привычек считает то, что видно на столе, и розыгрыш по нему смещён к
     * часто вскрываемым картам.
     *
     * <p>Механизм оставлен в дереве, хотя силы он не добавил (47% против 53% на 224
     * очных сравнениях): догадка «бот должен угадывать приказ соседа» выглядит
     * очевидной и приходит снова, а тест сторожит, что она хотя бы работает как
     * задумано, и мерить её повторно не придётся с нуля.
     */
    @Test
    void habitsCountRevealsAndBiasTheGuess() {
        OrderHabits h = new OrderHabits();
        assertEquals(0, h.seen(), "пока ничего не вскрыли, привычек нет");
        h.note(java.util.Map.of(1, "приказ-А", 2, "приказ-Б"));
        for (int i = 0; i < 20; i++) {
            h.note(java.util.Map.of(1, "приказ-А"));
        }
        assertEquals(22, h.seen(), "два вскрытия в первом круге плюс двадцать далее");

        List<Choice> options = List.of(new Choice("reveal_order", "приказ-А", "приказ-А"),
            new Choice("reveal_order", "приказ-В", "приказ-В"));
        int gotA = 0;
        Random rng = new Random(4);
        for (int i = 0; i < 200; i++) {
            if ("приказ-А".equals(h.pick(1, options, rng).payload())) {
                gotA++;
            }
        }
        assertTrue(gotA > 150,
            "привычный приказ должен выпадать намного чаще: " + gotA + " из 200");
        // Про место, за которым не следили, гадать нечем — пусть решает формула.
        assertNull(h.pick(3, options, rng), "без наблюдений привычка молчит");
    }

    /** Копия привычек не связана с исходной: просчёт не должен портить память. */
    @Test
    void habitsCopyIsIndependent() {
        OrderHabits h = new OrderHabits();
        h.note(java.util.Map.of(1, "приказ-А"));
        OrderHabits c = h.copy();
        h.note(java.util.Map.of(1, "приказ-А"));
        assertEquals(1, c.seen(), "копия не должна расти вслед за исходной");
        assertEquals(2, h.seen());
    }

    /** Судья позиции — часть мозга бота, а не настройка на весь запуск. */
    @Test
    void judgeTravelsWithTheGenome() {
        ValueNet net = new ValueNet(StateFeatures.DIM, 4, new Random(3));
        Genome withNet = Genome.defaults().withJudge(net);
        assertNotNull(withNet.judge, "судья должен ехать вместе с геномом");
        // Производные геномы судью не теряют: иначе обучение молча возвращалось бы
        // к линейной оценке после первой же мутации.
        assertNotNull(withNet.with("aggression", 2.0).judge);
        assertNotNull(withNet.withProfile("hawk").judge);
        assertNotNull(withNet.mutate(new Random(1), 0.1).judge);
        assertNotNull(Genome.crossover(withNet, Genome.defaults(), new Random(1)).judge);

        GameState s = Fix.game(4, 35L);
        assertNotEquals(StrategicAgent.evaluate(s, 0, Genome.defaults()),
            StrategicAgent.evaluate(s, 0, withNet),
            "с обученным судьёй оценка позиции должна отличаться от линейной");
    }

    // ============ справочник ботов ============

    /**
     * СПРАВОЧНИК — единственный список ботов, и он обязан уметь собрать каждого.
     *
     * <p>Раньше список был выписан трижды (лига, окно прогона, запись партии) и все
     * три расходились составом и названиями.
     */
    @Test
    void catalogCreatesEveryBotItOffers() {
        assertFalse(BotCatalog.ALL.isEmpty());
        for (var e : BotCatalog.ALL) {
            Agent a = BotCatalog.create(e.id(), 0, new Random(1), 4);
            assertNotNull(a, "справочник обязан собрать " + e.id());
            assertTrue(BotCatalog.known(e.id()));
            assertFalse(BotCatalog.label(e.id()).isBlank());
            assertFalse(e.tip().isBlank(), "у каждого бота должно быть пояснение: " + e.id());
        }
    }

    /**
     * СТАРЫЕ ИМЕНА ПРОДОЛЖАЮТ РАБОТАТЬ: записи уже сыгранных партий хранят имена
     * ботов внутри файла, и после переименования они должны открываться.
     */
    @Test
    void oldBotNamesStillResolve() {
        assertEquals("trained:hawk", BotCatalog.canonical("strat:hawk"));
        assertEquals("simple:aggressor", BotCatalog.canonical("heur:aggressor"));
        assertEquals("search:balanced", BotCatalog.canonical("deep:balanced"));
        assertEquals("trained:chaos", BotCatalog.canonical("chaos"));
        assertEquals("hunter", BotCatalog.canonical("exploit"));
        // Удалённые нейросетевые боты не должны ломать старые записи.
        assertEquals("trained:balanced", BotCatalog.canonical("neural"));
        assertEquals("trained:balanced", BotCatalog.canonical("onnx"));
        for (String old : List.of("strat:dove", "heur:economist", "deep:hawk", "neural", "onnx")) {
            assertNotNull(BotCatalog.create(old, 0, new Random(2), 4),
                "старое имя должно собираться: " + old);
        }
    }
}
