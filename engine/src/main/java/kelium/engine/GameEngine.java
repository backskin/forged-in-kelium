package kelium.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.core.PlayerState;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.rules.Ruleset;
import kelium.core.TurnJournal;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.dataio.Ctx;

/**
 * Движок раундов — ведёт партию от подготовки до подсчёта очков.
 *
 * <p>Структура раунда (источник истины §2):
 * <ol>
 *   <li>Обновление (пропускается в 1-м раунде): передать жетон первого игрока,
 *       заменить карту маркета, восстановить грядки, снять урон, разложить
 *       контейнеры.</li>
 *   <li>Слепой сброс: каждый игрок откладывает 1 из 5 карт приказов.</li>
 *   <li>Четыре круга: все игроки ОДНОВРЕМЕННО вскрывают карту приказа; разрешают
 *       по часовой стрелке от первого игрока (правило совпадения + нижний приказ).</li>
 *   <li>Возврат: карты обратно в руку, возврат захваченных жетонов, добор
 *       заданий, проверка условий конца.</li>
 * </ol>
 *
 * <p>Каждое решение предлагается агенту места списком {@link Choice}, поэтому один
 * и тот же цикл управляет случайными, эвристическими и будущими RL-агентами.
 *
 * <p>Вертикальный срез: SPEC-действия (задания/арсенал/супер-задания) сведены к
 * заглушке — карточная надстройка портируется отдельно
 * (TODO: портировать из forge/engine/objectives.py, modules.py, engine._offer_spec).
 */
public final class GameEngine {

    private final GameState state;
    private final List<Agent> agents;
    private final Consumer<Map<String, Object>> onEvent;
    // ПОСЛЕДНЕЕ ИЗ УСЛОВИЙ КОНЦА — «кончились карты рынка» (уточнение дизайнера
    // 13.08.2026). Это страховка, а не заданная длина партии: обычно раньше
    // срабатывает другое условие (вершины треков, последний тайл зарождения,
    // мгновенная победа), и так и задумано.
    //
    // Почему предел вообще есть: карт рынка восемь, каждый раунд выкладывается
    // одна, колода НЕ пересобирается из сброса — дальше игре нечем идти. Число
    // раундов поэтому не написано ни здесь, ни в наборе правил, а СЧИТАЕТСЯ по
    // числу печатных карт рынка (см. lastRoundByMarket): поменяется колода —
    // поменяется и предел, без правок кода.
    //
    // Раньше здесь стояла константа 8 «на всякий случай», и это был третий
    // источник правил помимо СВОДа и данных.
    private static final int RESERVE_ROUND_CAP_FALLBACK = 8;
    private int maxRounds = RESERVE_ROUND_CAP_FALLBACK;
    /**
     * До какого раунда доигрывать. По умолчанию резервный предел из правил.
     * Просчёт вперёд ставит меньший ГОРИЗОНТ: доиграть 2-3 раунда дешевле, чем всю
     * партию, а для сравнения вариантов этого обычно достаточно.
     */
    private int roundLimit = RESERVE_ROUND_CAP_FALLBACK;

    /** Резервный предел раундов ЭТОЙ партии — из набора правил. */
    /**
     * ПРЕДЕЛ РАУНДОВ = сколько карт рынка напечатано.
     *
     * <p>Это не «длина партии», а последнее условие конца: дальше идти нечем,
     * потому что карты рынка кончились. Нормальный ход партии — оборваться раньше
     * по одному из условий {@link #peacefulEnd()} или мгновенной победой.
     *
     * <p>Число берётся из самих карт, а не из настройки: иначе состав колоды и
     * предел могли бы разъехаться, и правило пришлось бы держать в двух местах.
     *
     * <p>Настройка {@code rounds.reserve_cap} остаётся как ЯВНОЕ переопределение
     * для опытов («а если партия на 11 раундов»), но по умолчанию её нет и длину
     * задают карты.
     */
    private int lastRoundByMarket() {
        Object v = rs().get("rounds.reserve_cap", null);
        if (v instanceof Number n && n.intValue() > 0) {
            return n.intValue();          // опыт задал длину явно
        }
        // Считаем ПЕЧАТНЫЕ карты рынка, а не те, что дошли до колоды. Разница
        // существенна: на подготовке движок отсеивает карты с нереализованными
        // эффектами, и 13.08.2026 из восьми карт рынка в колоду попадало ШЕСТЬ —
        // то есть партия молча становилась шестираундовой. Длина партии не должна
        // зависеть от того, что мы успели запрограммировать.
        try {
            int printed = kelium.dataio.Ctx.cfg(state).content.get("market").entries.size();
            if (printed > 0) {
                return printed;
            }
        } catch (RuntimeException ignored) {
            // содержимое недоступно (сцена в тесте) — падаем на запасное число
        }
        return RESERVE_ROUND_CAP_FALLBACK;
    }

    public GameEngine(GameState state, List<Agent> agents, Consumer<Map<String, Object>> onEvent) {
        this.state = state;
        this.agents = agents;
        this.onEvent = onEvent;
    }

    /** Ограничить горизонт: доигрывать не дальше раунда {@code lastRound}. */
    public GameEngine withRoundLimit(int lastRound) {
        this.roundLimit = Math.max(1, Math.min(lastRoundByMarket(), lastRound));
        return this;
    }

    private Ruleset rs() {
        return Ctx.rules(state);
    }

    private kelium.dataio.ContentSet orders() {
        return Ctx.cards(state, "orders");
    }

    /** Испустить событие: передать во внешний коллбэк и уведомить агента места. */
    private void emit(Map<String, Object> event) {
        if (onEvent != null) {
            onEvent.accept(event);
        }
        Object seat = event.get("seat");
        if (seat instanceof Integer si && si >= 0 && si < agents.size()) {
            agents.get(si).observeEvent(event);
        }
        // ОТКРЫТАЯ ИНФОРМАЦИЯ: происходящее на столе видят ВСЕ. Нужно ботам,
        // которые помнят, кто их ударил (см. Agent.observePublicEvent).
        for (Agent a : agents) {
            a.observePublicEvent(event);
        }
    }

    private static Map<String, Object> ev(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** Прогнать полную партию и вернуть итог (winner, condition, scores, rounds). */
    public Map<String, Object> run() {
        GameState s = state;
        Ruleset rs = rs();
        // Горизонт не задавали — играем до резервного предела из правил.
        maxRounds = roundLimit == RESERVE_ROUND_CAP_FALLBACK ? lastRoundByMarket() : roundLimit;

        bind(s, agents, this::emit);

        emit(ev("type", "game_start", "players", s.numPlayers(), "ruleset", rs.id));
        dealStart();
        offerSuperPick();
        offerStartObjectivePick();
        loop(1, 1, true);
        return finishAndScore();
    }

    /**
     * Проиграть партию ДО КОНЦА раунда {@code lastRound} и остановиться, НЕ
     * подводя итоги: состояние остаётся живым, партию можно продолжать.
     *
     * <p>Нужно пробникам и разбору: чтобы просчитать что-то из середины партии,
     * эту середину надо сперва получить. Обычный {@code run()} с ограничением
     * раундов не годится — он объявляет победителя и закрывает партию.
     */
    public void runToRound(int lastRound) {
        GameState s = state;
        maxRounds = Math.max(1, Math.min(lastRoundByMarket(), lastRound));
        bind(s, agents, this::emit);
        emit(ev("type", "game_start", "players", s.numPlayers(), "ruleset", rs().id));
        dealStart();
        loop(1, 1, true);
    }

    /**
     * ПРОДОЛЖИТЬ партию с текущего места — с начала круга {@code state.circle}
     * раунда {@code state.round}. Нужно ПРОСЧЁТУ ВПЕРЁД: бот копирует состояние
     * ({@link GameState#deepCopy}) и доигрывает копию до конца, чтобы увидеть, к
     * чему приведёт решение, а не угадывать это по формуле.
     *
     * <p>Начало круга — единственная честная точка возобновления: руки приказов
     * ещё целы, ходы не начаты. Вскрытие приказов ОДНОВРЕМЕННОЕ и закрытое,
     * поэтому в копии соперники выбирают приказ заново — это не подглядывание, а
     * честная выборка из неизвестного, ровно как рассуждает живой игрок.
     *
     * <p>Подготовка (раздача) и Обновление текущего раунда НЕ повторяются.
     */
    public Map<String, Object> resume() {
        GameState s = state;
        maxRounds = roundLimit;
        bindResume(s, agents, onEvent == null ? null : this::emit);
        loop(Math.max(1, s.round), Math.max(1, s.circle), false);
        return finishAndScore();
    }

    /**
     * Общий цикл раундов. {@code freshRound} = раунд {@code startRound} играется
     * с самого начала (Обновление + слепой сброс); иначе раунд продолжается с
     * круга {@code startCircle}.
     */
    private void loop(int startRound, int startCircle, boolean freshRound) {
        GameState s = state;
        int circles = rs().getInt("rounds.circles_per_round");
        for (int rnd = startRound; rnd <= maxRounds; rnd++) {
            s.round = rnd;
            int firstCircle = 1;
            if (rnd > startRound || freshRound) {
                refresh(rnd);
                blindDiscard();
            } else {
                firstCircle = startCircle;
            }
            for (int circle = firstCircle; circle <= circles; circle++) {
                s.circle = circle;
                playCircle(circle);
                if (s.finished) {
                    break;
                }
            }
            // I4: после мгновенной победы шаг Возврата НЕ играется
            if (s.finished) {
                break;
            }
            // Возврат СНАЧАЛА проверяет условия конца партии (уточнение
            // 2026-08-15): peacefulEnd() — чистая проверка состояния (пики
            // треков, тайлы зарождения), её результат не зависит от того,
            // вызвана она до returnStep() или после, поэтому можно спросить
            // заранее и передать флаг «это последний Возврат» внутрь.
            boolean gameEnding = peacefulEnd() || rnd >= maxRounds;
            returnStep(gameEnding);
            if (s.finished || gameEnding) {
                break;
            }
        }
    }

    private Map<String, Object> finishAndScore() {
        GameState s = state;
        Map<Integer, Map<String, Integer>> scores = Scoring.scoreAll(s);
        if (s.winner == null) {
            int best = 0;
            for (int i = 1; i < s.numPlayers(); i++) {
                if (better(scores, s, i, best)) {
                    best = i;
                }
            }
            s.winner = best;
            if (s.winCondition == null) {
                s.winCondition = "victory_points";
            }
        }
        s.finished = true;
        emit(ev("type", "game_end", "winner", s.winner, "condition", s.winCondition, "scores", scores));

        Map<String, Object> result = new HashMap<>();
        result.put("winner", s.winner);
        result.put("condition", s.winCondition);
        result.put("scores", scores);
        result.put("rounds", s.round);
        return result;
    }

    // Больше очков; ничья ломается по келемию, затем по монетам.
    private static boolean better(Map<Integer, Map<String, Integer>> scores, GameState s, int i, int best) {
        int ti = scores.get(i).get("total");
        int tb = scores.get(best).get("total");
        if (ti != tb) {
            return ti > tb;
        }
        int ki = s.players.get(i).resources.kelium();
        int kb = s.players.get(best).resources.kelium();
        if (ki != kb) {
            return ki > kb;
        }
        return s.players.get(i).resources.coin() > s.players.get(best).resources.coin();
    }

    // ---- помощники подготовки --------------------------------------------
    /**
     * Раздать стартовые руки приказов. ВАЖНО (правило): у каждого игрока СВОЯ
     * цветная колода приказов — 4 карты (по одной на каждый верхний приказ) плюс
     * одна карта БЕЗОПАСНОСТЬ. Именно принадлежность колоде задаёт асимметрию
     * нижних приказов (голубой цикл вперёд, алый назад, зелёный обмен, жёлтый —
     * зеркало на Операции). Цвета раздаются игрокам СЛУЧАЙНО по сиду.
     */
    /**
     * Цвет, выбранный за столом на это место, или {@code null} — «раздай сам».
     * Берётся из настроек стола, а не из правил: это решение игроков, а не
     * редакции правил.
     */
    private static String seatColorPick(int seat) {
        java.util.List<kelium.dataio.GameConfig.SeatPick> all =
            kelium.dataio.GameConfig.seatPickAll();
        if (seat >= all.size() || all.get(seat) == null) {
            return null;
        }
        return all.get(seat).orderColor();
    }

    @SuppressWarnings("unchecked")
    private void dealStart() {
        GameState s = state;
        // Сгруппировать id приказов по цвету колоды; джокеры (security) отдельно.
        // (цвет, выбранный за столом, см. seatColorPick ниже)
        Map<String, List<String>> byDeck = new java.util.LinkedHashMap<>();
        List<String> securities = new ArrayList<>();
        for (Map<String, Object> e : orders().entries) {
            String id = (String) e.get("id");
            if (Boolean.TRUE.equals(e.get("joker"))) {
                securities.add(id);
                continue;
            }
            String deck = String.valueOf(e.get("deck"));
            byDeck.computeIfAbsent(deck, k -> new ArrayList<>()).add(id);
        }
        // Список цветов, перемешать по сиду и раздать местам.
        List<String> colors = new ArrayList<>(byDeck.keySet());
        Collections.shuffle(colors, s.rng);
        Collections.shuffle(securities, s.rng);
        // ЦВЕТ, ВЫБРАННЫЙ ЗА СТОЛОМ, ИДЁТ ПЕРВЫМ. Дизайнер сажает игрока за
        // конкретный цвет — а с ним и за его колоду приказов (просьба
        // 13.08.2026). Выбранные цвета вынимаются из общей стопки, чтобы
        // остальным местам не достался тот же самый; на что цвет не выбран, тот
        // берёт из оставшихся, как раньше.
        List<String> picked = new ArrayList<>();
        for (int seat = 0; seat < s.players.size(); seat++) {
            String want = seatColorPick(seat);
            if (want != null && byDeck.containsKey(want) && !picked.contains(want)) {
                picked.add(want);
                colors.remove(want);
            } else {
                picked.add(null);
            }
        }

        for (int seat = 0; seat < s.players.size(); seat++) {
            PlayerState p = s.player(seat);
            if (colors.isEmpty()) {
                // Колод меньше, чем мест: цвета начинают повторяться — как и до
                // выбора цвета вручную.
                colors.addAll(byDeck.keySet());
            }
            String color = picked.get(seat) != null ? picked.get(seat)
                : colors.remove(0);
            List<String> hand = new ArrayList<>(byDeck.get(color));   // 4 приказа цвета
            if (seat < securities.size()) {
                hand.add(securities.get(seat));                       // + БЕЗОПАСНОСТЬ
            }
            p.orderHand = hand;
            p.orderColor = color;
            String obj = s.decks.get("objectives").draw(s.rng);
            if (obj != null) {
                p.objectiveHand.add(obj);
                emit(ev("type", "objective_drawn", "seat", p.seat, "card", obj,
                    "hand", p.objectiveHand.size(), "source", "setup"));
            }
        }
    }

    // ---- события раунда --------------------------------------------------
    /**
     * СОДЕРЖАНИЕ КАРТ АРСЕНАЛА в Обновление: карта с пассивкой
     * {@code card_is_energy_source_upkeep} («Полевой генератор») требует 1 монету
     * за раунд. Не заплатил — карта УДАЛЯЕТСЯ ИЗ ИГРЫ (не в сброс: её больше
     * нельзя вытянуть), а её кубик энергии снимается со всех потребителей.
     *
     * <p>Платит движок автоматически, когда монета есть: отказ от оплаты при
     * наличии денег — заведомо проигрышный выбор (карта стоит дороже монеты), и
     * лишний вопрос боту только зашумил бы решения.
     */
    /**
     * ЗНАКОМСТВО С КАРТАМИ (заказ дизайнера 14.08.2026, curriculum-обучение):
     * ключ {@code training.card_flood_rate} (0..1, по умолчанию 0 — в обычной
     * партии ничего не происходит) — вероятность в Обновление бесплатно
     * получить 1 карту арсенала и 1 карту задания, минуя обычную добычу
     * (науку/маркет). Цель НЕ в правиле игры, а в обучении: боты должны часто
     * видеть карты и решать, что с ними делать (ставить/жечь/сбрасывать),
     * прежде чем веса оценки карт начнут значить что-то осмысленное — иначе
     * они почти не встречаются в обучающих партиях и отбор их не проверяет
     * (тот же класс проблемы, что был у 29 весов {@code eval.*}, см.
     * [[forged-bot-search-tuning]]). Ставку убирает {@link SelfPlayTrainer}
     * ПОСТЕПЕННО по ходу обучения — «пусть ищут способ получить карты сами».
     */
    private void floodTrainingCards(PlayerState p) {
        double rate = ((Number) rs().get("training.card_flood_rate", 0.0)).doubleValue();
        if (rate <= 0) {
            return;
        }
        GameState s = state;
        if (s.rng.nextDouble() < rate
                && p.arsenalHand.size() + p.arsenalInstalled.size() < 3) {
            String c = s.decks.get("arsenal").draw(s.rng);
            if (c != null) {
                p.arsenalHand.add(c);
                emit(ev("type", "arsenal_flood", "seat", p.seat, "card", c));
            }
        }
        if (s.rng.nextDouble() < rate
                && p.objectiveHand.size() < rs().getInt("rounds.objective_hand_limit")) {
            String c = s.decks.get("objectives").draw(s.rng);
            if (c != null) {
                p.objectiveHand.add(c);
                emit(ev("type", "objective_flood", "seat", p.seat, "card", c));
            }
        }
    }

    private void payArsenalUpkeep(PlayerState p) {
        GameState s = state;
        for (String cid : new ArrayList<>(p.arsenalInstalled)) {
            Map<String, Object> card;
            try {
                card = Ctx.cards(s, "arsenal").find(cid);
            } catch (RuntimeException e) {
                continue;
            }
            if (card == null || !(card.get("bottom") instanceof Map<?, ?> bm)
                    || !"card_is_energy_source_upkeep".equals(
                        ((Map<String, Object>) bm).get("passive"))) {
                continue;
            }
            if (p.resources.canPay(kelium.core.Resource.COIN, 1)) {
                p.resources.pay(kelium.core.Resource.COIN, 1);
                emit(ev("type", "arsenal_upkeep", "seat", p.seat, "card", cid, "paid", 1));
            } else {
                p.arsenalInstalled.remove(cid);
                p.arsenalCardKelium.remove(cid);
                for (BuildingToken b : p.buildingsOnField()) {
                    b.stripEnergyOf(Actions.ARSENAL_CARD_SOURCE_UID);
                }
                emit(ev("type", "arsenal_upkeep", "seat", p.seat, "card", cid,
                    "paid", 0, "removed", true));
            }
        }
    }

    private void refresh(int rnd) {
        GameState s = state;
        if (rnd == 1) {
            s.marketActive = s.decks.get("market").draw(s.rng);
            moduleSwapAll();
            emit(ev("type", "refresh", "round", rnd, "skipped", true));
            return;
        }
        s.firstPlayer = (s.firstPlayer + 1) % s.numPlayers();
        // КАРТА РЫНКА УХОДИТ ИЗ ИГРЫ НАВСЕГДА (правило дизайнера 15.08.2026).
        // Колода рынка тасуется на подготовке, каждый раунд с неё снимается одна
        // карта — и больше в игру не возвращается. Восемь карт = восемь раундов,
        // и последняя карта означает последний раунд: это одно из условий конца
        // партии, а не отдельная константа.
        //
        // ИСТОРИЯ ОШИБКИ, чтобы её не починили обратно. 14.08.2026 чинили другую
        // неполадку — «карта рынка залипала на несколько раундов» — и починили
        // тем, что стали класть отыгравшую карту в СБРОС. Но Deck.draw() при
        // пустом доборе перетасовывает именно сброс, поэтому карты пошли по
        // второму кругу и начали повторяться за партию. Настоящая причина
        // залипания была не в этом, а в том, что при исчерпанной колоде draw()
        // молча возвращает null и прежняя карта остаётся лежать. Лечится это
        // явной проверкой ниже, а не возвратом карт в игру.
        String newMarket = s.decks.get("market").draw(s.rng);
        if (newMarket == null) {
            // Колода кончилась — партия должна была закончиться этим раундом.
            // Раньше здесь было молчание, и карта просто оставалась прежней.
            emit(ev("type", "market_deck_empty", "round", rnd));
        }
        if (newMarket != null) {
            s.marketActive = newMarket;
            // Новая карта — новые пустые ячейки предложений: кубики келемия с
            // прошлой карты уходят вместе с ней.
            for (int[] side : s.marketCells) {
                java.util.Arrays.fill(side, -1);
            }
        }
        // Келемий на тайлах зарождения НЕ восстанавливается: сколько выкопали —
        // столько и убыло, тайл истощается за партию и потом уходит с поля.
        // (Раньше здесь стояло ежераундовое восстановление — это была ошибка
        // движка, а не правило игры.)
        // Обновление: с КАЖДОГО жетона снимается ОДИН кубик урона (не весь).
        // Урон копится по раундам — штурм ЦУ можно вести несколько раундов.
        // Сколько кубиков урона снимается в Обновление. Правило — ОДИН (СВОД),
        // и это значение по умолчанию. Ключ вынесен в ruleset НЕ ради изменения
        // правила, а чтобы балансовый стенд ({@code kelium.RuleExperiment}) мог
        // проверить, что будет при другом числе: скорость лечения напрямую решает,
        // возможна ли многораундовая осада, а угадывать это по рассуждению нельзя.
        int heal = ((Number) rs().get("combat_model.heal_per_refresh", 1)).intValue();
        for (PlayerState p : s.players) {
            for (int i = 0; i < heal; i++) {
                for (UnitToken t : p.units) {
                    t.healOneDamage();
                }
                for (BuildingToken t : p.buildings) {
                    t.healOneDamage();
                }
            }
            // Супер-арсенал «Келемиевый рудник»: +1 келемий каждое Обновление.
            if (Passives.superArsenalPassive(s, p.seat, "kelium_income")) {
                Storage.addKeliumCapped(state, p, 1);
            }
            // ТОЧКА ПРАВИЛ: доход в Обновление от карт арсенала (например
            // «энергостанции платят монетами за каждый кубик энергии»).
            int income = (int) Math.round(kelium.engine.ability.RuleQuery
                .of(s, p.seat, kelium.engine.ability.Hook.REFRESH_INCOME)
                .base(0).ask());
            if (income > 0) {
                p.resources.add(kelium.core.Resource.COIN, income);
                emit(ev("type", "refresh_income", "seat", p.seat, "coin", income));
            }
            payArsenalUpkeep(p);
            floodTrainingCards(p);
        }
        refillContainers();
        // СТАРЫЙ РЕЖИМ КОНТЕЙНЕРОВ: в Обновление жетоны падают на все гексы без
        // жетонов игроков и без тайлов зарождения (ruleset 1.6.0-c1).
        int laid = TokenContainers.layoutOnRefresh(s);
        if (laid > 0) {
            emit(ev("type", "containers_laid", "round", rnd, "count", laid,
                "on_field", TokenContainers.onField(s)));
        }
        moduleSwapAll();
        emit(ev("type", "refresh", "round", rnd, "first_player", s.firstPlayer));
    }

    /** Провести бесплатную смену модулей для всех игроков, у кого они есть. */
    private void moduleSwapAll() {
        GameState s = state;
        for (int seat = 0; seat < s.numPlayers(); seat++) {
            PlayerState p = s.player(seat);
            if (p.redModules > 0 || p.blueModules > 0) {
                Modules.moduleSwap(s, seat, agents.get(seat), this::emit);
            }
        }
    }

    private void refillContainers() {
        GameState s = state;
        java.util.Set<String> occupied = new java.util.HashSet<>();
        for (PlayerState p : s.players) {
            for (UnitToken t : p.unitsOnField()) {
                occupied.add(t.hexId);
            }
            for (BuildingToken t : p.buildingsOnField()) {
                occupied.add(t.hexId);
            }
        }
        // Контейнеры на поле НЕ выкладываются: они напечатаны на блоках
        // (правило «Контейнеры 2.0», 12.08.2026).
    }

    private void blindDiscard() {
        GameState s = state;
        // Собираем, какую карту каждый игрок отложил под трофеи (для лога).
        Map<Integer, String> setAside = new TreeMap<>();
        for (int seat = 0; seat < s.numPlayers(); seat++) {
            PlayerState p = s.player(seat);
            p.orderHand.addAll(p.orderPlayed);
            p.orderPlayed.clear();
            if (p.orderSetAside != null) {
                p.orderHand.add(p.orderSetAside);
                p.orderSetAside = null;
            }
            if (p.orderHand.size() > 4) {
                // Правило дизайнера (опция rounds.blind_discard_choice):
                // true — игрок САМ выбирает, какого приказа лишиться на раунд;
                // false — случайный сброс (старый вариант, для сравнения).
                if (Boolean.TRUE.equals(rs().get("rounds.blind_discard_choice", Boolean.TRUE))) {
                    List<Choice> opts = new ArrayList<>();
                    for (String cid : p.orderHand) {
                        opts.add(new Choice("blind_discard", cid, cid));
                    }
                    Choice ch = agents.get(seat).choose(s, opts,
                        ev("kind", "blind_discard"));
                    p.orderSetAside = (String) ch.payload();
                    p.orderHand.remove(p.orderSetAside);
                } else {
                    int idx = s.rng.nextInt(p.orderHand.size());
                    p.orderSetAside = p.orderHand.remove(idx);
                }
                setAside.put(seat, p.orderSetAside);
            }
        }
        emit(ev("type", "blind_discard", "set_aside", new TreeMap<>(setAside)));
    }

    private void playCircle(int circle) {
        GameState s = state;
        // I2: вскрытие ОДНОВРЕМЕННОЕ. Сначала все места ВЫБИРАЮТ карту (руки не
        // трогаем — каждый агент видит одинаковый до-вскрытный стейт, поздние места
        // не подглядывают выбор ранних), и только потом карты разом снимаются с рук.
        Map<Integer, String> revealed = new HashMap<>();
        for (int seat : s.seatsInOrder()) {
            PlayerState p = s.player(seat);
            if (p.orderHand.isEmpty()) {
                continue;
            }
            List<Choice> opts = new ArrayList<>();
            for (String cid : p.orderHand) {
                opts.add(new Choice("reveal_order", cid, cid));
            }
            Choice ch = agents.get(seat).choose(s, opts, ev("kind", "reveal_order", "circle", circle));
            revealed.put(seat, (String) ch.payload());
        }
        for (Map.Entry<Integer, String> e : revealed.entrySet()) {
            s.player(e.getKey()).orderHand.remove(e.getValue());
        }
        emit(ev("type", "reveal", "circle", circle, "revealed", new HashMap<>(revealed)));

        Map<Integer, Order> topOrders = new HashMap<>();
        Map<Order, Integer> orderCounts = new HashMap<>();
        for (Map.Entry<Integer, String> e : revealed.entrySet()) {
            Order o = topOrder(e.getValue());
            topOrders.put(e.getKey(), o);
            if (o != null) {
                orderCounts.merge(o, 1, Integer::sum);
            }
        }

        for (int seat : s.seatsInOrder()) {
            if (!revealed.containsKey(seat)) {
                continue;
            }
            resolveTurn(seat, revealed.get(seat), topOrders, orderCounts);
            if (s.finished) {
                return;
            }
        }
        for (Map.Entry<Integer, String> e : revealed.entrySet()) {
            s.player(e.getKey()).orderPlayed.add(e.getValue());
        }
    }

    private void resolveTurn(int seat, String cardId, Map<Integer, Order> topOrders,
                             Map<Order, Integer> orderCounts) {
        GameState s = state;
        PlayerState p = s.player(seat);
        Ruleset rs = rs();
        s.journal.startTurn(seat);
        // ТОЧКА ПРАВИЛ: сколько СПЕЦ-действий за ход. Карта арсенала может дать
        // второе («два СПЕЦ, если не играл Безопасность»).
        int specLimit = (int) Math.round(kelium.engine.ability.RuleQuery
            .of(s, seat, kelium.engine.ability.Hook.ORDER_SPEC_COUNT)
            .base(Math.max(rs.getInt("actions.spec_per_turn"), Passives.specActions(s, seat)))
            .ask());
        TurnContext ctx = new TurnContext(seat, specLimit);

        Map<String, Object> card = orders().byId(cardId);
        boolean isJoker = Boolean.TRUE.equals(card.get("joker"));

        if (isJoker) {
            playActions(p, ctx, Actions.ALL_NAMES, 2, true);
            // БАГ (найден дизайнером 14.08.2026): карта БЕЗОПАСНОСТЬ (джокер) не
            // шлёт turn_orders вовсе — только у неё есть этот код, у обычных
            // приказов событие ниже. Проигрыватель ждёт именно turn_orders, чтобы
            // узнать, что карта дошла до хода и что на ней выбрали: без события
            // GameRecorder.turnFrame так и оставался −1, и карта навечно висела
            // «ждёт своего хода», хотя оба действия уже сыграны. Здесь top_actions
            // — не все восемь напечатанных, а ровно то, что выбрал игрок (порядок
            // важен, поэтому TurnContext.actionsPlayed — LinkedHashSet).
            emit(ev("type", "turn_orders", "seat", seat, "card", cardId,
                "top", "joker", "top_actions", new ArrayList<>(ctx.actionsPlayed),
                "top_allowed", 2, "coincided", false,
                "bottom", null, "bottom_open", false, "bottom_actions", List.of(),
                "maneuver", false));
        } else {
            Order top = Order.fromCode((String) card.get("top"));
            boolean coincided = rs.getBool("actions.coincidence_rule_enabled", true)
                && orderCounts.getOrDefault(top, 0) > 1;
            List<String> names = List.of(Order.ORDER_ACTIONS.get(top));
            int maxA = coincided ? 1 : 2;
            // Признак блокировки нужен способностям карт («Резервный штаб»
            // обходит её за келемий): OptionSource видит состояние партии, но не
            // ход, поэтому флаг живёт в журнале хода.
            s.journal.of(seat).orderBlocked = coincided;

            // Нижняя половина срабатывает, только если ЭТОТ приказ вскрыл сверху
            // кто-то другой. Считаем это ДО хода, чтобы отчёты и проигрыватель
            // могли показать полную раскладку приказа сразу (просьба дизайнера).
            Order bo = card.get("bottom") == null ? null
                : Order.fromCode(card.get("bottom").toString());
            boolean bottomOpen = false;
            if (bo != null) {
                for (Map.Entry<Integer, Order> e : topOrders.entrySet()) {
                    if (e.getValue() == bo && e.getKey() != seat) {
                        bottomOpen = true;
                        break;
                    }
                }
            }
            // n11 «Второй заход»: открытый нижний приказ — факт хода, и он
            // известен ещё до розыгрыша действий.
            s.journal.of(seat).lowerOrderOpen = bottomOpen;
            emit(ev("type", "turn_orders", "seat", seat, "card", cardId,
                "top", top.code, "top_actions", names, "top_allowed", maxA,
                "coincided", coincided,
                "bottom", bo == null ? null : bo.code,
                "bottom_open", bottomOpen,
                "bottom_actions", bo == null ? List.of()
                    : List.of(Order.ORDER_ACTIONS.get(bo)),
                "maneuver", Boolean.TRUE.equals(card.get("maneuver"))));

            playActions(p, ctx, names, maxA, false);

            if (bottomOpen) {
                // ТОЧКА ПРАВИЛ: сколько действий даёт открытый нижний приказ.
                // Карта арсенала «Двойной протокол»/«Параллельный контур» поднимает
                // 1 до 2 — играешь два РАЗНЫХ действия этого приказа вместо одного.
                int bottomA = (int) Math.round(kelium.engine.ability.RuleQuery
                    .of(s, seat, kelium.engine.ability.Hook.ORDER_BOTTOM_ACTIONS)
                    .base(1)
                    .ask());
                playActions(p, ctx, List.of(Order.ORDER_ACTIONS.get(bo)), bottomA, false);
            }
            // Плашка манёвра: на картах maneuver:true — одно бесплатное
            // перемещение одного жетона войска на его скорость (не открывает
            // Операцию, не тратит боеприпасы).
            if (Boolean.TRUE.equals(card.get("maneuver"))) {
                offerManeuver(p);
            }
        }
        emit(ev("type", "turn_end", "seat", seat, "resources", resourcesMap(p)));
    }

    private void playActions(PlayerState p, TurnContext ctx, List<String> actionNames,
                             int maxActions, boolean distinct) {
        GameState s = state;
        // Что ещё можно сыграть в этом ходу — знание хода, а не приказа: его
        // читают индикаторы заданий, когда строят план на этот ход.
        ctx.orderActions.addAll(actionNames);
        ctx.allowedActions = Math.max(ctx.allowedActions, maxActions);
        // СУПЕР ЗАДАНИЯ 2.0: подсунуть карты под планшет ради символов — не
        // действие и не СПЕЦ, поэтому предлагается один раз перед ходом.
        offerTuck(p);
        int played = 0;
        // Лишние действия, выданные способностями карт уже ПО ХОДУ дела (обход
        // блокировки приказа за келемий). Предел нельзя посчитать заранее:
        // разрешение выдаётся СПЕЦ-действием между основными действиями.
        int extra = 0;
        while (played < maxActions + extra && !s.finished) {
            List<String> candidates = new ArrayList<>();
            for (String nname : actionNames) {
                if (!ctx.actionsPlayed.contains(nname)) {
                    candidates.add(nname);
                }
            }
            if (candidates.isEmpty()) {
                break;
            }
            List<Choice> opts = new ArrayList<>();
            for (String nname : candidates) {
                opts.add(new Choice("action", nname, nname));
            }
            opts.add(new Choice("pass", null, "ничего не делать"));
            Choice ch = agents.get(p.seat).choose(s, opts,
                ev("kind", "action", "remaining", maxActions - played));
            if (ch.payload() == null) {
                break;
            }
            String actionName = (String) ch.payload();
            Action action = Actions.create(actionName, s);
            ActionResult res = action.perform(p, ctx, agents.get(p.seat));
            if (res.ok()) {
                s.journal.onAction(p.seat, actionName, res.telemetry());
            }
            emit(ev("type", "action", "seat", p.seat, "action", actionName,
                "ok", res.ok(), "detail", res.detail(), "telemetry", res.telemetry()));
            played += 1;
            // Правило 2026-08-10: открытие контейнера = СПЕЦ-действие; при
            // выключенном правиле — по-старому свободное после каждого действия.
            if (!containersOpenIsSpec()) {
                offerOpenContainer(p);
            }
            offerSpec(p, ctx);
            extra += s.journal.of(p.seat).takeBlockBypassGrants();
        }
        // B6: СПЕЦ — независимый ресурс хода. Пас по основному действию (или
        // пустой список кандидатов) НЕ отбирает СПЕЦ: игрок всё ещё может
        // завершить задание, установить арсенал, внести вклад в супер-задание
        // или развернуть готовое супер-задание ради мгновенной победы.
        if (!s.finished && ctx.canSpec()) {
            if (!containersOpenIsSpec()) {
                offerOpenContainer(p);
            }
            offerSpec(p, ctx);
        }
    }

    /**
     * Плашка манёвра: бесплатно передвинуть ОДИН жетон войска на его скорость
     * (несколько шагов одним и тем же жетоном), не открывая Операцию и не тратя
     * боеприпасы. Ход строится по правилам проходимости движения.
     */
    private void offerManeuver(PlayerState p) {
        GameState s = state;
        var side = p.board.troop;
        Integer airOverride = Passives.aircraftSpeedOverride(s, p.seat);

        // Выбор жетона для манёвра (или отказ).
        List<Choice> pickOpts = new ArrayList<>();
        for (UnitToken u : p.unitsOnField()) {
            int speed = Speed.of(s, p.seat, u.type);   // единая точка скорости
            if (speed <= 0) {
                continue;
            }
            // есть ли куда шагнуть
            boolean canMove = false;
            for (String nb : s.field.neighbors(u.hexId)) {
                if (Actions.MovementAction.canEnterHex(s, u, nb, p.seat)) {
                    canMove = true;
                    break;
                }
            }
            if (canMove) {
                pickOpts.add(new Choice("maneuver_unit", u.uid, u.type.code + "@" + u.hexId));
            }
        }
        if (pickOpts.isEmpty()) {
            return;
        }
        pickOpts.add(new Choice("pass", null, "без манёвра"));
        Choice pick = agents.get(p.seat).choose(s, pickOpts, ev("kind", "maneuver_unit"));
        if (pick.payload() == null) {
            return;
        }
        int uid = ((Number) pick.payload()).intValue();
        UnitToken unit = null;
        for (UnitToken u : p.units) {
            if (u.uid == uid) {
                unit = u;
                break;
            }
        }
        if (unit == null) {
            return;
        }
        int speed = (airOverride != null && unit.type == UnitType.AIRCRAFT)
            ? airOverride : Speed.of(s, p.seat, unit.type);

        // Шаги одним жетоном на его скорость.
        for (int step = 0; step < speed; step++) {
            List<Choice> moveOpts = new ArrayList<>();
            for (String nb : s.field.neighbors(unit.hexId)) {
                if (Actions.MovementAction.canEnterHex(s, unit, nb, p.seat)) {
                    moveOpts.add(new Choice("move", Map.of("uid", uid, "to", nb),
                        unit.type.code + "->" + nb));
                }
            }
            if (moveOpts.isEmpty()) {
                break;
            }
            moveOpts.add(new Choice("pass", null, "остановиться"));
            Choice mv = agents.get(p.seat).choose(s, moveOpts, ev("kind", "move", "maneuver", true));
            if (mv.payload() == null) {
                break;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> mp = (Map<String, Object>) mv.payload();
            String dest = (String) mp.get("to");
            String fromHex = unit.hexId;
            boolean wasInside = unit.inside();   // снять ДО хода: setHexId выведет
            unit.setHexId(dest);   // выводит войско из здания
            // ПЕЧАТНЫЙ КОНТЕЙНЕР: войско, вошедшее в гекс, встаёт на ячейку с
            // напечатанным контейнером, если она свободна, и берёт карту.
            PrintedContainers.onUnitMoved(s, p, fromHex, dest, unit.type, wasInside);
            TurnJournal.TurnFacts f = s.journal.of(p.seat);
            f.movedUids.add(uid);
            f.unitsMoved = f.movedUids.size();
            f.movedFromHexes.add(fromHex);
        }
        emit(ev("type", "maneuver", "seat", p.seat, "unit", uid, "to", unit.hexId));
    }

    /** Открытие контейнера — БЕСПЛАТНОЕ действие: взять, выбрать вариант, применить. */
    @SuppressWarnings("unchecked")
    private void offerOpenContainer(PlayerState p) {
        if (p.containers <= 0) {
            return;
        }
        GameState s = state;
        String cid = s.decks.get("containers").draw(s.rng);
        if (cid == null) {
            return;   // I3: пустая колода — жетон НЕ сгорает
        }
        Map<String, Object> card = Ctx.cards(s, "containers").byId(cid);
        Map<String, Object> a = (Map<String, Object>) card.get("a");
        Map<String, Object> b = (Map<String, Object>) card.get("b");
        // I3: решение «открывать ли» уже принято (СПЕЦ/выбор в mass_open);
        // увидев карту, игрок ОБЯЗАН выбрать вариант — бесплатного подглядывания
        // с отказом (и утечки карты в сброс) больше нет.
        // ВЫБОР ЕСТЬ НЕ У КАЖДОГО КОНТЕЙНЕРА (правило дизайнера 17.08.2026):
        // ровно половина колоды несёт две стороны, вторая половина — один
        // напечатанный эффект. Карта без стороны b применяется сразу, без
        // предложения выбора: спрашивать «выбери из одного» бессмысленно и за
        // столом, и в движке.
        Object[] payload;
        if (b == null) {
            payload = new Object[]{"a", a};
        } else {
            List<Choice> opts = new ArrayList<>();
            opts.add(new Choice("container_variant", new Object[]{"a", a},
                card.getOrDefault("name", "") + ":" + a.getOrDefault("label", "")));
            opts.add(new Choice("container_variant", new Object[]{"b", b},
                card.getOrDefault("name", "") + ":" + b.getOrDefault("label", "")));
            Choice ch = agents.get(p.seat).choose(s, opts,
                ev("kind", "open_container", "card", cid));
            payload = (Object[]) ch.payload();
        }
        Map<String, Object> variant = (Map<String, Object>) payload[1];
        p.containers -= 1;
        Map<String, Object> got;
        try {
            got = Effects.apply((String) variant.get("effect"), s, p.seat,
                (Map<String, Object>) variant.getOrDefault("params", Map.of()));
        } catch (Effects.EffectError e) {
            got = new HashMap<>();
        }
        s.decks.get("containers").discard(cid);
        s.journal.of(p.seat).containersOpened += 1;
        emit(ev("type", "container", "seat", p.seat, "card", cid, "variant", payload[0],
            "effect", variant.getOrDefault("effect", ""),
            "label", variant.getOrDefault("label", ""), "got", got));
    }

    /** Индикаторы заданий в виде, пригодном для журнала и проигрывателя. */
    private static List<Map<String, Object>> hintsForLog(List<ObjectiveHints.Hint> hints) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ObjectiveHints.Hint h : hints) {
            Map<String, Object> m = new HashMap<>();
            m.put("card", h.cardId());
            m.put("ready", h.ready());
            m.put("enhanced_ready", h.enhancedReady());
            m.put("reachable", h.reachable());
            m.put("value", h.value());
            m.put("max_value", h.maxValue());
            m.put("needed", h.needed());
            List<String> plans = new ArrayList<>();
            for (ObjectiveHints.Plan pl : h.plans()) {
                plans.add(pl.summary());
            }
            m.put("plans", plans);
            out.add(m);
        }
        return out;
    }

    /**
     * SPEC-действие: завершить выполненное задание, сжечь/установить карту
     * арсенала, внести часть в супер-задание или развернуть готовое супер-задание
     * (мгновенная победа). Порт из forge/engine/engine._offer_spec.
     */
    private void offerSpec(PlayerState p, TurnContext ctx) {
        // ЭФФЕКТ «разыграй любое число СПЕЦ-действий до конца хода» снимает
        // лимит на остаток хода. Флаг живёт в журнале: эффект карты не видит
        // контекст хода, а «до конца хода» — ровно срок жизни журнала.
        ctx.specUnlimited = state.journal.of(p.seat).unlimitedSpec;
        if (!ctx.canSpec()) {
            return;
        }
        GameState s = state;
        TurnJournal j = s.journal;
        List<Choice> opts = new ArrayList<>();
        for (String cid : Objectives.playableObjectives(s, p.seat, j)) {
            opts.add(new Choice("spec_objective", cid, "complete " + cid));
        }
        // Верхний утилизационный эффект: любую карту задания в руке можно СЖЕЧЬ
        // ради мгновенного верхнего эффекта (вместо выполнения низа). Доступно
        // для карт, у которых в данных задан top.
        for (String cid : new ArrayList<>(p.objectiveHand)) {
            Map<String, Object> oc = Ctx.cards(s, "objectives").find(cid);
            if (oc != null && oc.get("top") instanceof Map<?, ?>) {
                opts.add(new Choice("spec_objective_burn", cid, "burn top " + cid));
            }
        }
        for (String cid : new ArrayList<>(p.arsenalHand)) {
            opts.add(new Choice("spec_arsenal_burn", cid, "burn " + cid));
            opts.add(new Choice("spec_arsenal_install", cid, "install " + cid));
        }
        if (p.superObjective != null && !p.superObjectiveComplete && canContributeSuper(p)) {
            opts.add(new Choice("spec_super", p.superObjective, "assemble " + p.superObjective));
        }
        if (p.superObjective != null && p.superObjectiveComplete && superDeployReady(p)) {
            opts.add(new Choice("spec_super_deploy", p.superObjective, "DEPLOY " + p.superObjective));
        }
        // СУПЕР ЗАДАНИЯ 2.0: проверка рисунка и символов — своё СПЕЦ-действие.
        if (p.superObjective != null && p.superObjectiveComplete && superCheckReady(p)) {
            opts.add(new Choice("spec_super_check", p.superObjective,
                "ПРОВЕРКА супер задания " + p.superObjective));
        }
        // Вскрыть ОДНУ карту под планшетом — СПЕЦ-действие. Правило «открой всех
        // одним СПЕЦ» отменено дизайнером 12.08.2026 (иконка спец-действия теперь
        // на рубашке каждой карты).
        if (revealIsSpec(p)) {
            for (PlayerState.TuckedCard t : p.tucked) {
                if (!t.revealed) {
                    opts.add(new Choice("spec_symbol_reveal", t.cardId,
                        "вскрыть символ (" + t.cardId + ")"));
                }
            }
        }
        // Вскрытие контейнеров/арсенала: разом — только если правила это разрешают
        // (containers_storage.mass_open; в 1.6.0 выключено).
        if (containersOpenIsSpec() && (p.containers > 0 || !p.arsenalHand.isEmpty())) {
            boolean mass = Boolean.TRUE.equals(Ctx.rules(s)
                .get("containers_storage.mass_open", Boolean.TRUE));
            opts.add(new Choice("spec_container", "open",
                (mass ? "mass open (" : "open one (") + p.containers + " cont, "
                    + p.arsenalHand.size() + " ars)"));
        }
        // E2: SPEC-пассивки УСТАНОВЛЕННЫХ карт арсенала — реальные опции СПЕЦ.
        for (String cid : p.arsenalInstalled) {
            String passive = installedSpecPassive(cid);
            if (passive != null) {
                opts.add(new Choice("spec_arsenal_use", cid, "SPEC " + passive + " (" + cid + ")"));
            }
        }
        // СПОСОБНОСТИ АРСЕНАЛА сами кладут свои варианты в меню СПЕЦ: движок не
        // знает про карты, он спрашивает «что добавить?». Так карта даёт НОВОЕ
        // спец-действие без правки движка (13.08.2026).
        opts.addAll(kelium.engine.ability.Abilities.options(
            s, p.seat, kelium.engine.ability.OptionSource.Slot.SPEC));
        if (opts.isEmpty()) {
            return;
        }
        opts.add(new Choice("pass", null, "без спец-действия"));
        // ИНДИКАТОРЫ ЗАДАНИЙ (заказ дизайнера 17.08.2026). Движок сам считает по
        // каждой карте руки: горит ли «ГОТОВО», горит ли «ДОСТИЖИМО В ЭТОТ ХОД»,
        // и если достижимо — какими действиями. Кладём в контекст выбора, чтобы
        // агент решал СПЕЦ-действие, видя пути к наградам, а не одни награды.
        List<ObjectiveHints.Hint> hints = ObjectiveHints.forHand(s, p.seat, j,
            ctx.remainingActionNames(), ctx.remainingActions());
        Map<String, Object> specCtx = ev("kind", "spec");
        specCtx.put("objective_hints", hints);
        emit(ev("type", "objective_hints", "seat", p.seat, "round", s.round,
            "hints", hintsForLog(hints)));
        Choice ch = agents.get(p.seat).choose(s, opts, specCtx);
        if (ch.payload() == null) {
            return;
        }
        switch (ch.kind()) {
            case "spec_objective" -> Objectives.playObjective(s, p.seat, j, (String) ch.payload(), this::emit);
            case "spec_objective_burn" -> objectiveBurnTop(p, (String) ch.payload());
            case "spec_arsenal_burn" -> arsenalBurn(p, (String) ch.payload());
            case "spec_arsenal_install" -> arsenalInstall(p, (String) ch.payload());
            case "spec_super" -> contributeSuper(p);
            case "spec_super_deploy" -> deploySuper(p);
            case "spec_super_check" -> checkSuper(p);
            case "spec_symbol_reveal" -> revealSymbol(p, (String) ch.payload());
            case "spec_container" -> massOpen(p);
            case "spec_arsenal_use" -> useInstalledSpec(p, (String) ch.payload());
            default -> {
                // вариант от способности: исполняет сама карта
                if (kelium.engine.ability.Abilities.isAbilityChoice(ch)) {
                    boolean did = kelium.engine.ability.Abilities.perform(
                        s, p.seat, ch, agents.get(p.seat));
                    emit(ev("type", "ability_spec", "seat", p.seat,
                        "ability", ch.payload(), "did", did));
                }
            }
        }
        ctx.useSpec();
    }

    /**
     * E2: SPEC-пассивка установленной карты арсенала (id пассивки), если она
     * реализована как разовое СПЕЦ-применение; null — карта без такой пассивки.
     */
    @SuppressWarnings("unchecked")
    private String installedSpecPassive(String cid) {
        Map<String, Object> card = Ctx.cards(state, "arsenal").find(cid);
        if (card == null || !(card.get("bottom") instanceof Map<?, ?> bm)) {
            return null;
        }
        Map<String, Object> bottom = (Map<String, Object>) bm;
        if (!"SPEC".equals(bottom.get("kind"))) {
            return null;
        }
        String passive = (String) bottom.get("passive");
        return switch (passive == null ? "" : passive) {
            // miner_takes_container и grab_adjacent_container убраны 13.08.2026
            // вместе с картами as6/a08/a19 (противоречат КОНТЕЙНЕРАМ 2.0).
            case "move_one_unit_1", "heal_one_damage",
                 "deploy_1_unit", "move_one_module" -> passive;
            default -> null;
        };
    }

    /** E2: разовое применение SPEC-пассивки установленной карты. */
    private void useInstalledSpec(PlayerState p, String cid) {
        GameState s = state;
        String passive = installedSpecPassive(cid);
        if (passive == null) {
            return;
        }
        Map<String, Object> got;
        try {
            got = switch (passive) {
                case "move_one_unit_1" ->
                    Effects.apply("move_unit", s, p.seat, Map.of("hexes", 1));
                case "heal_one_damage" ->
                    Effects.apply("heal_one", s, p.seat, Map.of("amount", 1));
                case "deploy_1_unit" ->
                    Effects.apply("deploy_units", s, p.seat, Map.of("count", 1));
                case "move_one_module" -> {
                    Modules.moveOneModule(s, p.seat, agents.get(p.seat));
                    yield Map.of("module_move", 1);
                }
                default -> Map.of();
            };
        } catch (Effects.EffectError e) {
            got = Map.of("error", e.getMessage());
        }
        emit(ev("type", "arsenal_spec_use", "seat", p.seat, "card", cid,
            "passive", passive, "got", got));
    }

    /**
     * Массовое вскрытие (одно СПЕЦ): игрок открывает СКОЛЬКО УГОДНО контейнеров
     * и/или разыгрывает СКОЛЬКО УГОДНО закрытых карт арсенала (сжечь/установить),
     * пока не остановится. Две половинки модуля одного цвета складываются в
     * целый жетон автоматически (внутри эффектов контейнеров).
     */
    private void massOpen(PlayerState p) {
        GameState s = state;
        for (int guard = 0; guard < 24; guard++) {
            List<Choice> opts = new ArrayList<>();
            if (p.containers > 0) {
                opts.add(new Choice("mass_container", "open",
                    "open container (" + p.containers + " left)"));
            }
            for (String cid : new ArrayList<>(p.arsenalHand)) {
                opts.add(new Choice("spec_arsenal_burn", cid, "burn " + cid));
                opts.add(new Choice("spec_arsenal_install", cid, "install " + cid));
            }
            if (opts.isEmpty()) {
                return;
            }
            opts.add(new Choice("pass", null, "stop opening"));
            Choice ch = agents.get(p.seat).choose(s, opts, ev("kind", "mass_open"));
            if (ch.payload() == null) {
                return;
            }
            switch (ch.kind()) {
                case "mass_container" -> offerOpenContainer(p);
                case "spec_arsenal_burn" -> arsenalBurn(p, (String) ch.payload());
                case "spec_arsenal_install" -> arsenalInstall(p, (String) ch.payload());
                default -> { }
            }
        }
    }

    /** Включено ли правило «открытие контейнера = СПЕЦ» (containers_storage). */
    private boolean containersOpenIsSpec() {
        return Boolean.TRUE.equals(Ctx.rules(state)
            .get("containers_storage.open_is_spec", Boolean.FALSE));
    }

    /**
     * СУПЕР ЗАДАНИЯ 2.0 (12.08.2026): вторая часть выполнена, если на поле
     * сложился РИСУНОК из конкретных объектов (непрерывная связка по ячейкам,
     * {@link DeployPattern}) И под планшетом открыт НАБОР СИМВОЛОВ
     * ({@link Symbols}). Проверка — своё СПЕЦ-действие с карты; по правилам оно
     * же вскрывает последний символ, поэтому одному символу разрешено быть ещё
     * закрытым (ruleset {@code super_objectives.check_reveals_last_symbol}).
     */
    private boolean superCheckReady(PlayerState p) {
        GameState s = state;
        Map<String, Object> card = Ctx.cards(s, "super_objectives").find(p.superObjective);
        if (card == null || !(card.get("deploy") instanceof Map<?, ?> deploy)) {
            return false;                  // карта старого формата 1.0.0
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> dep = (Map<String, Object>) deploy;
        if (!DeployPattern.satisfied(s, p.seat, dep)) {
            return false;
        }
        if (!Boolean.TRUE.equals(Ctx.rules(s).get("super_objectives.require_symbols", Boolean.TRUE))) {
            return true;
        }
        int slack = Boolean.TRUE.equals(Ctx.rules(s)
            .get("super_objectives.check_reveals_last_symbol", Boolean.FALSE)) ? 1 : 0;
        return Symbols.satisfied(s, p, Symbols.required(s, p.superObjective), slack);
    }

    /** Проверка супер задания: вскрывает всё нужное под планшетом и побеждает. */
    private void checkSuper(PlayerState p) {
        GameState s = state;
        // Проверка ОТКРЫВАЕТ карты: игрок обязан показать символы, а не просто
        // заявить их. Открываем всё, что лежит закрытым и подходит по форме.
        for (String need : Symbols.required(s, p.superObjective)) {
            Symbols.Marking m = Symbols.of(s);
            for (PlayerState.TuckedCard t : p.tucked) {
                if (t.revealed) {
                    continue;
                }
                String form = "container".equals(t.kind) ? m.ofContainer(t.cardId)
                    : m.ofArsenal(t.cardId);
                if (need.equals(form)) {
                    t.revealed = true;
                    emit(ev("type", "symbol_reveal", "seat", p.seat, "card", t.cardId,
                        "symbol", form, "by", "check"));
                    break;
                }
            }
        }
        emit(ev("type", "super_check", "seat", p.seat, "card", p.superObjective,
            "symbols", Symbols.revealed(s, p)));
        deploySuper(p);
    }

    /**
     * ВЫБОР СУПЕР ЗАДАНИЯ на подготовке (правило 2.0, 12.08.2026): игроку
     * раздали несколько карт, он оставляет одну, остальные уходят в коробку.
     * Спрашивается один раз, до первого раунда, у всех по порядку мест.
     */
    private void offerSuperPick() {
        GameState s = state;
        for (PlayerState p : s.players) {
            if (p.superObjective != null || p.superObjectiveOffer.size() < 2) {
                continue;
            }
            List<Choice> opts = new ArrayList<>();
            for (String cid : p.superObjectiveOffer) {
                Map<String, Object> card = Ctx.cards(s, "super_objectives").find(cid);
                String label = card == null ? cid : String.valueOf(card.get("name"));
                opts.add(new Choice("super_pick", cid, label + " (" + cid + ")"));
            }
            Choice ch = agents.get(p.seat).choose(s, opts,
                ev("kind", "super_pick", "seat", p.seat));
            String chosen = ch.payload() instanceof String cid ? cid
                : p.superObjectiveOffer.get(0);
            p.superObjective = chosen;
            emit(ev("type", "super_pick", "seat", p.seat, "card", chosen,
                "offered", new ArrayList<>(p.superObjectiveOffer)));
        }
    }

    /**
     * ВЫБОР НАЧАЛЬНОГО ЗАДАНИЯ на подготовке (режим {@code starters},
     * 12.08.2026): устроен ровно как выбор супер задания — игроку раздали две
     * карты, одну он берёт в руку, вторая сбрасывается.
     */
    private void offerStartObjectivePick() {
        GameState s = state;
        for (PlayerState p : s.players) {
            if (p.startObjectiveOffer.size() < 2) {
                continue;
            }
            List<Choice> opts = new ArrayList<>();
            for (String cid : p.startObjectiveOffer) {
                Map<String, Object> card = Ctx.cards(s, "objectives").find(cid);
                String label = card == null ? cid : String.valueOf(card.get("name"));
                opts.add(new Choice("start_objective_pick", cid, label + " (" + cid + ")"));
            }
            Choice ch = agents.get(p.seat).choose(s, opts,
                ev("kind", "start_objective_pick", "seat", p.seat));
            String chosen = ch.payload() instanceof String cid ? cid
                : p.startObjectiveOffer.get(0);
            p.objectiveHand.add(chosen);
            for (String cid : p.startObjectiveOffer) {
                if (!cid.equals(chosen)) {
                    s.decks.get("objectives").discard(cid);
                }
            }
            emit(ev("type", "start_objective_pick", "seat", p.seat, "card", chosen,
                "offered", new ArrayList<>(p.startObjectiveOffer)));
        }
    }

    /** Включено ли правило «вскрытие подложенной карты = СПЕЦ-действие». */
    private boolean revealIsSpec(PlayerState p) {
        return !p.tucked.isEmpty() && Boolean.TRUE.equals(Ctx.rules(state)
            .get("symbols.reveal_is_spec", Boolean.FALSE));
    }

    /** Вскрыть одну подложенную карту — её символ становится открытым. */
    private void revealSymbol(PlayerState p, String cardId) {
        for (PlayerState.TuckedCard t : p.tucked) {
            if (!t.revealed && t.cardId.equals(cardId)) {
                t.revealed = true;
                Symbols.Marking m = Symbols.of(state);
                String form = "container".equals(t.kind) ? m.ofContainer(t.cardId)
                    : m.ofArsenal(t.cardId);
                emit(ev("type", "symbol_reveal", "seat", p.seat, "card", cardId,
                    "symbol", form, "by", "spec"));
                return;
            }
        }
    }

    /** Подложить карту под планшет ради символа — свободное решение, не действие. */
    private void offerTuck(PlayerState p) {
        GameState s = state;
        if (!Boolean.TRUE.equals(Ctx.rules(s).get("symbols.tuck_is_free", Boolean.FALSE))
                || p.superObjective == null) {
            return;
        }
        Symbols.Marking m = Symbols.of(s);
        while (true) {
            List<Choice> opts = new ArrayList<>();
            if (p.containers > 0) {
                // Конкретную карту контейнера игрок не выбирает: контейнеры лежат
                // рубашкой вверх, символ на рубашке не виден. Берём верхний из
                // колоды — это и есть «подсунуть не глядя».
                opts.add(new Choice("tuck_container", "container", "подсунуть контейнер под планшет"));
            }
            for (String cid : new ArrayList<>(p.arsenalHand)) {
                if (m.ofArsenal(cid) != null) {
                    opts.add(new Choice("tuck_arsenal", cid, "подсунуть арсенал " + cid));
                }
            }
            if (opts.isEmpty()) {
                return;
            }
            opts.add(new Choice("pass", null, "ничего не подсовывать"));
            Choice ch = agents.get(p.seat).choose(s, opts, ev("kind", "tuck"));
            if (ch.payload() == null) {
                return;
            }
            if ("tuck_container".equals(ch.kind())) {
                String cid = s.decks.get("containers").draw(s.rng);
                if (cid == null) {
                    return;
                }
                p.containers -= 1;
                p.tucked.add(new PlayerState.TuckedCard("container", cid));
                emit(ev("type", "tuck", "seat", p.seat, "kind", "container", "card", cid));
            } else {
                String cid = (String) ch.payload();
                p.arsenalHand.remove(cid);
                p.tucked.add(new PlayerState.TuckedCard("arsenal", cid));
                emit(ev("type", "tuck", "seat", p.seat, "kind", "arsenal", "card", cid));
            }
        }
    }

    private boolean superDeployReady(PlayerState p) {
        GameState s = state;
        Map<String, Object> card = Ctx.cards(s, "super_objectives").byId(p.superObjective);
        Object wpObj = card.get("win_pattern");
        if (!(wpObj instanceof Map<?, ?> wp)) {
            return false;
        }
        Object pid = ((Map<String, Object>) wp).get("id");
        if (pid == null || !Predicates.isRegistered(pid.toString())) {
            return false;
        }
        try {
            return Predicates.check(pid.toString(), s, p.seat, s.journal, Map.of());
        } catch (Predicates.PredicateError e) {
            return false;
        }
    }

    private void deploySuper(PlayerState p) {
        GameState s = state;
        s.finished = true;
        s.winner = p.seat;
        s.winCondition = "super_objective";
        emit(ev("type", "super_deploy", "seat", p.seat, "card", p.superObjective, "win", true));
    }

    @SuppressWarnings("unchecked")
    private void arsenalBurn(PlayerState p, String cid) {
        GameState s = state;
        Map<String, Object> card = Ctx.cards(s, "arsenal").byId(cid);
        Map<String, Object> top = card.get("top") instanceof Map<?, ?> t
            ? (Map<String, Object>) t : Map.of();
        Map<String, Object> got;
        try {
            got = Effects.apply((String) top.getOrDefault("effect", "noop"), s, p.seat,
                (Map<String, Object>) top.getOrDefault("params", Map.of()));
        } catch (Effects.EffectError e) {
            got = new HashMap<>();
        }
        p.arsenalHand.remove(cid);
        s.decks.get("arsenal").discard(cid);
        // ЭФФЕКТ И ЯРЛЫК В СОБЫТИИ (заказ дизайнера 15.08.2026): без них в отчётах
        // видно только «сожгли карту», а какой утиль-эффект сработал и что он дал —
        // нет. Задания это сообщали, арсенал молчал, и метрики утиля были неполными.
        emit(ev("type", "arsenal", "seat", p.seat, "card", cid, "mode", "burn",
            "effect", top.getOrDefault("effect", ""),
            "label", top.getOrDefault("label", ""), "got", got));
    }

    /**
     * Сжечь ВЕРХ карты задания — одноразовый утилизационный эффект вместо
     * выполнения низа. Карта уходит в сброс. Эффект берётся из данных top
     * (реестр {@link Effects}).
     */
    @SuppressWarnings("unchecked")
    private void objectiveBurnTop(PlayerState p, String cid) {
        GameState s = state;
        Map<String, Object> card = Ctx.cards(s, "objectives").byId(cid);
        Map<String, Object> top = card.get("top") instanceof Map<?, ?> t
            ? (Map<String, Object>) t : Map.of();
        Map<String, Object> got;
        try {
            got = Effects.apply((String) top.getOrDefault("effect", "noop"), s, p.seat,
                (Map<String, Object>) top.getOrDefault("params", Map.of()));
        } catch (Effects.EffectError e) {
            got = new HashMap<>();
        }
        p.objectiveHand.remove(cid);
        s.decks.get("objectives").discard(cid);
        emit(ev("type", "objective_burn", "seat", p.seat, "card", cid, "round", s.round,
            "effect", top.getOrDefault("effect", ""),
            "label", top.getOrDefault("label", ""), "got", got));
    }

    private void arsenalInstall(PlayerState p, String cid) {
        GameState s = state;
        p.arsenalHand.remove(cid);
        if (p.arsenalInstalled.size() >= 3) {
            String dropped = p.arsenalInstalled.remove(0);
            applyHpPassive(p, dropped, -1);   // B7: снять бонус вытесненной карты
            s.decks.get("arsenal").discard(dropped);
        }
        p.arsenalInstalled.add(cid);
        applyHpPassive(p, cid, +1);           // B7: вшить бонус HP в жетоны
        emit(ev("type", "arsenal", "seat", p.seat, "card", cid, "mode", "install"));
    }

    /**
     * B7: пассивки «+HP» меняют САМО поле hp жетонов при установке/снятии карты,
     * чтобы живость была единой во всех проверках (никаких «фантомов», которые
     * alive()==false, но не уничтожены).
     */
    @SuppressWarnings("unchecked")
    private void applyHpPassive(PlayerState p, String cid, int sign) {
        Map<String, Object> card = Ctx.cards(state, "arsenal").find(cid);
        if (card == null || !(card.get("bottom") instanceof Map<?, ?> bm)) {
            return;
        }
        String passive = String.valueOf(((Map<String, Object>) bm).get("passive"));
        if ("buildings_plus1_hp".equals(passive)) {
            for (BuildingToken b : p.buildings) {
                b.hp += sign;
                b.damage = Math.min(b.damage, Math.max(0, b.hp - 1));
            }
        } else if ("cu_plus2_hp".equals(passive)) {
            for (BuildingToken b : p.buildings) {
                if (b.type == kelium.core.BuildingType.COMMAND_CENTER) {
                    b.hp += 2 * sign;
                    b.damage = Math.min(b.damage, Math.max(0, b.hp - 1));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean canContributeSuper(PlayerState p) {
        GameState s = state;
        Map<String, Object> card = Ctx.cards(s, "super_objectives").byId(p.superObjective);
        Map<String, Object> assembly = card.get("assembly") instanceof Map<?, ?> a
            ? (Map<String, Object>) a : Map.of();
        List<Object> parts = assembly.get("parts") instanceof List<?> l ? (List<Object>) l : List.of();
        // B5: доступна ли хоть одна часть С НЕДОБОРОМ по своей квоте.
        for (Object po : parts) {
            Map<String, Object> part = (Map<String, Object>) po;
            String kind = (String) part.get("kind");
            int amount = ((Number) part.get("amount")).intValue();
            if (p.superPartProgress.getOrDefault(kind, 0) < amount
                    && superPartAvailable(p, kind)) {
                return true;
            }
        }
        return false;
    }

    private boolean superPartAvailable(PlayerState p, String kind) {
        GameState s = state;
        if (kind.equals("kelium") || kind.equals("coin") || kind.equals("ammo") || kind.equals("debris")) {
            return p.resources.get(kelium.core.Resource.fromCode(kind)) >= 1;
        }
        if (kind.equals("enemy_unit_token") || kind.equals("enemy_building_token")) {
            boolean wantUnit = kind.equals("enemy_unit_token");
            for (Token t : p.trophySpace) {
                if ((t instanceof UnitToken) == wantUnit) {
                    return true;
                }
            }
            return false;
        }
        if (kind.equals("own_miner_bordering_grid")) {
            try {
                return Predicates.check("powered_miners_bordering_grids", s, p.seat,
                    s.journal, Map.of("count", 1));
            } catch (Predicates.PredicateError e) {
                return false;
            }
        }
        if (kind.equals("own_building_adjacent_enemy")) {
            try {
                return Predicates.check("building_adjacent_to_enemy", s, p.seat,
                    s.journal, Map.of());
            } catch (Predicates.PredicateError e) {
                return false;
            }
        }
        if (kind.equals("own_unit_on_enemy_hex")) {
            for (UnitToken u : p.unitsOnField()) {
                for (PlayerState pl : s.players) {
                    if (pl.seat == p.seat) {
                        continue;
                    }
                    for (Token t : allOnField(pl)) {
                        if (u.hexId.equals(t.hexId())) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
        return false;
    }

    /**
     * B5+K6: вклад в супер-задание. Прогресс ведётся ПО ЧАСТЯМ (kind -> внесено,
     * не больше amount каждой), ЧАСТЬ ВЫБИРАЕТ ИГРОК; позиционные части
     * (свой добытчик у грядки и т.п.) требуют истинного предиката и не могут
     * дать «бесплатный» прогресс сверх своей квоты.
     */
    @SuppressWarnings("unchecked")
    private void contributeSuper(PlayerState p) {
        GameState s = state;
        Map<String, Object> card = Ctx.cards(s, "super_objectives").find(p.superObjective);
        if (card == null) {
            return;
        }
        Map<String, Object> assembly = card.get("assembly") instanceof Map<?, ?> a
            ? (Map<String, Object>) a : Map.of();
        List<Object> parts = assembly.get("parts") instanceof List<?> l ? (List<Object>) l : List.of();

        // доступные для вклада части (есть недобор + выполнима оплата/условие)
        List<Choice> opts = new ArrayList<>();
        for (Object po : parts) {
            Map<String, Object> part = (Map<String, Object>) po;
            String kind = (String) part.get("kind");
            int amount = ((Number) part.get("amount")).intValue();
            int done = p.superPartProgress.getOrDefault(kind, 0);
            if (done >= amount || !superPartAvailable(p, kind)) {
                continue;
            }
            opts.add(new Choice("super_part", kind, kind + " " + done + "/" + amount));
        }
        if (opts.isEmpty()) {
            return;
        }
        opts.add(new Choice("pass", null, "no contribution"));
        Choice ch = agents.get(p.seat).choose(s, opts, ev("kind", "super_part"));
        if (ch.payload() == null) {
            return;
        }
        String kind = (String) ch.payload();
        if (kind.equals("kelium") || kind.equals("coin") || kind.equals("ammo") || kind.equals("debris")) {
            p.resources.pay(kelium.core.Resource.fromCode(kind), 1);
        } else if (kind.equals("enemy_unit_token") || kind.equals("enemy_building_token")) {
            boolean wantUnit = kind.equals("enemy_unit_token");
            for (Token t : new ArrayList<>(p.trophySpace)) {
                if ((t instanceof UnitToken) == wantUnit) {
                    p.trophySpace.remove(t);   // вложен навсегда (из игры)
                    break;
                }
            }
        } else if (kind.equals("own_unit_on_enemy_hex")) {
            outer:
            for (UnitToken u : new ArrayList<>(p.unitsOnField())) {
                for (PlayerState pl : s.players) {
                    if (pl.seat == p.seat) {
                        continue;
                    }
                    for (Token t : allOnField(pl)) {
                        if (u.hexId.equals(t.hexId())) {
                            p.units.remove(u);   // жертвуется
                            break outer;
                        }
                    }
                }
            }
        }
        // позиционные части (own_miner_bordering_grid, own_building_adjacent_enemy)
        // не жертвуются — их «оплата» = истинный предикат (проверен выше)
        p.superPartProgress.merge(kind, 1, Integer::sum);
        p.superObjectiveProgress += 1;

        boolean complete = true;
        for (Object po : parts) {
            Map<String, Object> part = (Map<String, Object>) po;
            int need = ((Number) part.get("amount")).intValue();
            if (p.superPartProgress.getOrDefault(String.valueOf(part.get("kind")), 0) < need) {
                complete = false;
                break;
            }
        }
        // ПО ЗА ПЕРВУЮ ЧАСТЬ начисляются РОВНО ОДИН РАЗ — в момент, когда лицо
        // карты собрано целиком. Величина напечатана на карте (2–5 по стоимости
        // сдаваемого), а не выведена формулой: цена части — решение дизайнера.
        int firstPartVp = 0;
        if (complete && p.superFirstPartVp == 0) {
            Map<String, Object> so = Ctx.cards(state, "super_objectives")
                .find(p.superObjective);
            if (so != null && so.get("first_part_vp") instanceof Number n) {
                firstPartVp = n.intValue();
                p.superFirstPartVp = firstPartVp;
            }
        }
        p.superObjectiveComplete = complete;
        emit(ev("type", "super_objective", "seat", p.seat, "card", p.superObjective,
            "progress", p.superObjectiveProgress, "complete", p.superObjectiveComplete,
            "first_part_vp", firstPartVp));
    }

    private static List<Token> allOnField(PlayerState pl) {
        List<Token> out = new ArrayList<>();
        out.addAll(pl.unitsOnField());
        out.addAll(pl.buildingsOnField());
        return out;
    }

    private Order topOrder(String cardId) {
        Map<String, Object> card = orders().byId(cardId);
        if (Boolean.TRUE.equals(card.get("joker"))) {
            return null;
        }
        return Order.fromCode((String) card.get("top"));
    }

    /**
     * ПРАВИЛО (уточнение 2026-08-15): Возврат СНАЧАЛА проверяет условия конца
     * партии; если это ПОСЛЕДНИЙ Возврат игры (мирный конец наступает СЕЙЧАС,
     * либо это конец последнего разрешённого раунда), возврат жетонов вообще НЕ
     * делается — трофеи остаются лежать у игроков, и в подсчёт очков идёт их
     * ПОЛНАЯ печатная ценность (см. Scoring — trophySpacePoints() приплюсован к
     * обломкам), а не флат-1-за-жетон, как в обычном Возврате мидгейма. Мгновенная
     * победа (I4, {@code s.finished} уже true до вызова) отдельно пропускает
     * returnStep() целиком — сюда даже не заходит.
     */
    private void returnStep(boolean gameEnding) {
        GameState s = state;
        Ruleset rs = rs();
        // ЭКСПЕРИМЕНТ «военный трек» (economy.leftover_trophy_vp_per = N, 0=выкл,
        // и таким и остаётся во всех живых рулсетах) — устарел с правилом 2026-08-15
        // «в Возврат ВСЕ трофеи конвертируются в обломки» ниже: раньше это был
        // единственный способ утилизировать несданные трофеи очками, теперь их
        // просто конвертирует правило. Ключ не удалён (обратная совместимость
        // балансовых прогонов), но комбинировать его с обломками не нужно — он
        // не мешает (отдельный аддитивный канал war_track_vp), но и не нужен.
        int per = ((Number) rs.get("economy.leftover_trophy_vp_per", 0)).intValue();
        if (per > 0 && !gameEnding) {
            for (PlayerState p : s.players) {
                int pts = p.trophySpacePoints();
                int gained = pts / per;
                if (gained > 0) {
                    p.warTrackVp += gained;
                    emit(ev("type", "war_track", "seat", p.seat,
                        "points", pts, "vp", gained, "round", s.round));
                }
            }
        }
        // ПРАВИЛО 2 (2026-08-15): в Возврат ВСЕ трофеи со стола игрока
        // конвертируются в обломки 1:1 (заменяет старое «обменять ещё ровно один
        // жетон, остальное возвращается владельцам без конвертации» — то правило
        // так и не было реализовано в движке; здесь оно реализуется впервые, уже
        // в новом виде). Сами жетоны возвращаются исходным владельцам как раньше;
        // если это добытчик/энергостанция — возврат ЗАКРЫВАЕТ ячейки склада
        // владельца, и излишек кубиков сгорает без права выбора (Storage.forceEvictOnBuildingReturn).
        // НО НЕ в ПОСЛЕДНИЙ Возврат партии — см. javadoc метода.
        if (!gameEnding && rs.getBool("return_step.return_destroyed_tokens", true)) {
            int returned = 0;
            java.util.Set<Integer> ownersToReconcile = new java.util.HashSet<>();
            // ТРОФЕЙНЫЙ СКЛАД (карта арсенала b13, правило дизайнера 15.08.2026).
            // Сначала освобождаем ячейки: жетон, пролежавший на карте раунд,
            // уходит владельцу. Потом игрок выбирает, какой трофей задержать —
            // он не вернётся владельцу ещё раунд и не даст обломок.
            for (PlayerState p : s.players) {
                for (Token held : new ArrayList<>(p.trophyHeldOnCards)) {
                    held.setCapturedBy(null);
                    held.resetDamage();
                    held.setHexId(null);
                    emit(ev("type", "trophy_released", "seat", p.seat,
                        "owner", held.owner(), "round", s.round));
                }
                p.trophyHeldOnCards.clear();
                int slots = kelium.engine.ability.RuleQuery
                    .of(s, p.seat, kelium.engine.ability.Hook.RETURN_KEEP_TROPHY)
                    .base(0).ask();
                for (int i = 0; i < slots && !p.trophySpace.isEmpty(); i++) {
                    Token keep = chooseTrophyToHold(p);
                    if (keep == null) {
                        break;
                    }
                    p.trophySpace.remove(keep);
                    p.trophyHeldOnCards.add(keep);
                    emit(ev("type", "trophy_held", "seat", p.seat,
                        "owner", keep.owner(), "round", s.round));
                }
            }
            for (PlayerState p : s.players) {
                // ФЛАТ, не печатная ценность (уточнение 2026-08-15): несданный в
                // Науку жетон даёт РОВНО 1 обломок, независимо от trophyValue()
                // (техника ценностью 2 всё равно даёт 1, а не 2) — штраф за
                // хранение трофея до конца раунда вместо его активной траты.
                int flatCount = p.trophySpace.size();
                if (flatCount > 0) {
                    int gained = Storage.addDebrisCapped(s, p, flatCount);
                    emit(ev("type", "trophy_to_debris", "seat", p.seat,
                        "tokens", flatCount, "gained", gained, "round", s.round));
                }
                for (Token tok : new ArrayList<>(p.trophySpace)) {
                    tok.setCapturedBy(null);
                    tok.resetDamage();
                    tok.setHexId(null);
                    returned++;
                    if (tok instanceof BuildingToken bt
                            && (bt.type == kelium.core.BuildingType.MINER
                                || bt.type == kelium.core.BuildingType.POWER_PLANT)) {
                        ownersToReconcile.add(tok.owner());
                    }
                }
                p.trophySpace.clear();
            }
            for (int ownerSeat : ownersToReconcile) {
                Storage.forceEvictOnBuildingReturn(s, s.player(ownerSeat));
            }
            emit(ev("type", "tokens_returned", "round", s.round, "count", returned));
        }
        // «Аварийные щиты» (арсенал 2.0.0): +1 здоровья зданиям временный (только
        // в Combat.effectiveHp, поле b.hp не трогается), но раненые здания, которые
        // без бонуса УЖЕ считались бы уничтоженными, в Возврат уходят в резерв —
        // временная защита оборачивается потерей, если урон не сняли.
        for (PlayerState p : s.players) {
            if (!Passives.hasPassive(s, p.seat, "buildings_plus1_hp_until_round_end")) {
                continue;
            }
            for (BuildingToken b : new ArrayList<>(p.buildingsOnField())) {
                if (b.damage >= b.hp) {
                    Actions.returnOwnBuildingToReserve(s, p, b);
                    emit(ev("type", "ability_reaction", "seat", p.seat,
                        "ability", "buildings_plus1_hp_until_round_end",
                        "returned_building", b.type.code));
                }
            }
        }
        // Пополнение заданий: в конце раунда игрок получает РОВНО ОДНУ новую карту,
        // только если у него сейчас СТРОГО МЕНЬШЕ трёх. Предела руки в середине
        // раунда нет — лимит проверяется только здесь.
        int limit = rs.getInt("rounds.objective_hand_limit");
        for (PlayerState p : s.players) {
            // Пассив objective_hand_plus1 (стартовая карта «Штаб связи»):
            // лимит руки заданий для пополнения +1.
            int myLimit = limit
                + (Passives.hasPassive(s, p.seat, "objective_hand_plus1") ? 1 : 0);
            if (p.objectiveHand.size() < myLimit) {
                String c = s.decks.get("objectives").draw(s.rng);
                if (c != null) {
                    p.objectiveHand.add(c);
                    emit(ev("type", "objective_drawn", "seat", p.seat, "card", c,
                        "hand", p.objectiveHand.size(), "source", "round_end"));
                }
            }
        }
        emit(ev("type", "return", "round", s.round));
    }

    /**
     * Какой трофей задержать на карте «Трофейный склад».
     *
     * <p>Спрашиваем игрока: выбор осмысленный, а не механический. Задержать
     * выгоднее тот жетон, который противнику нужнее всего — тогда он не сможет
     * выставить его заново (личный запас у каждого ровно по четыре на род).
     * Отказаться тоже можно: задержанный трофей не конвертируется в обломок,
     * то есть за отказ платят одним очком экономики.
     */
    private Token chooseTrophyToHold(PlayerState p) {
        GameState s = state;
        if (agents == null || p.seat >= agents.size() || agents.get(p.seat) == null) {
            return p.trophySpace.get(0);
        }
        List<kelium.core.Choice> opts = new ArrayList<>();
        for (Token t : p.trophySpace) {
            opts.add(new kelium.core.Choice("trophy_hold", t,
                "задержать жетон игрока " + t.owner()
                    + " (ценность " + t.trophyValue() + ")"));
        }
        opts.add(new kelium.core.Choice("pass", null, "не задерживать"));
        kelium.core.Choice c = agents.get(p.seat).choose(s, opts,
            java.util.Map.of("kind", "trophy_hold"));
        return c.payload() instanceof Token t ? t : null;
    }

    // ---- условия конца партии --------------------------------------------
    /**
     * Мирные условия конца партии (дизайнер, ПРОБЛЕМА 2). Партия заканчивается,
     * когда выполнено ЛЮБОЕ из:
     * <ol>
     *   <li>заняты все ТРИ последние (верхние, шаг 5) ячейки на трёх тех-треках
     *       — {@code tech.allPeaksOccupied()};</li>
     *   <li>на поле остался ПОСЛЕДНИЙ источник келемия: не более 1 гекса типа
     *       SPAWN с {@code kelium > 0} и не убранного ({@code !spawnRemoved}).</li>
     * </ol>
     * Условие 3 (мгновенная победа: 2-е уничтожение ЦУ или развёртка
     * супер-задания) обрабатывается отдельно через {@code s.finished}.
     * Никакой завязки на минимальное число раундов больше НЕТ.
     */
    private boolean peacefulEnd() {
        GameState s = state;
        if (s.tech.allPeaksOccupied()) {
            s.winCondition = "all_peaks_occupied";
            return true;
        }
        List<Hex> spawnTiles = s.field.spawnTiles();
        int remaining = 0;
        for (Hex h : spawnTiles) {
            if (h.spawnTile != null && h.spawnTile.kelium > 0) {
                // ДВОЙНОЙ ТАЙЛ СЧИТАЕТСЯ ЗА ДВА (уточнение дизайнера 13.08.2026):
                // под выработанным тайлом лежит ещё один такой же, и партия не
                // может кончиться «по последнему тайлу», пока стопка не снята
                // целиком. Раньше считались ГЕКСЫ, и стопка ×2 обрывала партию
                // на раунд раньше срока.
                remaining += Math.max(1, h.spawnTile.stack);
            }
        }
        // Порог «остался последний источник келемия». Правило — ОДИН (СВОД), это
        // и значение по умолчанию. Ключ вынесен в ruleset для балансового стенда:
        // от числа тайлов напрямую зависит ДЛИНА партии, а от длины — успевает ли
        // окупиться война. Значение −1 выключает условие целиком (для опыта).
        int lastTile = ((Number) rs().get("end_conditions.last_spawn_tile_threshold", 1))
            .intValue();
        if (!spawnTiles.isEmpty() && lastTile >= 0 && remaining <= lastTile) {
            s.winCondition = "last_spawn_tile";
            return true;
        }
        return false;
    }

    private static Map<String, Object> resourcesMap(PlayerState p) {
        Map<String, Object> m = new HashMap<>();
        m.put("coin", p.resources.coin());
        m.put("kelium", p.resources.kelium());
        m.put("ammo", p.resources.ammo());
        m.put("debris", p.resources.debris());
        return m;
    }

    /**
     * ПРИВЯЗАТЬ к состоянию всё, без чего действия не работают: журнал хода,
     * разрешитель боя и агентов по местам.
     *
     * <p>Публичный шов. Раньше это делалось только внутри {@link #run()}, и
     * каждый тест, который хотел проверить ОДНО действие или ОДИН бой без
     * прогона целой партии, выписывал эти три строки руками (и забывал их).
     */
    public static void bind(GameState state, List<Agent> agents,
                            Consumer<Map<String, Object>> onEvent) {
        state.journal = new TurnJournal(state.numPlayers());
        state.combat = new CombatResolver(state, onEvent == null ? e -> { } : onEvent)
            .bindAgents(agents);
        state.agents = agents;
    }

    /** Привязать без наблюдателя событий — для тестов и пробников. */
    public static void bind(GameState state, List<Agent> agents) {
        bind(state, agents, null);
    }

    /**
     * Привязка для ПРОДОЛЖЕНИЯ партии: журнал текущего хода сохраняется, если он
     * уже есть (в копии состояния он скопирован вместе с фактами хода). Обычная
     * {@link #bind} завела бы пустой журнал и «забыла», что игрок успел сделать.
     */
    public static void bindResume(GameState state, List<Agent> agents,
                                  Consumer<Map<String, Object>> onEvent) {
        if (state.journal == null) {
            state.journal = new TurnJournal(state.numPlayers());
        }
        state.combat = new CombatResolver(state, onEvent == null ? e -> { } : onEvent)
            .bindAgents(agents);
        state.agents = agents;
    }

    /** Удобная обёртка: создать движок и прогнать партию, вернув итог. */
    public static Map<String, Object> playGame(GameState state, List<Agent> agents,
                                               Consumer<Map<String, Object>> onEvent) {
        return new GameEngine(state, agents, onEvent).run();
    }
}
