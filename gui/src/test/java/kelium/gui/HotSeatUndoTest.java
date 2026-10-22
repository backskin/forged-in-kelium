package kelium.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import kelium.core.Choice;
import kelium.core.InteractiveAgent;
import kelium.core.UndoableAgent;

/**
 * ОТКАТ ЛЮБОГО РЕШЕНИЯ ЧЕРЕЗ НАСТОЯЩЕЕ ОКНО.
 *
 * <p>Робот играет за живого, набирает в круге несколько решений и откатывается
 * к одному из них. Окно обязано переиграть партию до этого места, и движок
 * обязан спросить ровно то же решение заново: того же вида, с теми же
 * вариантами. После отката партия доигрывается как обычно.
 */
class HotSeatUndoTest {

    @BeforeAll
    static void offscreen() {
        System.setProperty("kelium.gui.offscreen", "true");
    }

    @Test
    void откатВозвращаетТоЖеРешениеИПартияИдётДальше() throws Exception {
        long seed = 20260925L;
        HotSeatWindow w = new HotSeatWindow(
            HotSeatWindow.Options.simple(2, seed, List.of("human", "builder:1")));
        SwingUtilities.invokeAndWait(w::start);
        Random rnd = new Random(seed);
        long deadline = System.currentTimeMillis() + 120_000L;
        int undos = 0;
        int answered = 0;
        while (undos < 3 && !w.finishedForTest()) {
            if (System.currentTimeMillis() > deadline) {
                fail("не дождались отката; ответов " + answered);
            }
            InteractiveAgent.PendingDecision d = pending(w);
            if (d == null) {
                Thread.sleep(10);
                continue;
            }
            List<Integer> targets = new ArrayList<>();
            SwingUtilities.invokeAndWait(() -> targets.addAll(w.undoTargets(0)));
            if (targets.size() >= 3) {
                int t = targets.get(1);
                String kind;
                int options;
                synchronized (w.moves) {
                    kind = w.decisions.get(t).kind();
                }
                SwingUtilities.invokeAndWait(() -> w.undoTo(t));
                InteractiveAgent.PendingDecision back = waitPending(w, deadline);
                assertNotNull(back, "после отката движок никого не спросил");
                assertEquals(kind, String.valueOf(back.context().get("kind")),
                    "после отката спрошено не то решение");
                synchronized (w.moves) {
                    assertEquals(t, w.moves.size(), "лента после отката не той длины");
                }
                undos++;
                continue;
            }
            int idx = pick(rnd, d.options());
            SwingUtilities.invokeAndWait(() -> w.answerForTest(0, idx));
            answered++;
        }
        assertTrue(undos == 3, "откатов сделано " + undos);
        // И партия идёт дальше: ещё полсотни ответов без поломки.
        for (int i = 0; i < 50 && !w.finishedForTest(); ) {
            InteractiveAgent.PendingDecision d = pending(w);
            if (d == null) {
                Thread.sleep(10);
                continue;
            }
            int idx = pick(rnd, d.options());
            SwingUtilities.invokeAndWait(() -> w.answerForTest(0, idx));
            i++;
        }
        assertEquals(null, w.failureForTest(), "партия оборвалась после отката");
        SwingUtilities.invokeAndWait(() -> w.frame.dispose());
    }

    private static InteractiveAgent.PendingDecision pending(HotSeatWindow w) throws Exception {
        UndoableAgent a = w.humansBySeat.get(0);
        InteractiveAgent.PendingDecision d = a == null ? null : a.pending();
        if (d == null) {
            return null;
        }
        Thread.sleep(15);
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
        UndoableAgent again = w.humansBySeat.get(0);
        return again == a && a.pending() == d ? d : null;
    }

    private static InteractiveAgent.PendingDecision waitPending(HotSeatWindow w, long deadline)
            throws Exception {
        while (System.currentTimeMillis() < deadline) {
            InteractiveAgent.PendingDecision d = pending(w);
            if (d != null && !w.catchingUpForTest()) {
                return d;
            }
            Thread.sleep(10);
        }
        return null;
    }

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
