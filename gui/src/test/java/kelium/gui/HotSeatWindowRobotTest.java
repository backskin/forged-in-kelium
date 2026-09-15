package kelium.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import kelium.core.Choice;
import kelium.core.InteractiveAgent;
import kelium.core.UndoableAgent;

/**
 * РОБОТ ЗА ЭКРАНОМ — целая партия через настоящее окно «Командного пункта».
 *
 * <p>ЗАЧЕМ. Живое окно проверялось только руками: сыграть партию — сотни точек
 * решения, и после каждой правки интерфейса никто их заново не проходил.
 * Ошибка в отрисовке редкой точки решения (высадка, реакция карты, выбор
 * жертвы) всплывала у дизайнера посреди партии. Здесь за живого игрока
 * садится робот: ждёт, пока окно покажет решение, и отвечает ТЕМ ЖЕ путём,
 * что кнопка окна ({@link HotSeatWindow#answerForTest}) — окно при этом
 * рисует каждую точку решения по-настоящему, только за краем экрана.
 *
 * <p>Что сторожится: партия доигрывается до конца; ни движок, ни поток Swing
 * не бросают исключений; журнал партии записан тем же форматом, что у
 * симуляций (заказ «цифровая версия», §3); при двух живых игроках шторка
 * передачи устройства поднимается и принимает ход.
 */
class HotSeatWindowRobotTest {

    /** Самый долгий разумный прогон: партия ботов-новичков идёт секунды. */
    private static final long DEADLINE_MS = 4 * 60_000L;

    private static final List<Throwable> EDT_ERRORS = new CopyOnWriteArrayList<>();
    private static Thread.UncaughtExceptionHandler previous;

    @BeforeAll
    static void offscreenAndCatchEdt() {
        System.setProperty("kelium.gui.offscreen", "true");
        previous = Thread.getDefaultUncaughtExceptionHandler();
        // Исключение на потоке Swing по умолчанию печатается и глотается —
        // тест бы прошёл зелёным поверх сломанного окна.
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> EDT_ERRORS.add(e));
    }

    @AfterAll
    static void restore() {
        Thread.setDefaultUncaughtExceptionHandler(previous);
    }

    @Test
    void одинЖивойПротивБотаДоигрываетПартиюЧерезОкно() throws Exception {
        HotSeatWindow w = play(List.of("human", "builder:1"), 20260913L);
        assertEquals(0, w.mySeatForTest(), "единственный живой подписан «вы»");
    }

    @Test
    void дваЖивыхЗаОднимЭкраномПередаютХодЧерезШторку() throws Exception {
        HotSeatWindow w = play(List.of("human", "human", "builder:1"), 913L);
        assertEquals(-1, w.mySeatForTest(), "при двух живых пометки «вы» нет ни у кого");
        assertTrue(curtains > 0, "шторка передачи устройства ни разу не поднялась");
    }

    private int curtains;

    private HotSeatWindow play(List<String> seats, long seed) throws Exception {
        EDT_ERRORS.clear();
        curtains = 0;
        HotSeatWindow w = new HotSeatWindow(
            HotSeatWindow.Options.simple(seats.size(), seed, seats));
        SwingUtilities.invokeAndWait(w::start);

        Random rnd = new Random(seed);
        long deadline = System.currentTimeMillis() + DEADLINE_MS;
        int answered = 0;
        while (!w.finishedForTest()) {
            if (System.currentTimeMillis() > deadline) {
                fail("партия не доиграна за " + DEADLINE_MS / 1000 + " с; ответов дано "
                    + answered + ", окно говорит: " + w.statusForTest());
            }
            if (!EDT_ERRORS.isEmpty()) {
                fail("ошибка на потоке Swing: " + EDT_ERRORS.get(0), EDT_ERRORS.get(0));
            }
            boolean acted = false;
            for (int seat = 0; seat < seats.size(); seat++) {
                UndoableAgent agent = w.humansBySeat.get(seat);
                if (agent == null) {
                    continue;
                }
                InteractiveAgent.PendingDecision d = agent.pending();
                if (d == null) {
                    continue;
                }
                // Дать окну ПОКАЗАТЬ решение: движок ставит pending и лишь потом
                // шлёт задачу потоку Swing — дождаться её и опустошить очередь.
                Thread.sleep(15);
                SwingUtilities.invokeAndWait(() -> { });
                SwingUtilities.invokeAndWait(() -> {
                    if (w.curtain.raised()) {
                        curtains++;
                        w.curtain.ready();
                    }
                });
                SwingUtilities.invokeAndWait(() -> { });
                if (agent.pending() != d) {
                    continue;    // движок уже ушёл дальше — ответ не нужен
                }
                int idx = pick(rnd, d.options());
                int seatFinal = seat;
                SwingUtilities.invokeAndWait(() -> w.answerForTest(seatFinal, idx));
                answered++;
                acted = true;
            }
            if (!acted) {
                Thread.sleep(10);
            }
        }
        SwingUtilities.invokeAndWait(() -> { });

        Throwable broke = w.failureForTest();
        if (broke != null) {
            fail("партия оборвалась: " + broke, broke);
        }
        assertTrue(EDT_ERRORS.isEmpty(), "ошибки на потоке Swing: " + EDT_ERRORS);
        assertTrue(answered > 20, "робот почти не играл: ответов " + answered);
        assertTrue(w.rec != null && w.rec.rounds >= 1, "журнал партии не собрался");
        assertFalse(w.rec.frames.isEmpty(), "в журнале нет ни одного кадра");
        assertEquals(seats.size(), w.rec.seatLabels.size(), "подпись у каждого места");
        for (int seat = 0; seat < seats.size(); seat++) {
            String label = w.rec.seatLabels.get(seat);
            // Живой в журнале подписан «Игрок N», бот — именем характера и уровня.
            assertTrue("human".equals(seats.get(seat)) ? label.startsWith("Игрок")
                : !label.isBlank() && !label.startsWith("Игрок"),
                "подпись места " + (seat + 1) + " не про того, кто там сидел: " + label);
        }
        Path journal = Path.of("reports", "hotseat", "hotseat-" + seed + ".kelium-replay.json");
        assertTrue(Files.exists(journal), "журнал партии не записан: " + journal.toAbsolutePath());
        // Записанный журнал читается тем же читателем, что записи симуляций.
        var back = kelium.report.ReplayRecord.load(journal);
        assertEquals(w.rec.rounds, back.rounds);
        SwingUtilities.invokeAndWait(() -> w.frame.dispose());
        return w;
    }

    /**
     * Случайный ответ — как у игрока, который тычет во всё подряд. «Пас» тоже
     * в игре, но не чаще прочих: иначе робот проскочит партию, ничего не сыграв.
     */
    private static int pick(Random rnd, List<Choice> options) {
        List<Integer> plays = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).payload() != null) {
                plays.add(i);
            }
        }
        if (plays.isEmpty() || rnd.nextInt(4) == 0) {
            return rnd.nextInt(options.size());
        }
        return plays.get(rnd.nextInt(plays.size()));
    }
}
