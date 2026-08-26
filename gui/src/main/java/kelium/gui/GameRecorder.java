package kelium.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Consumer;

import kelium.agents.Genome;
import kelium.agents.HeuristicAgent;
import kelium.agents.RandomAgent;
import kelium.agents.StrategicAgent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.dataio.ContentSet;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.engine.Storage;
import kelium.report.FieldGeometry;
import kelium.dataio.Locations;
import kelium.report.ReplayRecord;
import kelium.core.Agent;

/**
 * GameRecorder — прогон партии и её ЗАПИСЬ по шагам.
 *
 * <p>Движок играет партию целиком и синхронно (остановить его нельзя), поэтому
 * порядок такой: прогоняем один раз, на каждое событие снимаем «кадр» —
 * состояние поля и игроков, само событие, строку лога и мысли ботов. Дальше
 * проигрыватель просто листает готовый список (заказ §4.2).
 *
 * <p>Подсветки (§4.4) вычисляются СРАВНЕНИЕМ соседних снимков: жетон сменил гекс
 * — стрелка перемещения; появился на поле — значок постройки; прибавился урон —
 * вспышка; перестал быть живым — уничтожение. Событие {@code combat_hit} даёт
 * линию удара «откуда → куда». Так подсветки не требуют правок движка.
 */
public final class GameRecorder {

    private GameRecorder() {
    }

    // ==================== боты по местам ====================

    /**
     * Вариант бота для одного места. Состав и названия — из единого справочника
     * {@link kelium.agents.BotCatalog}: раньше этот список был выписан здесь
     * отдельно от прогонщика и от лиги, и все три расходились между собой.
     */
    public record SeatOption(String id, String label, String tip) {
        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Кого можно посадить на место — ТОЛЬКО ИГРАЮЩИЕ НА ПОБЕДУ.
     *
     * <p>Приборы (жнец, аксиома, воитель, исследователь, ищейка, случайный,
     * простой) в выбор не попадают: их учили не победе, и за столом они делают
     * ходы, осмысленные лишь для замера (решение дизайнера 20.08.2026). Создать
     * их по имени по-прежнему можно — этим и живут инструменты замера, которые
     * читают полный BotCatalog.ALL.
     */
    public static List<SeatOption> seatOptions(int players) {
        List<SeatOption> out = new ArrayList<>();
        for (var e : kelium.agents.BotCatalog.players()) {
            out.add(new SeatOption(e.id(), e.label(), e.tip()));
        }
        return out;
    }

    /** Кого можно посадить на место (состав не зависит от числа игроков). */
    public static final List<SeatOption> SEAT_OPTIONS = seatOptions(4);

    /** Запомнить в записи, какие дополнения были включены. */
    private static void rememberExpansions(ReplayRecord rec) {
        var settings = kelium.dataio.AppSettings.of("replay2");
        for (String name : Expansions.ALL) {
            rec.expansions.put(name, Expansions.on(settings, name));
        }
    }

    /** Человеческое название бота по имени. */
    public static String botLabel(String id) {
        return kelium.agents.BotCatalog.label(id);
    }

    // ==================== прогон и запись ====================

    /**
     * Сыграть партию и вернуть её запись.
     *
     * @param rulesetId версия правил
     * @param players   число игроков (2..4)
     * @param seed      зерно (партия воспроизводится по нему детерминированно)
     * @param seatIds   боты по местам
     * @param note      куда писать краткие сообщения о ходе прогона (может быть null)
     */
    public static ReplayRecord play(String rulesetId, int players, long seed,
                                    List<String> seatIds, Consumer<String> note) {
        return play(rulesetId, players, seed, seatIds, null, null, note);
    }

    /**
     * То же, но с явным выбором раскладки и стартового поворота ЦУ по местам.
     *
     * @param scenarioId id варианта раскладки ({@code field_4p_v2}); null — по сиду
     * @param cuFacing   сторона гекса 0..5, с которой стоит ЦУ каждого места;
     *                   элемент null — подобрать автоматически
     */
    public static ReplayRecord play(String rulesetId, int players, long seed,
                                    List<String> seatIds, String scenarioId,
                                    List<Integer> cuFacing, Consumer<String> note) {
        return play(rulesetId, players, seed, seatIds, scenarioId, cuFacing, null, note);
    }

    /** То же плюс ФАЙЛ раскладки — поле, нарисованное конструктором. */
    public static ReplayRecord play(String rulesetId, int players, long seed,
                                    List<String> seatIds, String scenarioId,
                                    List<Integer> cuFacing, Path scenarioFile,
                                    Consumer<String> note) {
        return play(rulesetId, players, seed, seatIds, scenarioId, cuFacing,
            scenarioFile, null, note);
    }

    /**
     * То же плюс ОТДЕЛЬНЫЙ сид раскладки блоков.
     *
     * <p>Поле собирается из картонных блоков с напечатанными контейнерами, и за
     * столом игроки каждый раз кладут их иначе. Этот сид позволяет перекатить
     * ТОЛЬКО раскладку блоков: то же поле, те же колоды, те же боты — другое
     * расположение контейнеров. null — брать общий сид партии.
     */
    public static ReplayRecord play(String rulesetId, int players, long seed,
                                    List<String> seatIds, String scenarioId,
                                    List<Integer> cuFacing, Path scenarioFile,
                                    Long blockSeed, Consumer<String> note) {
        GameConfig cfg = GameConfig.buildCached(rulesetId, players, seed, null, null,
            scenarioId, cuFacing, scenarioFile);
        // ДОПОЛНЕНИЯ: выбор игрока накладывается на свод ДО сборки партии —
        // движок читает те же ключи expansions.*, что и тумблеры подготовки.
        Expansions.applyTo(cfg.ruleset, kelium.dataio.AppSettings.of("replay2"));
        GameState state = Setup.buildGame(cfg, blockSeed);
        ReplayRecord rec = header(cfg, state, players, seed, seatIds, scenarioId, cuFacing);
        rec.scenarioFile = scenarioFile == null ? null : scenarioFile.toString();
        // СОСТОЯНИЕ ДОПОЛНЕНИЙ — В ЗАПИСЬ (20.08.2026). Тот же сид и свод при
        // других тумблерах дают ДРУГУЮ партию, поэтому без этого запись нельзя
        // было повторить, а по логу — понять, что её породило.
        rememberExpansions(rec);

        List<ReplayRecord.Thought> pending = new ArrayList<>();
        List<Agent> agents = buildAgents(state, seed, rec.seatIds,
            (seat, text) -> pending.add(new ReplayRecord.Thought(seat, text)), note);

        ReplayText text = new ReplayText(cfg, rec);
        Recorder rc = new Recorder(state, rec, text, pending);
        Map<String, Object> result = GameEngine.playGame(state, agents, rc);

        rec.winner = result.get("winner") instanceof Number n ? n.intValue() : null;
        rec.condition = String.valueOf(result.get("condition"));
        rec.rounds = result.get("rounds") instanceof Number n ? n.intValue() : state.round;
        if (note != null) {
            note.accept("Партия сыграна: шагов " + rec.frames.size()
                + ", раундов " + rec.rounds + ".");
        }
        return rec;
    }

    /**
     * То же, что {@link #play}, но со СВОИМ списком агентов вместо ботов по
     * справочнику — вызывающий код (hot-seat, сетевой сервер) сам решает, кто
     * сидит на каждом месте: {@link kelium.core.InteractiveAgent} для живого
     * игрока, {@code Bots.create(...)} для бота. Запись пишется ТЕМ ЖЕ кодом
     * ({@code header}/{@code Recorder}/{@code ReplayText}), что и симуляции —
     * заказ на цифровую версию (§3, п.6) требует одинакового формата журнала
     * независимо от режима партии.
     *
     * @param seatLabels подписи мест для шапки записи (например {@code "human"}
     *                   для живого игрока, имя характера — для бота); длина
     *                   должна совпадать с {@code agents.size()}.
     */
    public static ReplayRecord playWithAgents(GameConfig cfg, GameState state,
                                               List<Agent> agents, List<String> seatLabels,
                                               long seed, Consumer<String> note) {
        return playWithAgents(cfg, state, agents, seatLabels, seed, note, null);
    }

    /**
     * То же, плюс {@code onFrame} — вызывается на каждый добавленный кадр СРАЗУ,
     * пока партия ещё играется. Нужно живому окну (hot-seat/сеть): та же запись
     * {@code rec}, что вернётся из этого вызова, все это время растёт у него на
     * руках — {@link FieldView}/{@link BoardsPanel} умеют показывать её "на
     * ходу", кадр за кадром, точно как готовую запись в {@code replay2}.
     *
     * <p>Вызывается в потоке движка — если это Swing, окну нужно самому уйти на
     * EDT ({@code SwingUtilities.invokeLater}), сюда это не встроено: движок не
     * должен знать о существовании Swing.
     */
    public static ReplayRecord playWithAgents(GameConfig cfg, GameState state,
                                               List<Agent> agents, List<String> seatLabels,
                                               long seed, Consumer<String> note,
                                               Consumer<ReplayRecord> onFrame) {
        return playWithAgents(cfg, state, agents, seatLabels, seed, null, note, onFrame);
    }

    /**
     * То же, плюс ЦВЕТА МЕСТ, выбранные игроком в меню запуска. Они не меняют
     * партию, но обязаны попасть в её журнал: иначе запись переиграется не в тех
     * красках, в каких за столом сидели.
     */
    public static ReplayRecord playWithAgents(GameConfig cfg, GameState state,
                                               List<Agent> agents, List<String> seatLabels,
                                               long seed, List<Integer> seatColors,
                                               Consumer<String> note,
                                               Consumer<ReplayRecord> onFrame) {
        ReplayRecord rec = header(cfg, state, state.numPlayers(), seed, seatLabels,
            cfg.scenarioId, cfg.cuFacing);
        if (seatColors != null) {
            rec.seatColors.addAll(seatColors);
        }
        rememberExpansions(rec);

        List<ReplayRecord.Thought> pending = new ArrayList<>();
        for (Agent a : agents) {
            if (a instanceof StrategicAgent sa) {
                sa.withThoughts((seat, txt) -> pending.add(new ReplayRecord.Thought(seat, txt)));
            }
        }

        ReplayText text = new ReplayText(cfg, rec);
        Recorder rc = new Recorder(state, rec, text, pending);
        Consumer<Map<String, Object>> sink = onFrame == null ? rc : event -> {
            rc.accept(event);
            onFrame.accept(rec);
        };
        Map<String, Object> result = GameEngine.playGame(state, agents, sink);

        rec.winner = result.get("winner") instanceof Number n ? n.intValue() : null;
        rec.condition = String.valueOf(result.get("condition"));
        rec.rounds = result.get("rounds") instanceof Number n ? n.intValue() : state.round;
        if (note != null) {
            note.accept("Партия сыграна: шагов " + rec.frames.size()
                + ", раундов " + rec.rounds + ".");
        }
        return rec;
    }

    /**
     * ПОДГОТОВКА без прогона: собрать состояние партии и вернуть запись из
     * одного кадра — стартовую расстановку. Нужна проигрывателю, чтобы поле
     * перерисовывалось СРАЗУ при смене числа игроков, раскладки или поворота ЦУ,
     * не дожидаясь партии.
     */
    public static ReplayRecord preview(String rulesetId, int players, long seed,
                                       List<String> seatIds, String scenarioId,
                                       List<Integer> cuFacing, Path scenarioFile) {
        return preview(rulesetId, players, seed, seatIds, scenarioId, cuFacing,
            scenarioFile, null);
    }

    /** То же плюс отдельный сид раскладки блоков (см. {@link #play}). */
    public static ReplayRecord preview(String rulesetId, int players, long seed,
                                       List<String> seatIds, String scenarioId,
                                       List<Integer> cuFacing, Path scenarioFile,
                                       Long blockSeed) {
        GameConfig cfg = GameConfig.buildCached(rulesetId, players, seed, null, null,
            scenarioId, cuFacing, scenarioFile);
        // ДОПОЛНЕНИЯ: выбор игрока накладывается на свод ДО сборки партии —
        // движок читает те же ключи expansions.*, что и тумблеры подготовки.
        Expansions.applyTo(cfg.ruleset, kelium.dataio.AppSettings.of("replay2"));
        GameState state = Setup.buildGame(cfg, blockSeed);
        ReplayRecord rec = header(cfg, state, players, seed, seatIds, scenarioId, cuFacing);
        rec.scenarioFile = scenarioFile == null ? null : scenarioFile.toString();
        // СОСТОЯНИЕ ДОПОЛНЕНИЙ — В ЗАПИСЬ (20.08.2026). Тот же сид и свод при
        // других тумблерах дают ДРУГУЮ партию, поэтому без этого запись нельзя
        // было повторить, а по логу — понять, что её породило.
        rememberExpansions(rec);
        ReplayRecord.Frame f = new ReplayRecord.Frame();
        f.type = "setup_preview";
        f.log = "РАССТАНОВКА — " + players + " игроков, поле "
            + (scenarioId == null ? "по сиду " + seed : scenarioId)
            + ". Нажми «Сыграть и показать», чтобы увидеть партию.";
        f.snapshot = snapshot(state, null);
        rec.frames.add(f);
        rec.rounds = 0;
        rec.condition = "не сыграна";
        return rec;
    }

    /** Общая шапка записи: кто играет, на чём и какое поле. */
    private static ReplayRecord header(GameConfig cfg, GameState state, int players, long seed,
                                       List<String> seatIds, String scenarioId,
                                       List<Integer> cuFacing) {
        ReplayRecord rec = new ReplayRecord();
        rec.ruleset = cfg.ruleset.id;
        // ПЕЧАТНЫЙ ЛИЧНЫЙ ЗАПАС ВОЙСК — чтобы планшет игрока не выводил его сам из
        // того, сколько жетонов успело появиться в партии.
        if (state != null && state.tokenStats != null) {
            for (kelium.core.UnitType t : kelium.core.UnitType.values()) {
                rec.unitStock.put(t.code, state.tokenStats.unitStock(t));
            }
        }
        rec.players = players;
        rec.seed = seed;
        rec.scenarioId = scenarioId;
        if (cuFacing != null) {
            rec.cuFacing.addAll(cuFacing);
        }
        for (int seat = 0; seat < players; seat++) {
            String id = seat < seatIds.size() ? seatIds.get(seat) : "trained:balanced";
            rec.seatIds.add(id);
            // «human» — не бот из справочника, а живое место цифровой версии:
            // без этого в планшетах игрока красовалось сырое «human» (блокер
            // приёмки: отладка на экране игрока).
            rec.seatLabels.add("human".equals(id) ? "Игрок " + (seat + 1) : botLabel(id));
            rec.sides.add(state.player(seat).board.troop.side);
        }
        fillTableAndField(rec, cfg, state);
        return rec;
    }

    /**
     * НАЗВАНИЯ КАРТ И ГЕОМЕТРИЯ ПОЛЯ — то, без чего запись нельзя нарисовать.
     *
     * <p>Вынесено в отдельный публичный шов ради режима разработчика: он собирает
     * состояние руками и обязан получить ту же шапку, что настоящая партия. Без
     * геометрии {@link kelium.gui.FieldView} рисует пустоту, потому что список
     * гексов у него берётся из шапки, а не из снимка, — и это правильно: гексы за
     * партию не меняются, повторять их в каждом кадре незачем.
     *
     * <p>Вторая копия этого кода означала бы, что сцена рисуется не тем полем, что
     * партия, и расхождение нашлось бы не сразу.
     */
    public static void fillTableAndField(ReplayRecord rec, GameConfig cfg, GameState state) {
        collectCardNames(cfg, rec);
        for (Hex h : state.field.hexes.values()) {
            int[] qr = FieldGeometry.parseQR(h.id);
            ReplayRecord.HexInfo hi = new ReplayRecord.HexInfo();
            hi.id = h.id;
            hi.q = qr != null ? qr[0] : 0;
            hi.r = qr != null ? qr[1] : 0;
            hi.kind = h.kind.name();
            rec.hexes.add(hi);
        }
    }

    /** Собрать агентов по местам и подключить к стратегам приёмник мыслей. */
    private static List<Agent> buildAgents(GameState state, long seed, List<String> seatIds,
                                           java.util.function.BiConsumer<Integer, String> thoughts,
                                           Consumer<String> note) {
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < state.numPlayers(); seat++) {
            Random r = new Random(seed * 131 + seat + 1);
            // Кого посадить — решает справочник ботов, один на всю программу.
            Agent a = kelium.agents.BotCatalog.create(seatIds.get(seat), seat, r,
                state.numPlayers());
            if (a instanceof StrategicAgent sa) {
                sa.withThoughts(thoughts);   // бот озвучивает свои решения в записи
            }
            agents.add(a);
        }
        return agents;
    }

    private static Genome loadGenome(int players, Consumer<String> note) {
        Path gp = Locations.botMemory()
            .resolve("strategic_" + players + "p.json");
        try {
            return Genome.loadJson(gp);
        } catch (Exception e) {
            if (note != null) {
                note.accept("Геном " + gp.getFileName() + " не загрузился — "
                    + "стратеги играют настройками по умолчанию.");
            }
            return Genome.defaults();
        }
    }

    /** Названия карт по идентификаторам — чтобы в зонах игроков был человеческий текст. */
    private static void collectCardNames(GameConfig cfg, ReplayRecord rec) {
        for (String type : List.of("orders", "objectives", "arsenal", "super_objectives",
                                   "super_arsenal", "market", "containers")) {
            ContentSet cs;
            try {
                cs = cfg.content.get(type);
            } catch (RuntimeException e) {
                continue;                       // такого набора в правилах нет
            }
            if (cs == null) {
                continue;
            }
            // У ПРИКАЗОВ НАЗВАНИЯ В НАБОРЕ НЕТ: карта опознаётся колодой и парой
            // приказов. Без этого запись отдавала бы вместо имени внутренний код
            // (blue_acq на планшете игрока, найдено 13.08.2026).
            if ("orders".equals(type)) {
                rec.cardNames.putAll(kelium.gui.replay2.HelpCards.names(cs, type));
                continue;
            }
            for (Map<String, Object> e : cs.entries) {
                Object id = e.get("id");
                Object name = e.get("name");
                if (id != null && name != null) {
                    rec.cardNames.put(String.valueOf(id), String.valueOf(name));
                }
            }
        }
    }

    // ==================== приёмник событий ====================

    /** Приёмник событий движка: на каждое событие кладёт кадр в запись. */
    private static final class Recorder implements Consumer<Map<String, Object>> {

        private final GameState state;
        private final ReplayRecord rec;
        private final ReplayText text;
        private final List<ReplayRecord.Thought> pending;
        private ReplayRecord.Snapshot prev;

        Recorder(GameState state, ReplayRecord rec, ReplayText text,
                 List<ReplayRecord.Thought> pending) {
            this.state = state;
            this.rec = rec;
            this.text = text;
            this.pending = pending;
        }

        @Override
        public void accept(Map<String, Object> event) {
            ReplayRecord.Frame f = new ReplayRecord.Frame();
            f.type = String.valueOf(event.get("type"));
            f.round = state.round;
            f.circle = state.circle;
            f.seat = event.get("seat") instanceof Number n ? n.intValue() : null;
            f.log = text.describe(event, state);
            f.combat = "combat_hit".equals(f.type) || "raze_neutral".equals(f.type)
                || "damage_neutral".equals(f.type);
            f.thoughts.addAll(pending);
            pending.clear();

            ReplayRecord.Snapshot snap = snapshot(state, f.seat);
            f.highlight = diff(prev, snap);
            if ("combat_hit".equals(f.type)) {
                String from = String.valueOf(event.get("source"));
                String to = String.valueOf(event.get("target"));
                f.highlight.attacks.add(new String[]{from, to});
                if (!f.highlight.damaged.contains(to)) {
                    f.highlight.damaged.add(to);
                }
            }
            if ("raze_neutral".equals(f.type) || "damage_neutral".equals(f.type)) {
                String to = String.valueOf(event.get("target"));
                if (!f.highlight.damaged.contains(to)) {
                    f.highlight.damaged.add(to);
                }
            }
            f.snapshot = snap;
            prev = snap;
            rec.frames.add(f);
            int idx = rec.frames.size() - 1;
            if ("reveal".equals(f.type)) {
                // ВСЕ вскрывают приказы ОДНОВРЕМЕННО — значит и на столе карты
                // должны появиться сразу, все сразу (просьба дизайнера). Что
                // именно карта даст, дозаполним на ходу её владельца.
                @SuppressWarnings("unchecked")
                Map<Integer, String> rev = (Map<Integer, String>) event.get("revealed");
                if (rev != null) {
                    for (Map.Entry<Integer, String> e : new java.util.TreeMap<>(rev).entrySet()) {
                        rec.orderPlays.add(text.revealedPlay(e.getKey(), e.getValue(), f, idx));
                    }
                }
            }
            if ("turn_orders".equals(f.type)) {
                ReplayRecord.OrderPlay op = find(rec, f.seat, f.round,
                    String.valueOf(event.get("card")));
                if (op == null) {
                    // карта почему-то не попала во вскрытие — заводим на ходу
                    op = orderPlay(event, f, idx);
                    rec.orderPlays.add(op);
                } else {
                    fill(op, event);
                }
                // ВСКРЫТИЕ и ХОД — разные моменты: карта уже лежала на столе,
                // а сейчас лишь стало ясно, что она даёт.
                op.turnFrame = idx;
            }
        }

        /** Найти уже вскрытую карту этого игрока в этом раунде. */
        private static ReplayRecord.OrderPlay find(ReplayRecord rec, Integer seat,
                                                   int round, String card) {
            if (seat == null) {
                return null;
            }
            for (ReplayRecord.OrderPlay op : rec.orderPlays) {
                if (op.seat == seat && op.round == round && op.card.equals(card)
                        && op.turnFrame < 0) {
                    return op;
                }
            }
            return null;
        }

        /** Собрать разыгранную карту приказа для наглядной «руки на столе». */
        private static ReplayRecord.OrderPlay orderPlay(Map<String, Object> ev,
                                                        ReplayRecord.Frame f, int frameIdx) {
            ReplayRecord.OrderPlay op = new ReplayRecord.OrderPlay();
            op.seat = f.seat == null ? 0 : f.seat;
            op.round = f.round;
            op.circle = f.circle;
            op.revealFrame = frameIdx;
            fill(op, ev);
            return op;
        }

        /** Дозаполнить карту раскладкой, узнанной на ходу владельца. */
        @SuppressWarnings("unchecked")
        private static void fill(ReplayRecord.OrderPlay op, Map<String, Object> ev) {
            op.card = String.valueOf(ev.get("card"));
            op.top = String.valueOf(ev.get("top"));
            op.topActions.clear();
            op.topActions.addAll((List<String>) ev.getOrDefault("top_actions", List.of()));
            op.topAllowed = ev.get("top_allowed") instanceof Number n ? n.intValue() : 2;
            op.coincided = Boolean.TRUE.equals(ev.get("coincided"));
            op.bottom = ev.get("bottom") == null ? null : String.valueOf(ev.get("bottom"));
            op.bottomActions.clear();
            op.bottomActions.addAll((List<String>) ev.getOrDefault("bottom_actions", List.of()));
            op.bottomOpen = Boolean.TRUE.equals(ev.get("bottom_open"));
            op.maneuver = Boolean.TRUE.equals(ev.get("maneuver"));
        }
    }

    // ==================== снимок состояния ====================

    /** Снять всё, что рисуется, с живого состояния партии. */
    static ReplayRecord.Snapshot snapshot(GameState s, Integer active) {
        return ReplayRecord.snapshotOf(s, active);
    }


    // ==================== подсветки через сравнение снимков ====================

    /** Что изменилось между снимками: движение, стройка, урон, уничтожение. */
    static ReplayRecord.Highlight diff(ReplayRecord.Snapshot before, ReplayRecord.Snapshot after) {
        ReplayRecord.Highlight h = new ReplayRecord.Highlight();
        if (before == null || after == null) {
            return h;
        }
        Map<Integer, ReplayRecord.Tok> was = new HashMap<>();
        for (ReplayRecord.Tok t : before.tokens) {
            was.put(t.uid, t);
        }
        for (ReplayRecord.Tok now : after.tokens) {
            ReplayRecord.Tok old = was.get(now.uid);
            if (old == null) {
                if (now.hexId != null && now.alive) {
                    h.builds.add(now.hexId);
                }
                continue;
            }
            boolean wasOnField = old.hexId != null && old.alive;
            boolean nowOnField = now.hexId != null && now.alive;
            if (wasOnField && nowOnField && !old.hexId.equals(now.hexId)) {
                h.moves.add(new String[]{old.hexId, now.hexId});
            } else if (!wasOnField && nowOnField) {
                h.builds.add(now.hexId);
            } else if (wasOnField && !nowOnField) {
                // жетон ушёл с поля: уничтожен (иначе он остался бы живым)
                if (!now.alive) {
                    h.destroyed.add(old.hexId);
                }
            }
            if (now.damage > old.damage) {
                String where = nowOnField ? now.hexId : old.hexId;
                if (where != null && !h.damaged.contains(where)) {
                    h.damaged.add(where);
                }
                if (!now.alive && old.alive && where != null && !h.destroyed.contains(where)) {
                    h.destroyed.add(where);
                }
            }
        }
        return h;
    }

    // ==================== строки лога ====================

    /**
     * ReplayText — превращает событие движка в строку лога по-русски.
     * Формулировки согласованы с русским логом партии ({@code GameLogger}).
     */
    static final class ReplayText {

        private static final Map<String, String> ACTIONS = Map.ofEntries(
            Map.entry("assembly", "сборка"),
            Map.entry("mining", "добыча"),
            Map.entry("build", "стройка"),
            Map.entry("energy_swap", "энергия"),
            Map.entry("movement", "движение"),
            Map.entry("combat", "бой"),
            Map.entry("market", "рынок"),
            Map.entry("science", "наука"));

        private static final Map<String, String> ORDERS = Map.of(
            "development", "Разработка",
            "infrastructure", "Инфраструктура",
            "operation", "Операция",
            "acquisitions", "Приобретения");

        private final GameConfig cfg;
        private final ReplayRecord rec;

        ReplayText(GameConfig cfg, ReplayRecord rec) {
            this.cfg = cfg;
            this.rec = rec;
        }

        private String who(Object seat) {
            if (seat instanceof Number n) {
                return rec.playerName(n.intValue());
            }
            return "игрок ?";
        }

        /** Имя карты приказа: «Разработка (blue_dev)». */
        /**
         * РАСКЛАДКА ПРИКАЗА на ход — отдельными строками, чтобы в логе было
         * видно не только «что сыграл», но и «что ему вообще доступно»
         * (просьба дизайнера 12.08.2026):
         *
         * <pre>
         * ▸ Игрок 1 · ПРИКАЗ: РАЗРАБОТКА (o07)
         *     сверху доступно: сборка · добыча
         *     СОВПАДЕНИЕ: приказ вскрыл кто-то ещё — вместо двух действий одно
         *     снизу ОТКРЫЛОСЬ: рынок · наука — одно действие
         *     манёвр: бесплатный ход одним жетоном
         * </pre>
         */
        @SuppressWarnings("unchecked")
        private String turnOrders(Map<String, Object> ev) {
            String top = String.valueOf(ev.get("top"));
            StringBuilder sb = new StringBuilder();
            sb.append(who(ev.get("seat"))).append(" · ПРИКАЗ: ")
              .append(ORDERS.getOrDefault(top, top).toUpperCase(Locale.ROOT))
              .append(" (").append(ev.get("card")).append(')');

            List<String> tops = (List<String>) ev.getOrDefault("top_actions", List.of());
            int allowed = ev.get("top_allowed") instanceof Number n ? n.intValue() : 2;
            sb.append('\n').append("    сверху доступно: ").append(actionList(tops))
              .append(allowed >= tops.size() ? " — оба действия" : " — только ОДНО из двух");
            if (Boolean.TRUE.equals(ev.get("coincided"))) {
                sb.append('\n').append("    ! СОВПАДЕНИЕ: этот приказ вскрыл кто-то ещё, "
                    + "поэтому сверху играется одно действие вместо двух");
            }

            Object bottom = ev.get("bottom");
            List<String> bots = (List<String>) ev.getOrDefault("bottom_actions", List.of());
            if (bottom != null && !bots.isEmpty()) {
                boolean open = Boolean.TRUE.equals(ev.get("bottom_open"));
                String bcode = String.valueOf(bottom);
                sb.append('\n').append("    снизу ")
                  .append(open ? "ОТКРЫЛОСЬ: " : "закрыто: ")
                  .append(ORDERS.getOrDefault(bcode, bcode)).append(" — ")
                  .append(actionList(bots))
                  .append(open ? " — одно действие"
                      : " (нужно, чтобы этот приказ кто-то вскрыл сверху)");
            }
            if (Boolean.TRUE.equals(ev.get("maneuver"))) {
                sb.append('\n').append("    манёвр: бесплатный ход одним жетоном войска");
            }
            return sb.toString();
        }

        private String actionList(List<String> ids) {
            StringBuilder sb = new StringBuilder();
            for (String a : ids) {
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                sb.append(ACTIONS.getOrDefault(a, a));
            }
            return sb.toString();
        }

        /**
         * Карта, ТОЛЬКО ЧТО ВСКРЫТАЯ игроком: известны сам приказ и его действия,
         * но ещё не известно, срежет ли совпадение действия и откроется ли низ —
         * это выяснится на ходу владельца.
         */
        ReplayRecord.OrderPlay revealedPlay(int seat, String cid,
                                            ReplayRecord.Frame f, int frameIdx) {
            ReplayRecord.OrderPlay op = new ReplayRecord.OrderPlay();
            op.seat = seat;
            op.round = f.round;
            op.circle = f.circle;
            op.revealFrame = frameIdx;
            op.card = cid;
            try {
                Map<String, Object> c = cfg.content.get("orders").byId(cid);
                boolean joker = Boolean.TRUE.equals(c.get("joker"));
                op.top = joker ? "joker" : String.valueOf(c.get("top"));
                if (!joker) {
                    op.topActions.addAll(List.of(kelium.engine.Order.ORDER_ACTIONS.get(
                        kelium.engine.Order.fromCode(op.top))));
                    if (c.get("bottom") != null) {
                        op.bottom = String.valueOf(c.get("bottom"));
                        op.bottomActions.addAll(List.of(kelium.engine.Order.ORDER_ACTIONS.get(
                            kelium.engine.Order.fromCode(op.bottom))));
                    }
                }
                op.maneuver = Boolean.TRUE.equals(c.get("maneuver"));
            } catch (RuntimeException e) {
                op.top = "joker";
            }
            return op;
        }


    private String order(String cid) {
            try {
                Map<String, Object> c = cfg.content.get("orders").byId(cid);
                if (Boolean.TRUE.equals(c.get("joker"))) {
                    return "БЕЗОПАСНОСТЬ (" + cid + ")";
                }
                String top = String.valueOf(c.get("top"));
                return ORDERS.getOrDefault(top, top) + " (" + cid + ")";
            } catch (RuntimeException e) {
                return cid;
            }
        }

        private String card(Object cid) {
            return cid == null ? "—"
                : kelium.gui.replay2.Names.card(rec, String.valueOf(cid));
        }

        /** Строка лога для одного события. */
        @SuppressWarnings("unchecked")
        String describe(Map<String, Object> ev, GameState s) {
            String t = String.valueOf(ev.get("type"));
            switch (t) {
                case "game_start":
                    return "НАЧАЛО ПАРТИИ — игроков " + ev.get("players")
                        + ", правила " + ev.get("ruleset") + ", сид " + rec.seed;
                case "refresh":
                    if (Boolean.TRUE.equals(ev.get("skipped"))) {
                        return "=== РАУНД " + ev.get("round") + " — обновление пропускается";
                    }
                    return "=== РАУНД " + ev.get("round") + " — обновление; первым ходит "
                        + who(ev.get("first_player")) + "; карта рынка: " + card(s.marketActive);
                case "blind_discard": {
                    Object sa = ev.get("set_aside");
                    if (!(sa instanceof Map<?, ?> m) || m.isEmpty()) {
                        return "Отложенные приказы: откладывать нечего";
                    }
                    StringBuilder sb = new StringBuilder("Отложенные приказы: ");
                    Map<Integer, String> sorted = new TreeMap<>((Map<Integer, String>) sa);
                    boolean first = true;
                    for (Map.Entry<Integer, String> e : sorted.entrySet()) {
                        if (!first) {
                            sb.append("; ");
                        }
                        first = false;
                        sb.append(who(e.getKey())).append(" отложил ").append(order(e.getValue()));
                    }
                    return sb.toString();
                }
                case "reveal": {
                    Map<Integer, String> rev = new TreeMap<>((Map<Integer, String>) ev.get("revealed"));
                    StringBuilder sb = new StringBuilder("Круг " + ev.get("circle") + ": ");
                    boolean first = true;
                    for (Map.Entry<Integer, String> e : rev.entrySet()) {
                        if (!first) {
                            sb.append("; ");
                        }
                        first = false;
                        sb.append(who(e.getKey())).append(" вскрыл ").append(order(e.getValue()));
                    }
                    return sb.toString();
                }
                case "turn_orders":
                    return turnOrders(ev);
                case "action": {
                    boolean ok = Boolean.TRUE.equals(ev.get("ok"));
                    String name = ACTIONS.getOrDefault(String.valueOf(ev.get("action")),
                        String.valueOf(ev.get("action")));
                    String detail = ru(String.valueOf(ev.getOrDefault("detail", "")));
                    // Компенсация энергии монетами видна не по жетону (кубик в
                    // ячейку не кладётся), поэтому её надо ПИСАТЬ — иначе
                    // выглядит, будто незапитанное здание сработало само.
                    String paid = "";
                    if (ev.get("telemetry") instanceof Map<?, ?> tel
                            && tel.get("power_coins") instanceof Number pc
                            && pc.intValue() > 0) {
                        paid = "  [энергия куплена за " + pc.intValue() + " МОН]";
                    }
                    // МЕТКА — ТОЧКОЙ, а не квадратиком. Символы ▪/▫ в шрифте
                    // интерфейса отсутствуют, и на экране вместо них рисовался
                    // перечёркнутый прямоугольник — «⊠ ДОБЫЧА» в поворотных
                    // моментах (замечание дизайнера 15.08.2026). Смысл несёт не
                    // значок, а приписка «— не вышло», она и остаётся.
                    return "   " + (ok ? "· " : "· ") + name.toUpperCase(Locale.ROOT)
                        + (ok ? "" : " — не вышло")
                        + (detail.isBlank() || "null".equals(detail) ? "" : ": " + detail)
                        + paid;
                }
                case "combat_hit": {
                    boolean dead = Boolean.TRUE.equals(ev.get("destroyed"));
                    return "БОЙ. " + who(ev.get("seat")) + ": " + ev.get("attacker") + " с "
                        + ev.get("source") + " бьёт по " + ev.get("target") + " — "
                        + who(ev.get("victim_owner")) + ", " + ev.get("victim")
                        + (dead ? " УНИЧТОЖЕН" : " получает урон");
                }
                case "raze_neutral":
                    return "БОЙ. " + who(ev.get("seat")) + " снёс нейтральную постройку на "
                        + ev.get("target") + " (+" + ev.getOrDefault("trophy", 1) + " трофей, +"
                        + ev.getOrDefault("containers", 1) + " контейнер)";
                case "damage_neutral":
                    return "БОЙ. " + who(ev.get("seat")) + " повредил нейтрала на " + ev.get("target")
                        + ", у него осталось прочности " + ev.get("hpLeft");
                case "maneuver":
                    return who(ev.get("seat")) + " воспользовался манёвром: жетон переведён на "
                        + ev.get("to");
                case "container":
                    return who(ev.get("seat")) + " вскрыл контейнер «" + card(ev.get("card"))
                        + "», выбрал вариант " + ("a".equals(ev.get("variant")) ? "А" : "Б");
                case "objective":
                    return "ЗАДАНИЕ. " + who(ev.get("seat")) + " выполнил задание «" + card(ev.get("card"))
                        + "»" + (Boolean.TRUE.equals(ev.get("enhanced")) ? " с усилением" : "");
                case "objective_burn":
                    return who(ev.get("seat")) + " сжёг верх задания «" + card(ev.get("card"))
                        + "» (" + ev.getOrDefault("label", "") + ")";
                case "objective_drawn":
                    return who(ev.get("seat")) + " получил задание «" + card(ev.get("card"))
                        + "» (в руке " + ev.get("hand") + ")";
                case "arsenal":
                    return who(ev.get("seat"))
                        + ("install".equals(ev.get("mode")) ? " установил арсенал «" : " сжёг арсенал «")
                        + card(ev.get("card")) + "»";
                case "arsenal_spec_use":
                    return who(ev.get("seat")) + " применил спец-способность арсенала «"
                        + card(ev.get("card")) + "»";
                case "super_objective": {
                    // ПО ЗА ПЕРВУЮ ЧАСТЬ (правила 1.7.0): их видно только здесь, в
                    // момент сборки лица карты, — в конце партии они уже просто
                    // строка в подсчёте.
                    int fp = ev.get("first_part_vp") instanceof Number n ? n.intValue() : 0;
                    return who(ev.get("seat")) + " внёс вклад в супер-задание «"
                        + card(ev.get("card")) + "» (частей " + ev.get("progress") + ")"
                        + (Boolean.TRUE.equals(ev.get("complete")) ? " — ЛИЦО СОБРАНО" : "")
                        + (fp > 0 ? ", получил " + fp + " ПО за первую часть" : "");
                }
                // --- СИМВОЛЫ СУПЕР ЗАДАНИЙ (модуль включён в 1.7.0) ---
                case "tuck":
                    return who(ev.get("seat")) + " подсунул под планшет "
                        + ("container".equals(ev.get("kind")) ? "контейнер" : "карту арсенала")
                        + " рубашкой вверх — ради символа";
                case "symbol_reveal":
                    return who(ev.get("seat")) + " вскрыл символ «" + ev.get("symbol")
                        + "» с карты «" + card(ev.get("card")) + "»";
                // --- доход от карт арсенала в Обновление ---
                case "refresh_income":
                    return who(ev.get("seat")) + " получил " + ev.get("coin")
                        + " монет дохода от установленных карт";
                case "super_deploy":
                    return "ПОБЕДА! " + who(ev.get("seat")) + " развернул супер-задание «"
                        + card(ev.get("card")) + "» — мгновенная победа!";
                case "module_swap":
                    return who(ev.get("seat")) + " переставил модули";
                case "war_track":
                    return who(ev.get("seat")) + " получил " + ev.get("vp")
                        + " ПО за несданные трофеи (" + ev.get("points") + ")";
                case "turn_end": {
                    Object r = ev.get("resources");
                    String res = "";
                    if (r instanceof Map<?, ?> rm) {
                        res = "монет " + num(rm, "coin") + ", келемия " + num(rm, "kelium")
                            + ", боеприпасов " + num(rm, "ammo")
                            + ", трофеев " + num(rm, "trophy");
                    }
                    return "   конец хода " + who(ev.get("seat")) + " — " + res;
                }
                case "tokens_returned":
                    return "Возврат: жетонов вернулось владельцам — " + ev.get("count");
                case "return":
                    return "--- конец раунда " + ev.get("round");
                case "game_end": {
                    Integer w = ev.get("winner") instanceof Number n ? n.intValue() : null;
                    String cond = switch (String.valueOf(ev.get("condition"))) {
                        case "victory_points" -> "по победным очкам";
                        case "super_objective" -> "развёрнутым супер-заданием";
                        case "all_peaks_occupied" -> "заняты все вершины треков";
                        case "last_spawn_tile" -> "кончился келемий на поле";
                        case "military" -> "военная победа (второе ЦУ)";
                        default -> String.valueOf(ev.get("condition"));
                    };
                    return "КОНЕЦ ПАРТИИ — победил " + (w == null ? "никто" : rec.playerName(w))
                        + " (" + cond + ")";
                }
                default:
                    return t + " " + trimmed(ev);
            }
        }

        /**
         * Перевести служебную строку {@code detail} движка на русский.
         *
         * <p>Движок пишет подробности по-английски («built factory @ h2_1 for 3»),
         * потому что это его внутренний технический лог. Дизайнер читает ИМЕННО
         * этот текст, поэтому здесь он превращается в человеческий. Незнакомая
         * строка отдаётся как есть — врать переводом хуже, чем оставить оригинал.
         */
        static String ru(String detail) {
            if (detail == null || detail.isBlank() || "null".equals(detail)) {
                return "";
            }
            StringBuilder out = new StringBuilder();
            for (String part : detail.split(";")) {
                String s = part.trim();
                if (s.isEmpty()) {
                    continue;
                }
                if (out.length() > 0) {
                    out.append("; ");
                }
                out.append(ruOne(s));
            }
            return out.toString();
        }

        private static String ruOne(String s) {
            java.util.regex.Matcher m;
            if ((m = re("mined (\\d+) kelium, (\\d+) containers", s)) != null) {
                return "добыто " + m.group(1) + " келемия и " + m.group(2) + " контейнеров";
            }
            if ((m = re("assembled (\\d+) units, (\\d+) ammo", s)) != null) {
                return "собрано войск " + m.group(1) + ", боеприпасов " + m.group(2);
            }
            if ((m = re("built (.+) @ (\\S+) for (\\d+)", s)) != null) {
                return "построено «" + m.group(1) + "» на " + m.group(2)
                    + " за " + m.group(3) + " мон.";
            }
            if ((m = re("no room to build (\\S+)", s)) != null) {
                return "негде поставить: " + bld(m.group(1));
            }
            if ((m = re("demolished (\\S+) @ (\\S+) \\(\\+(\\d+)\\)", s)) != null) {
                return "снесено: " + bld(m.group(1)) + " на " + m.group(2)
                    + " (вернулось " + m.group(3) + " мон.)";
            }
            if ((m = re("moved (\\S+) (\\S+) -> (\\S+)", s)) != null) {
                return "здание " + bld(m.group(1)) + " перенесено " + m.group(2)
                    + " → " + m.group(3);
            }
            if ((m = re("moved (\\d+) steps", s)) != null) {
                return "сделано шагов: " + m.group(1);
            }
            if ((m = re("energy swap: (\\d+) cubes over (\\d+) hexes", s)) != null) {
                return "переставлено кубиков энергии " + m.group(1)
                    + " на " + m.group(2) + " гексах";
            }
            if ((m = re("combat resolved x(\\d+)", s)) != null) {
                return "проведено боёв: " + m.group(1);
            }
            if ((m = re("market rate: 1 kel -> (\\d+) coin", s)) != null) {
                return "курс рынка: 1 келемий → " + m.group(1) + " мон.";
            }
            if ((m = re("market: (.+)", s)) != null) {
                return switch (m.group(1)) {
                    case "no kelium" -> "нести на рынок нечего — келемия нет";
                    case "no trade" -> "сделка не состоялась";
                    default -> "рынок: " + m.group(1);
                };
            }
            if ((m = re("science: (.+)", s)) != null) {
                if ("no affordable step".equals(m.group(1))) {
                    return "на шаг по треку не хватает трофеев";
                }
                return "наука: " + m.group(1)
                    .replace("left", "левый трек").replace("middle", "средний трек")
                    .replace("right", "правый трек").replace("->", "→ шаг ");
            }
            return switch (s) {
                case "built nothing" -> "ничего не построено";
                case "combat: no battle" -> "боя не вышло — бить некого";
                default -> s;
            };
        }

        private static java.util.regex.Matcher re(String pattern, String s) {
            java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("^" + pattern + "$").matcher(s);
            return m.matches() ? m : null;
        }

        private static String bld(String code) {
            try {
                return buildingName(code);
            } catch (RuntimeException e) {
                return code;
            }
        }

        /** Значение числового поля карты (0, если нет). */
        private static int num(Map<?, ?> m, String key) {
            Object v = m.get(key);
            return v instanceof Number n ? n.intValue() : 0;
        }

        private static String trimmed(Map<String, Object> ev) {
            Map<String, Object> copy = new LinkedHashMap<>(ev);
            copy.remove("type");
            return copy.toString();
        }
    }

    /** Все жетоны игрока на поле (здания и войска) — для внешних проверок. */
    static List<Token> onField(PlayerState p) {
        List<Token> out = new ArrayList<>();
        out.addAll(p.unitsOnField());
        out.addAll(p.buildingsOnField());
        return out;
    }

    // Подписи зданий/войск перенесены в kelium.report.Labels (14.08.2026,
    // разделение на модули engine/bots/gui): FieldPainter из engine нуждался в
    // тех же подписях, а движок не имеет права зависеть от GUI. Тексты и
    // комментарии дизайнера — там; здесь только делегирование, чтобы не
    // трогать десятки вызовов внутри gui.

    /** Короткий код здания (тот же, что на жетоне в SVG). */
    public static String buildingCode(String typeCode) {
        return kelium.report.Labels.buildingCode(typeCode);
    }

    /** Код здания вместе с уровнем, как он подписывается на жетоне. */
    public static String buildingLabel(String typeCode, Integer level) {
        return kelium.report.Labels.buildingLabel(typeCode, level);
    }

    /** Полное русское название здания (для подсказок и зон игроков). */
    public static String buildingName(String typeCode) {
        return kelium.report.Labels.buildingName(typeCode);
    }

    /** Полное название здания с уровнем: «энергостанция, уровень 3». */
    public static String buildingName(String typeCode, Integer level) {
        return kelium.report.Labels.buildingName(typeCode, level);
    }

    /** Полное русское название рода войск. */
    public static String unitName(String typeCode) {
        return kelium.report.Labels.unitName(typeCode);
    }
}
