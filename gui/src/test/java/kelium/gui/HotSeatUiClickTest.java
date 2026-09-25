package kelium.gui;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import kelium.core.InteractiveAgent;
import kelium.core.UndoableAgent;
import kelium.gui.kp.FieldBubbles;

/**
 * ПАРТИЯ ДО ФИНАЛА — ТОЛЬКО ЧЕРЕЗ ТО, ЧТО НАРИСОВАНО НА ЭКРАНЕ.
 *
 * <p>Робот-игрок не зовёт служебный ответ движку: на каждом решении он
 * собирает ОРГАНЫ УПРАВЛЕНИЯ, которые окно показало, — пузыри и карточку
 * вопроса на поле, детали стола игрока и его раскрытые карты, карты вскрытия,
 * дугу постройки, шторку передачи хода — и нажимает один из них. Если у
 * решения движка на экране нет ни одного органа, это тупик интерфейса, и тест
 * падает с именем решения: живой игрок в этом месте застрял бы.
 *
 * <p>Прогоняются разные столы: 2, 3 и 4 места, двое живых за одним экраном,
 * новая раскладка конструктора. Изредка робот жмёт «шаг назад» — откат обязан
 * работать посреди настоящей партии и не ломать её.
 */
class HotSeatUiClickTest {

    private static final long DEADLINE_MS = 6 * 60_000L;
    private static final List<Throwable> EDT_ERRORS = new CopyOnWriteArrayList<>();
    private static Thread.UncaughtExceptionHandler previous;

    @BeforeAll
    static void offscreen() {
        System.setProperty("kelium.gui.offscreen", "true");
        previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> EDT_ERRORS.add(e));
    }

    @AfterAll
    static void restore() {
        Thread.setDefaultUncaughtExceptionHandler(previous);
    }

    @Test
    void двоеПротивБотаНаОбычномПоле() throws Exception {
        play(HotSeatWindow.Options.simple(2, 20260925L, List.of("human", "builder:1")));
    }

    @Test
    void трое() throws Exception {
        play(HotSeatWindow.Options.simple(3, 31L, List.of("human", "builder:1", "builder:1")));
    }

    @Test
    void четверо() throws Exception {
        play(HotSeatWindow.Options.simple(4, 44L,
            List.of("human", "builder:1", "builder:1", "builder:1")));
    }

    @Test
    void двоеЖивыхЗаОднимЭкраном() throws Exception {
        play(HotSeatWindow.Options.simple(3, 913L, List.of("human", "human", "builder:1")));
    }

    @Test
    void противСбалансированногоБота() throws Exception {
        play(HotSeatWindow.Options.simple(2, 555L, List.of("balanced", "human")));
    }

    @Test
    void троеЖивыхИзЧетырёх() throws Exception {
        play(HotSeatWindow.Options.simple(4, 4242L,
            List.of("human", "builder:1", "human", "human")));
    }

    @Test
    void новаяРаскладкаДвоеЖивых() throws Exception {
        Path dir = kelium.dataio.GameConfig.resolveDataRoot(null)
            .resolve("scenarios").resolve("new");
        var layouts = kelium.engine.LayoutLibrary.scanFolder(dir, 2, null);
        if (layouts.isEmpty()) {
            return;
        }
        var e = layouts.get(0);
        var base = HotSeatWindow.Options.simple(2, 8L, List.of("human", "human"));
        play(new HotSeatWindow.Options(base.rulesetId(), 2, 8L, base.seatSpecs(),
            e.id(), e.file(), null, null, null, null, null));
    }

    @Test
    void новаяРаскладкаКонструктора() throws Exception {
        Path dir = kelium.dataio.GameConfig.resolveDataRoot(null)
            .resolve("scenarios").resolve("new");
        var layouts = kelium.engine.LayoutLibrary.scanFolder(dir, 2, null);
        if (layouts.isEmpty()) {
            return;          // новых раскладок на машине нет — проверять нечего
        }
        var e = layouts.get(layouts.size() - 1);
        var base = HotSeatWindow.Options.simple(2, 7L, List.of("human", "builder:1"));
        play(new HotSeatWindow.Options(base.rulesetId(), 2, 7L, base.seatSpecs(),
            e.id(), e.file(), null, null, null, null, null));
    }

    private void play(HotSeatWindow.Options options) throws Exception {
        EDT_ERRORS.clear();
        HotSeatWindow w = new HotSeatWindow(options);
        SwingUtilities.invokeAndWait(w::start);
        Random rnd = new Random(options.seed());
        long deadline = System.currentTimeMillis() + DEADLINE_MS;
        int clicks = 0;
        int undos = 0;
        while (!w.finishedForTest()) {
            if (System.currentTimeMillis() > deadline) {
                fail("партия не доиграна за " + DEADLINE_MS / 1000 + " с; щелчков " + clicks
                    + ", окно: " + w.statusForTest());
            }
            if (!EDT_ERRORS.isEmpty()) {
                fail("ошибка на потоке Swing: " + EDT_ERRORS.get(0), EDT_ERRORS.get(0));
            }
            if (w.failureForTest() != null) {
                fail("партия оборвалась: " + w.failureForTest(), w.failureForTest());
            }
            // Шторка передачи хода — первым делом: под ней ничего не видно.
            boolean[] curtain = {false};
            SwingUtilities.invokeAndWait(() -> {
                if (w.curtain.raised()) {
                    curtain[0] = true;
                    w.curtain.ready();
                }
            });
            if (curtain[0]) {
                continue;
            }
            Integer seat = null;
            InteractiveAgent.PendingDecision d = null;
            for (var entry : w.humansBySeat.entrySet()) {
                InteractiveAgent.PendingDecision p = entry.getValue().pending();
                if (p != null) {
                    seat = entry.getKey();
                    d = p;
                }
            }
            if (d == null || w.catchingUpForTest()) {
                Thread.sleep(10);
                continue;
            }
            // дать окну показать решение
            Thread.sleep(20);
            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() -> { });
            UndoableAgent agent = w.humansBySeat.get(seat);
            if (agent == null || agent.pending() != d) {
                continue;
            }
            String kind = String.valueOf(d.context().get("kind"));
            // Изредка — шаг назад посреди партии.
            if (rnd.nextInt(40) == 0) {
                int s = seat;
                boolean[] did = {false};
                SwingUtilities.invokeAndWait(() -> {
                    if (!w.undoTargets(s).isEmpty()) {
                        w.undoLast();
                        did[0] = true;
                    }
                });
                if (did[0]) {
                    undos++;
                    continue;
                }
            }
            List<Runnable> controls = new ArrayList<>();
            List<Runnable> plays = new ArrayList<>();
            // Окно показывает решение не мгновенно (шторка гаснет, карты
            // выезжают) — даём ему до трёх секунд, прежде чем звать это тупиком.
            long show = System.currentTimeMillis() + 3_000;
            while (true) {
                controls.clear();
                plays.clear();
                SwingUtilities.invokeAndWait(() -> {
                    // шторка могла подняться уже после того, как решение пришло
                    if (w.curtain.raised()) {
                        w.curtain.ready();
                    }
                    collect(w, controls, plays);
                    checkText("заголовок хода", w.statusForTest());
                });
                if (!controls.isEmpty() || System.currentTimeMillis() > show
                        || agent.pending() != d) {
                    break;
                }
                Thread.sleep(30);
            }
            if (agent.pending() != d) {
                continue;
            }
            if (controls.isEmpty()) {
                fail("ТУПИК ИНТЕРФЕЙСА: у решения «" + kind + "» на экране нет ни одного "
                    + "органа управления; вариантов у движка " + d.options().size()
                    + ": " + d.options());
            }
            List<Runnable> pool = !plays.isEmpty() && rnd.nextInt(4) != 0 ? plays : controls;
            Runnable click = pool.get(rnd.nextInt(pool.size()));
            SwingUtilities.invokeAndWait(click);
            clicks++;
            // Щелчок обязан сдвинуть партию: решение принято, движок ушёл дальше.
            long wait = System.currentTimeMillis() + 5_000;
            while (agent.pending() == d && System.currentTimeMillis() < wait
                    && !w.finishedForTest()) {
                Thread.sleep(5);
            }
            if (agent.pending() == d) {
                fail("щелчок по органу решения «" + kind + "» не ответил движку");
            }
        }
        SwingUtilities.invokeAndWait(() -> { });
        assertNull(w.failureForTest(), "партия оборвалась");
        assertTrue(EDT_ERRORS.isEmpty(), "ошибки на потоке Swing: " + EDT_ERRORS);
        assertTrue(clicks > 20, "робот почти не играл: щелчков " + clicks);
        assertTrue(w.rec != null && w.rec.winner != null || w.rec.condition != null,
            "у партии нет итога");
        Path journal = Path.of("reports", "hotseat",
            "hotseat-" + options.seed() + ".kelium-replay.json");
        assertTrue(Files.exists(journal), "журнал не записан: " + journal.toAbsolutePath());
        System.out.println("партия " + options.players() + " мест, сид " + options.seed()
            + ": щелчков " + clicks + ", откатов " + undos + ", раундов " + w.rec.rounds);
        for (String line : w.feedLog) {
            checkText("лента", line);
        }
        // шаги хода — тоже строки на экране (панель «Шаги хода»)
        synchronized (w.moves) {
            for (HotSeatWindow.Decision d : w.decisions) {
                checkText("шаг хода", d.label());
            }
        }
        SwingUtilities.invokeAndWait(() -> w.frame.dispose());
        assertTrue(RAW.isEmpty(), "служебный текст на экране:\n" + String.join("\n", RAW));
    }

    /**
     * ОРГАНЫ УПРАВЛЕНИЯ НА ЭКРАНЕ. {@code plays} — те, что что-то делают (не
     * отказ): робот предпочитает их, чтобы партия не проскочила пасами.
     */
    private static void collect(HotSeatWindow w, List<Runnable> all, List<Runnable> plays) {
        for (FieldBubbles.Opt o : w.field.bubbles.allOptsForTest()) {
            add(o, all, plays);
        }
        for (var e : w.table.choicesForTest().entrySet()) {
            for (FieldBubbles.Opt o : e.getValue()) {
                add(o, all, plays);
            }
        }
        // Карты вскрытия — только пока решение и правда карточное: гаснущая
        // раскладка прошлого вопроса ещё видна, но уже ничего не решает.
        if (w.ceremony.isVisible() && List.of("reveal_order", "blind_discard", "super_pick",
                "start_objective_pick", "arsenal_draw2", "keep_objective", "objective_keep")
                .contains(w.pendingKindForTest())) {
            for (var c : w.ceremony.cards()) {
                checkText("карта выбора", c.title());
                all.add(c.onPick());
                plays.add(c.onPick());
            }
        }
        if (w.field.facingVariants != null && w.field.onFacingPick != null) {
            int n = w.field.facingVariants.size();
            for (int i = 0; i < n; i++) {
                int idx = i;
                Runnable r = () -> w.field.onFacingPick.accept(idx);
                all.add(r);
                plays.add(r);
            }
        }
    }

    /**
     * СЫРОЙ ТЕКСТ НА ЭКРАНЕ (сдача под ключ 25.09.2026): латиница, «@», «_»,
     * скобки списков, «null», стрелки «->» — значит, подпись варианта ушла к
     * игроку служебной строкой движка, а не словами.
     */
    static final java.util.Set<String> RAW = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.regex.Pattern RAW_TEXT = java.util.regex.Pattern.compile(
        "[A-Za-z]{2,}|@|_|\\[|\\]|null|->|\\{");

    static void checkText(String where, String text) {
        if (text != null && RAW_TEXT.matcher(text).find()) {
            RAW.add(where + ": «" + text + "»");
        }
    }

    private static void add(FieldBubbles.Opt o, List<Runnable> all, List<Runnable> plays) {
        if (o.pick() == null) {
            return;
        }
        checkText("вариант", o.label());
        checkText("пояснение варианта", o.sub());
        all.add(o.pick());
        if (o.tone() != 2) {
            plays.add(o.pick());
        }
    }
}
