package kelium.gui.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Frame;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.dataio.GameConfig;
import kelium.gui.HotSeatWindow;
import kelium.gui.kp.KpButton;
import kelium.report.ReplayRecord;

/**
 * СЕТЬ В НАСТОЯЩИХ ОКНАХ (беззвучно, за краем экрана): хост ведёт партию в
 * окне партии, игрок — в окне клиента.
 * <ul>
 *   <li>отмена клиентом идёт через окно партии хоста (откат переигровкой);</li>
 *   <li>отвал — у хоста шторка с тремя кнопками; «Ждать игрока» оставляет её с
 *       пометкой, возвращение игрока снимает; «Заменить ботом» — партия
 *       доигрывается до конца;</li>
 *   <li>чат из партии виден в окне хоста;</li>
 *   <li>у другого игрока — шторка «вышел из игры», а после закрытия хостом —
 *       «Хост закрыл партию» с кнопкой «Выйти из игры».</li>
 * </ul>
 */
class NetWindowsTest {

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

    private static HotSeatWindow.Options table(int players, long seed) {
        List<String> specs = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            specs.add("human");
        }
        return new HotSeatWindow.Options(GameConfig.DEFAULT_RULESET, players, seed, specs,
            null, null, null, null, null, null, null);
    }

    private static void until(String what, long ms, BooleanSupplier ok) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (!ok.getAsBoolean()) {
            if (System.currentTimeMillis() > end) {
                Assertions.fail("не дождались: " + what);
            }
            Thread.sleep(30);
        }
    }

    private static <T> T onEdt(java.util.function.Supplier<T> s) {
        AtomicReference<T> r = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> r.set(s.get()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return r.get();
    }

    private static JFrame hostFrame() {
        for (Frame f : Frame.getFrames()) {
            if (f instanceof JFrame j && j.isDisplayable()
                    && j.getTitle().contains("Командный пункт")) {
                return j;
            }
        }
        return null;
    }

    private static <T> T layer(JFrame f, Class<T> type) {
        for (Component c : f.getLayeredPane().getComponents()) {
            if (type.isInstance(c)) {
                return type.cast(c);
            }
        }
        return null;
    }

    private static KpButton button(NetOverlay o, String title) {
        for (KpButton b : o.buttonList()) {
            if (title.equals(b.title())) {
                return b;
            }
        }
        return null;
    }

    @Test
    void хостВОкнеПартии_отменаОтвалЖдатьВозвратБотЧат() {
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(6), this::hostWindow);
    }

    private void hostWindow() throws Exception {
        long seed = 20260930L;
        NetHost host = new NetHost(table(2, seed), "Хост");
        host.log = s -> { };
        int port = host.listen(0);

        Random rng = new Random(3);
        AtomicInteger answered = new AtomicInteger();
        AtomicInteger undos = new AtomicInteger();
        AtomicInteger repeats = new AtomicInteger();
        AtomicReference<String> lastSig = new AtomicReference<>();
        AtomicReference<String> expect = new AtomicReference<>();
        AtomicBoolean chatted = new AtomicBoolean();
        CountDownLatch welcomed = new CountDownLatch(1);
        CountDownLatch drop1 = new CountDownLatch(1);
        CountDownLatch drop2 = new CountDownLatch(1);
        List<String> failures = new CopyOnWriteArrayList<>();
        NetClient[] c = new NetClient[1];
        c[0] = new NetClient("Друг", new NetClient.Listener() {
            @Override
            public void welcome(int seat, int players, String hostName) {
                welcomed.countDown();
            }

            @Override
            public void decide(Map<String, Object> q) {
                if (chatted.compareAndSet(false, true)) {
                    c[0].chat("привет из партии");
                }
                int seq = ((Number) q.get("seq")).intValue();
                int undo = q.get("undo") instanceof Number u ? u.intValue() : 0;
                String sig = q.get("kind") + " " + q.get("options");
                String want = expect.getAndSet(null);
                if (want != null) {
                    if (!want.equals(sig)) {
                        failures.add("после отмены другой вопрос: " + want + " / " + sig);
                    }
                    repeats.incrementAndGet();
                } else if (undo > 0 && undos.get() < 2 && lastSig.get() != null) {
                    undos.incrementAndGet();
                    expect.set(lastSig.get());
                    c[0].undo(seq, false);
                    return;
                }
                lastSig.set(sig);
                c[0].answer(seq, rng.nextInt(((List<?>) q.get("options")).size()));
                int n = answered.incrementAndGet();
                if (n == 25) {
                    c[0].drop();
                    drop1.countDown();
                } else if (n == 50) {
                    c[0].drop();
                    drop2.countDown();
                }
            }

            @Override
            public void error(String text) {
                failures.add("ошибка от хоста: " + text);
            }
        });
        c[0].connect("127.0.0.1", port);
        assertTrue(welcomed.await(10, TimeUnit.SECONDS));
        c[0].ready(true);
        until("стол готов", 5000, () -> host.whyNotStart() == null);

        // хост — в окне партии; за его место играет бот (живой — это окно)
        HotSeatWindow.Options o = host.begin();
        List<String> specs = new ArrayList<>(o.seatSpecs());
        specs.set(host.hostSeat(), "builder:1");
        HotSeatWindow.open(new HotSeatWindow.Options(o.rulesetId(), o.players(), o.seed(), specs,
            o.scenarioId(), o.scenarioFile(), o.cuFacing(), o.seatColors(), o.startCoins(),
            o.startKelium(), o.startAmmo(), o.prepRound(), o.marketCards()));
        until("окно партии хоста", 20000, () -> hostFrame() != null);
        JFrame hf = hostFrame();
        until("шторка и чат на окне хоста", 10000,
            () -> onEdt(() -> layer(hf, NetOverlay.class) != null && layer(hf, NetChatDock.class) != null));
        NetOverlay overlay = onEdt(() -> layer(hf, NetOverlay.class));
        NetChatDock chat = onEdt(() -> layer(hf, NetChatDock.class));

        // ---- первый отвал: «Ждать игрока», потом игрок возвращается ----
        assertTrue(drop1.await(120, TimeUnit.SECONDS), "до 25-го решения не дошли");
        until("шторка у хоста", 10000, () -> onEdt(overlay::shown));
        assertEquals(3, onEdt(() -> overlay.buttonList().size()), "у хоста три выхода");
        assertTrue(onEdt(overlay::titleText).contains("вышел из игры"));
        int frozen = host.framesSeen();
        onEdt(() -> {
            button(overlay, "Ждать игрока").click();
            return null;
        });
        until("пометка ожидания", 5000, () -> onEdt(() -> overlay.shown()
            && button(overlay, "Ждать игрока") != null
            && button(overlay, "Ждать игрока").state() == KpButton.State.ACTIVE));
        Thread.sleep(600);
        assertEquals(frozen, host.framesSeen(), "на паузе движок стоит");
        c[0].reconnect();
        until("шторка снята после возвращения", 10000, () -> !onEdt(overlay::shown));

        // ---- второй отвал: «Заменить ботом» — доигрываем ----
        assertTrue(drop2.await(180, TimeUnit.SECONDS), "до 50-го решения не дошли");
        until("шторка у хоста снова", 10000, () -> onEdt(overlay::shown));
        onEdt(() -> {
            button(overlay, "Заменить ботом").click();
            return null;
        });
        until("шторка снята после бота", 10000, () -> !onEdt(overlay::shown));
        until("партия доиграна ботом", 240000, host::finished);
        assertEquals(NetHost.Kind.BOT, host.seats()[1 - host.hostSeat()].kind);

        String history = onEdt(chat::historyText);
        assertTrue(history.contains("Друг: привет из партии"), "чат не дошёл до окна хоста: " + history);
        assertTrue(undos.get() > 0, "отмена клиентом не проверилась");
        assertEquals(undos.get(), repeats.get(), "после отмены вопрос не задан заново");
        assertTrue(failures.isEmpty(), String.valueOf(failures));
        assertTrue(EDT_ERRORS.isEmpty(), "ошибки на потоке окна: " + EDT_ERRORS);
        onEdt(() -> {
            hf.dispose();
            return null;
        });
        host.close();
        c[0].close();
    }

    @Test
    void уДругогоИгрокаШторкаПаузыИЗакрытие() {
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(3), this::otherPlayer);
    }

    private void otherPlayer() throws Exception {
        long seed = 20261001L;
        NetHost host = new NetHost(table(3, seed), "Хост");
        host.log = s -> { };
        int port = host.listen(0);
        Random ra = new Random(1);
        Random rb = new Random(2);
        AtomicInteger n = new AtomicInteger();
        CountDownLatch wa = new CountDownLatch(1);
        CountDownLatch wb = new CountDownLatch(1);
        NetClient[] a = new NetClient[1];
        a[0] = new NetClient("Аня", new NetClient.Listener() {
            @Override
            public void welcome(int seat, int players, String hostName) {
                wa.countDown();
            }

            @Override
            public void decide(Map<String, Object> q) {
                a[0].answer(((Number) q.get("seq")).intValue(),
                    ra.nextInt(((List<?>) q.get("options")).size()));
                if (n.incrementAndGet() == 10) {
                    a[0].drop();
                }
            }
        });
        NetClientWindow[] win = new NetClientWindow[1];
        NetClient[] b = new NetClient[1];
        b[0] = new NetClient("Боря", new NetClient.Listener() {
            @Override
            public void welcome(int seat, int players, String hostName) {
                wb.countDown();
            }

            @Override
            public void start(int seat, List<String> names) {
                win[0] = new NetClientWindow(b[0], seat, names, null);
                win[0].show();
            }

            @Override
            public void record(ReplayRecord part, boolean reset, int from) {
                win[0].record(part, reset, from);
            }

            @Override
            public void decide(Map<String, Object> q) {
                win[0].decide(q);
                b[0].answer(((Number) q.get("seq")).intValue(),
                    rb.nextInt(((List<?>) q.get("options")).size()));
            }

            @Override
            public void paused(int seat, String name, boolean waiting) {
                win[0].paused(seat, name, waiting);
            }

            @Override
            public void resumed(int seat, String how) {
                win[0].resumed(seat, how);
            }

            @Override
            public void closed() {
                win[0].closed();
            }

            @Override
            public void chat(String from, String text) {
                win[0].chat(from + ": " + text);
            }
        });
        a[0].connect("127.0.0.1", port);
        assertTrue(wa.await(10, TimeUnit.SECONDS));
        b[0].connect("127.0.0.1", port);
        assertTrue(wb.await(10, TimeUnit.SECONDS));
        a[0].ready(true);
        b[0].ready(true);
        until("стол готов", 5000, () -> host.whyNotStart() == null);

        int hs = host.hostSeat();
        Agent hostBot = kelium.agents.BotCatalog.create("builder:1", hs,
            new Random(seed * 131 + hs + 1), 3);
        AtomicReference<Object> out = new AtomicReference<>();
        Thread game = new Thread(() -> {
            try {
                out.set(NetGame.playHosted(host, hostBot));
            } catch (Throwable e) {
                out.set(e);
            }
        });
        game.setDaemon(true);
        game.start();

        until("шторка паузы у другого игрока", 90000, () -> win[0] != null
            && onEdt(() -> win[0].overlayTitle()) != null);
        String t = onEdt(() -> win[0].overlayTitle());
        assertTrue(t.startsWith("Игрок " + (a[0].seat() + 1)) && t.contains("вышел из игры"), t);

        // чат во время паузы — доходит до окна игрока
        host.say("ждём Аню");
        until("чат в окне игрока", 5000, () -> onEdt(() -> {
            NetChatDock d = layer(winFrame(win[0]), NetChatDock.class);
            return d != null && d.historyText().contains("Хост: ждём Аню");
        }));

        host.closeGame();
        until("«Хост закрыл партию» у игрока", 10000,
            () -> "Хост закрыл партию".equals(onEdt(() -> win[0].overlayTitle())));
        game.join(20000);
        assertTrue(out.get() instanceof kelium.core.GameAborted, String.valueOf(out.get()));
        assertTrue(EDT_ERRORS.isEmpty(), "ошибки на потоке окна: " + EDT_ERRORS);
        JFrame f = winFrame(win[0]);
        assertNotNull(f);
        onEdt(() -> {
            f.dispose();
            return null;
        });
    }

    private static JFrame winFrame(NetClientWindow w) {
        for (Frame f : Frame.getFrames()) {
            if (f instanceof JFrame j && j.isDisplayable()
                    && j.getTitle().contains("сетевая партия")) {
                return j;
            }
        }
        return null;
    }
}
