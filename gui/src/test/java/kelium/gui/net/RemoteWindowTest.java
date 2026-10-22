package kelium.gui.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.dataio.GameConfig;
import kelium.gui.HotSeatWindow;
import kelium.report.ReplayRecord;

/**
 * ДРУГ ИГРАЕТ В НАСТОЯЩЕМ ОКНЕ ПАРТИИ (29.09.2026). Хост по localhost — без
 * окна, его место играет бот; друг — в окне партии в режиме друга, отвечает
 * через окно, как щелчком. Партия обязана дойти до конца, поток окна — без
 * единого исключения, итог — виден в окне друга.
 */
class RemoteWindowTest {

    @Test
    void другДоигрываетПартиюВОкнеПартии() {
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(6), this::play);
    }

    private void play() throws Exception {
        System.setProperty("kelium.gui.offscreen", "true");
        List<Throwable> crashes = new CopyOnWriteArrayList<>();
        Thread.UncaughtExceptionHandler was = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> crashes.add(e));
        long seed = 20260929L;
        List<String> specs = List.of("human", "human");
        NetHost host = new NetHost(new HotSeatWindow.Options(GameConfig.DEFAULT_RULESET, 2, seed,
            specs, null, null, null, null, null, null, null), "Хост");
        host.log = s -> { };
        int port = host.listen(0);

        AtomicReference<RemoteGame> game = new AtomicReference<>();
        CountDownLatch welcomed = new CountDownLatch(1);
        CountDownLatch over = new CountDownLatch(1);
        NetClient[] c = new NetClient[1];
        c[0] = new NetClient("Друг", new NetClient.Listener() {
            @Override
            public void welcome(int seat, int players, String hostName) {
                welcomed.countDown();
            }

            @Override
            public void start(int seat, List<String> names) {
                game.set(new RemoteGame(c[0], seat, names, () -> { }));
            }

            @Override
            public void record(ReplayRecord part, boolean reset, int from) {
                game.get().record(part, reset, from);
            }

            @Override
            public void decide(Map<String, Object> q) {
                game.get().decide(q);
            }

            @Override
            public void over(Map<String, Object> r) {
                game.get().over(r);
                over.countDown();
            }
        });
        c[0].connect("127.0.0.1", port);
        assertTrue(welcomed.await(10, TimeUnit.SECONDS), "хост не пустил друга");
        c[0].ready(true);
        long until = System.currentTimeMillis() + 5000;
        while (host.whyNotStart() != null && System.currentTimeMillis() < until) {
            Thread.sleep(20);
        }
        int hs = host.hostSeat();
        Agent hostBot = kelium.agents.BotCatalog.create("builder:1", hs,
            new Random(seed * 131 + hs + 1), 2);
        AtomicReference<Object> result = new AtomicReference<>();
        Thread hostGame = new Thread(() -> {
            try {
                result.set(NetGame.playHosted(host, hostBot));
            } catch (Throwable e) {
                result.set(e);
            }
        }, "test-host-game");
        hostGame.setDaemon(true);
        hostGame.start();

        Random rnd = new Random(7);
        int answered = 0;
        while (over.getCount() > 0) {
            RemoteGame g = game.get();
            HotSeatWindow w = g == null ? null : g.window();
            int n = w == null ? 0 : w.remotePendingForTest();
            if (n == 0) {
                Thread.sleep(10);
                continue;
            }
            Thread.sleep(15);
            SwingUtilities.invokeAndWait(() -> { });
            if (w.remotePendingForTest() != n) {
                continue;
            }
            w.remoteAnswerForTest(rnd.nextInt(n));
            answered++;
        }
        hostGame.join(20_000);
        Thread.sleep(500);
        SwingUtilities.invokeAndWait(() -> { });
        HotSeatWindow w = game.get().window();
        assertNotNull(w, "окно друга не открылось");
        String status = w.remoteStatusForTest();
        SwingUtilities.invokeAndWait(() -> w.frameForTest().dispose());
        Thread.setDefaultUncaughtExceptionHandler(was);
        assertTrue(result.get() instanceof ReplayRecord, "партия у хоста не доиграна: " + result.get());
        assertTrue(answered > 50, "друг ответил мало: " + answered);
        assertTrue(status.contains("Партия окончена"), "окно друга не показало итог: " + status);
        List<String> why = new ArrayList<>();
        for (Throwable t : crashes) {
            why.add(String.valueOf(t));
        }
        assertEquals(List.of(), why, "исключения в окне друга");
    }
}
