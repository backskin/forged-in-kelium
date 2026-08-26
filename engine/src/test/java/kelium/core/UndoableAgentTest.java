package kelium.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import kelium.support.Fix;

/**
 * {@link UndoableAgent} — та же проверка, что и в живой игре, но без Swing и
 * без полного {@code GameEngine.playGame}: сам {@link Agent#choose} вызывается
 * вручную (как это бы сделал {@code GameEngine.playActions}), а точки решения
 * отвечаются с ДРУГОГО потока через {@link InteractiveAgent#submitIndex} — так
 * же, как это делает GUI. {@link #choose} блокирует, поэтому каждый вызов идёт
 * на отдельном потоке ({@link CompletableFuture}), а тест поллит {@code
 * pending()} перед тем, как отвечать — тот же паттерн, что уже был проверен в
 * {@code HotSeatWindow}/{@code HotSeatCli}.
 */
class UndoableAgentTest {

    @Test
    void snapshotsBeforeSafeActionAndRestoresOnUndo() throws Exception {
        GameState state = Fix.game(2, 1L);
        PlayerState p = state.player(0);
        p.resources.add(Resource.COIN, 20);
        int coinBefore = p.resources.coin();

        AtomicReference<InteractiveAgent.PendingDecision> lastDecision = new AtomicReference<>();
        UndoableAgent agent = new UndoableAgent(0, "test", state,
            lastDecision::set, ev -> { });

        // 1) движок спрашивает "какое действие" — отвечаем "build".
        List<Choice> actionOpts = List.of(
            new Choice("action", "build", "build"),
            new Choice("pass", null, "ничего не делать"));
        Choice picked = chooseOnOtherThread(agent, state, actionOpts,
            Map.of("kind", "action"), 0);
        assertEquals("build", picked.payload());

        // Снимок снят СРАЗУ по выбору безопасного действия — до того, как оно
        // реально что-то изменит (см. javadoc UndoableAgent.choose).
        assertTrue(agent.canUndo());
        assertEquals("build", agent.undoLabel());

        // 2) само действие (в реальности — build_pick/build_hex/build_facing)
        // тут просто симулируется прямой правкой состояния, как сделал бы
        // Actions.java.
        p.resources.pay(Resource.COIN, 6);
        assertEquals(coinBefore - 6, p.resources.coin());

        // 3) движок снова спрашивает "какое действие" — ДО ответа игрок жмёт
        // "отменить": решение ещё висит нерешённым, undo() можно звать сразу.
        List<Choice> actionOpts2 = List.of(
            new Choice("action", "mining", "mining"),
            new Choice("pass", null, "ничего не делать"));
        CompletableFuture<Choice> future2 = new CompletableFuture<>();
        Thread t = new Thread(() -> {
            try {
                future2.complete(agent.choose(state, actionOpts2, Map.of("kind", "action")));
            } catch (Throwable ex) {
                future2.completeExceptionally(ex);
            }
        });
        t.start();
        waitForPending(agent);

        agent.undo();
        assertEquals(coinBefore, p.resources.coin());
        assertFalse(agent.canUndo());
        assertNull(agent.undoLabel());

        // Точка решения всё ещё ждёт ответа — отвечаем "pass", чтобы поток не
        // повис навсегда, и завершаем.
        agent.submitIndex(1);
        Choice picked2 = future2.get(5, TimeUnit.SECONDS);
        assertNull(picked2.payload());
    }

    @Test
    void nonSafeActionsAreNeverUndoable() throws Exception {
        GameState state = Fix.game(2, 2L);
        UndoableAgent agent = new UndoableAgent(0, "test", state, d -> { }, ev -> { });

        List<Choice> opts = List.of(
            new Choice("action", "combat", "combat"),
            new Choice("pass", null, "ничего не делать"));
        chooseOnOtherThread(agent, state, opts, Map.of("kind", "action"), 0);

        assertFalse(agent.canUndo());
    }

    @Test
    void undoWithoutASnapshotThrows() {
        GameState state = Fix.game(2, 3L);
        UndoableAgent agent = new UndoableAgent(0, "test", state, d -> { }, ev -> { });
        assertThrows(IllegalStateException.class, agent::undo);
    }

    /** СТЕК точек: откат «до точки» возвращает состояние ПЕРЕД ней (концепт §5). */
    @Test
    void undoToEarliestPointRewindsAcrossSeveralActions() throws Exception {
        GameState state = Fix.game(2, 4L);
        PlayerState p = state.player(0);
        p.resources.add(Resource.COIN, 20);
        int coinStart = p.resources.coin();

        UndoableAgent agent = new UndoableAgent(0, "test", state, d -> { }, ev -> { });

        chooseAction(agent, state, "build");      // точка 0 — перед стройкой
        p.resources.pay(Resource.COIN, 3);        // «стройка» что-то потратила
        chooseAction(agent, state, "mining");     // точка 1 — перед добычей
        p.resources.pay(Resource.COIN, 5);
        chooseAction(agent, state, "movement");   // точка 2
        p.resources.pay(Resource.COIN, 2);

        assertEquals(List.of("build", "mining", "movement"), agent.checkpointLabels());
        agent.undoTo(0);
        assertEquals(coinStart, p.resources.coin());
        assertFalse(agent.canUndo());
    }

    /**
     * Запекает не ВЫБОР боя, а ПЕРВЫЙ ЗАЛП: вход в Бой с выходом пасом ничего
     * необратимого не совершил — точки живы (уточнение к концепту §5).
     */
    @Test
    void combatBakesOnFirstShotNotOnMenuPick() throws Exception {
        GameState state = Fix.game(2, 5L);
        UndoableAgent agent = new UndoableAgent(0, "test", state, d -> { }, ev -> { });
        chooseAction(agent, state, "build");
        chooseAction(agent, state, "mining");
        chooseAction(agent, state, "combat");
        assertEquals(2, agent.checkpointLabels().size(),
            "выбор Боя в меню сам по себе ничего не запекает");

        // «пас» в меню залпов — бой отменён, точки живы
        List<Choice> passOnly = List.of(
            new Choice("attack", Map.of("uid", 1, "row", "r", "ammo", 1, "tcat", "units"),
                "залп"),
            new Choice("pass", null, "stop attacking"));
        chooseOnOtherThread(agent, state, passOnly, Map.of("kind", "attack"), 1);
        assertEquals(2, agent.checkpointLabels().size(), "пас по залпам не запекает");

        // настоящий залп — всё запеклось
        chooseOnOtherThread(agent, state, passOnly, Map.of("kind", "attack"), 0);
        assertFalse(agent.canUndo());
    }

    /** Памятка хода: откат возвращает и счётчик сыгранного, и список действий. */
    @Test
    void undoRestoresTurnContextThroughMemento() throws Exception {
        GameState state = Fix.game(2, 6L);
        kelium.engine.TurnContext ctx = new kelium.engine.TurnContext(0, 1);
        state.turnUndo = ctx;
        UndoableAgent agent = new UndoableAgent(0, "test", state, d -> { }, ev -> { });

        chooseAction(agent, state, "build");      // памятка: сыграно 0, пусто
        ctx.actionsPlayed.add("build");
        ctx.playedCount = 1;
        chooseAction(agent, state, "mining");     // памятка: сыграно 1, {build}
        ctx.actionsPlayed.add("mining");
        ctx.playedCount = 2;

        agent.undoTo(1);                          // перед добычей
        assertEquals(1, ctx.playedCount);
        assertEquals(java.util.Set.of("build"), ctx.actionsPlayed);

        agent.undoTo(0);                          // перед стройкой
        assertEquals(0, ctx.playedCount);
        assertTrue(ctx.actionsPlayed.isEmpty());
    }

    private static void chooseAction(UndoableAgent agent, GameState state, String name)
            throws Exception {
        List<Choice> opts = List.of(
            new Choice("action", name, name),
            new Choice("pass", null, "ничего не делать"));
        chooseOnOtherThread(agent, state, opts, Map.of("kind", "action"), 0);
    }

    /** Вызвать choose() на отдельном потоке и ответить индексом {@code answer}. */
    private static Choice chooseOnOtherThread(UndoableAgent agent, GameState state,
                                                List<Choice> options, Map<String, Object> ctx,
                                                int answer) throws Exception {
        CompletableFuture<Choice> future = new CompletableFuture<>();
        Thread t = new Thread(() -> {
            try {
                future.complete(agent.choose(state, options, ctx));
            } catch (Throwable ex) {
                future.completeExceptionally(ex);
            }
        });
        t.start();
        waitForPending(agent);
        agent.submitIndex(answer);
        return future.get(5, TimeUnit.SECONDS);
    }

    private static void waitForPending(UndoableAgent agent) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (agent.pending() == null) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("точка решения не появилась за 5с");
            }
            Thread.sleep(5);
        }
    }
}
