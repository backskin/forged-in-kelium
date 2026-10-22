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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.dataio.GameConfig;
import kelium.gui.HotSeatWindow;
import kelium.report.ReplayRecord;

/**
 * СЕТЕВАЯ ПАРТИЯ ПО РЕШЕНИЯМ ВЛАДА (26.09.2026): хост и клиенты в одном
 * процессе по localhost.
 * <ul>
 *   <li>отвал игрока — пауза у хоста (движок стоит), «ждать» оставляет паузу,
 *       «боту» — партия доигрывается ботом на его месте;</li>
 *   <li>хост закрыл партию — остальные игроки получают паузу, затем «партия
 *       закрыта»;</li>
 *   <li>клиент отменяет своё решение — стол переигрывается, и ему заново
 *       задан тот же вопрос; отмена после чужого решения не проходит;</li>
 *   <li>чат ходит в обе стороны посреди партии.</li>
 * </ul>
 */
class NetSessionTest {

    private static final String BOT = "builder:1";

    private static HotSeatWindow.Options table(int players, long seed) {
        List<String> specs = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            specs.add("human");
        }
        return new HotSeatWindow.Options(GameConfig.DEFAULT_RULESET, players, seed, specs,
            null, null, null, null, null, null, null);
    }

    /** Клиент-«игрок»: на вопрос отвечает {@code onDecide}, прочее — в списки. */
    private static final class Player implements NetClient.Listener {
        final NetClient client;
        final CountDownLatch welcomed = new CountDownLatch(1);
        final CountDownLatch over = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
        final List<String> chat = new CopyOnWriteArrayList<>();
        final List<String> errors = new CopyOnWriteArrayList<>();
        final List<int[]> pauses = new CopyOnWriteArrayList<>();
        final List<String> resumes = new CopyOnWriteArrayList<>();
        final AtomicInteger resets = new AtomicInteger();
        volatile BiConsumer<Player, Map<String, Object>> onDecide;

        Player(String name) {
            client = new NetClient(name, this);
        }

        void answer(Map<String, Object> q, int i) {
            client.answer(((Number) q.get("seq")).intValue(), i);
        }

        static int size(Map<String, Object> q) {
            return ((List<?>) q.get("options")).size();
        }

        @Override
        public void welcome(int seat, int players, String hostName) {
            welcomed.countDown();
        }

        @Override
        public void record(ReplayRecord part, boolean reset, int from) {
            if (reset) {
                resets.incrementAndGet();
            }
        }

        @Override
        public void decide(Map<String, Object> q) {
            BiConsumer<Player, Map<String, Object>> d = onDecide;
            if (d != null) {
                d.accept(this, q);
            }
        }

        @Override
        public void chat(String from, String text) {
            chat.add(from + ": " + text);
        }

        @Override
        public void paused(int seat, String name, boolean waiting) {
            pauses.add(new int[]{seat, waiting ? 1 : 0});
        }

        @Override
        public void resumed(int seat, String how) {
            resumes.add(seat + ":" + how);
        }

        @Override
        public void closed() {
            closed.countDown();
        }

        @Override
        public void over(Map<String, Object> r) {
            over.countDown();
        }

        @Override
        public void error(String text) {
            errors.add(text);
        }

        @Override
        public void reject(String reason) {
            errors.add("отказ: " + reason);
        }
    }

    /** Хост с клиентами за столом, все готовы. */
    private static NetHost host(HotSeatWindow.Options t, List<Player> players) throws Exception {
        NetHost host = new NetHost(t, "Хост");
        host.log = s -> { };
        int port = host.listen(0);
        for (Player p : players) {
            p.client.connect("127.0.0.1", port);
            assertTrue(p.welcomed.await(10, TimeUnit.SECONDS), "хост не пустил клиента");
            p.client.ready(true);
        }
        long until = System.currentTimeMillis() + 5000;
        while (host.whyNotStart() != null && System.currentTimeMillis() < until) {
            Thread.sleep(20);
        }
        assertEquals(null, host.whyNotStart(), "стол не готов к старту");
        return host;
    }

    /** Партия у хоста на своём потоке: итог или исключение. */
    private static Thread run(NetHost host, long seed, AtomicReference<Object> out) {
        int hs = host.hostSeat();
        Agent hostBot = kelium.agents.BotCatalog.create(BOT, hs, new Random(seed * 131 + hs + 1),
            host.seats().length);
        Thread t = new Thread(() -> {
            try {
                out.set(NetGame.playHosted(host, hostBot));
            } catch (Throwable e) {
                out.set(e);
            }
        }, "test-host-game");
        t.setDaemon(true);
        t.start();
        return t;
    }

    @Test
    void правилоОтменыНеГлубжеЧужого() {
        List<NetSeats.Step> s = List.of(new NetSeats.Step(0, 1, 1), new NetSeats.Step(1, 1, 1),
            new NetSeats.Step(1, 1, 1));
        assertEquals(List.of(1, 2), NetSeats.undoTargets(s, 1, 1, 1));
        s = List.of(new NetSeats.Step(1, 1, 1), new NetSeats.Step(0, 1, 1),
            new NetSeats.Step(1, 1, 1));
        assertEquals(List.of(2), NetSeats.undoTargets(s, 1, 1, 1), "чужое решение запекает");
        s = List.of(new NetSeats.Step(1, 1, 1), new NetSeats.Step(1, 1, 2));
        assertEquals(List.of(1), NetSeats.undoTargets(s, 1, 1, 2), "прошлый круг не отменить");
        s = List.of(new NetSeats.Step(1, 1, 1), new NetSeats.Step(0, 1, 1));
        assertEquals(List.of(), NetSeats.undoTargets(s, 1, 1, 1));
    }

    @Test
    void отвалКлиентаПаузаЗатемБотДоигрывает() {
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(4), () -> {
            long seed = 20260927L;
            Player p = new Player("Друг");
            Random rng = new Random(5);
            AtomicInteger n = new AtomicInteger();
            p.onDecide = (me, q) -> {
                me.answer(q, rng.nextInt(Player.size(q)));
                if (n.incrementAndGet() == 15) {
                    me.client.drop();          // ответил — и пропала сеть
                }
            };
            NetHost host = host(table(2, seed), List.of(p));
            CountDownLatch pause = new CountDownLatch(1);
            host.pauseListeners.add(() -> {
                if (host.awaySeat() != null) {
                    pause.countDown();
                }
            });
            AtomicReference<Object> out = new AtomicReference<>();
            Thread game = run(host, seed, out);
            assertTrue(pause.await(60, TimeUnit.SECONDS), "хост не получил паузы");
            int seat = 1 - host.hostSeat();
            assertEquals(seat, host.awaySeat());

            // ПАУЗА: движок стоит — новых кадров нет
            Thread.sleep(500);
            int f = host.framesSeen();
            Thread.sleep(900);
            assertEquals(f, host.framesSeen(), "партия на паузе, а кадры идут");

            // «Ждать» — пауза остаётся, с пометкой ожидания
            host.waitFor(seat);
            assertTrue(host.waitingFor(seat));
            Thread.sleep(400);
            assertEquals(f, host.framesSeen(), "«ждать» не должно снимать паузу");

            // «Заменить ботом» — партия доигрывается
            host.replaceWithBot(seat);
            game.join(Duration.ofMinutes(3).toMillis());
            host.close();
            assertTrue(out.get() instanceof ReplayRecord, "партия не доиграна: " + out.get());
            ReplayRecord rec = (ReplayRecord) out.get();
            assertTrue(rec.rounds > 0);
            assertEquals(NetHost.Kind.BOT, host.seats()[seat].kind);
            assertEquals(null, host.awaySeat());
            assertTrue(host.framesSeen() > f, "после замены ботом партия не пошла");
            System.out.println("[net] отвал на решении 15, бот доиграл: раундов " + rec.rounds
                + ", победил " + rec.winner);
        });
    }

    @Test
    void хостЗакрылПартиюИгрокиУзнают() {
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(3), () -> {
            long seed = 20260928L;
            Player a = new Player("Аня");
            Player b = new Player("Боря");
            Random ra = new Random(1);
            Random rb = new Random(2);
            AtomicInteger n = new AtomicInteger();
            a.onDecide = (me, q) -> {
                me.answer(q, ra.nextInt(Player.size(q)));
                if (n.incrementAndGet() == 10) {
                    me.client.drop();
                }
            };
            b.onDecide = (me, q) -> me.answer(q, rb.nextInt(Player.size(q)));
            NetHost host = host(table(3, seed), List.of(a, b));
            int aSeat = a.client.seat();
            CountDownLatch pause = new CountDownLatch(1);
            host.pauseListeners.add(() -> {
                if (host.awaySeat() != null) {
                    pause.countDown();
                }
            });
            AtomicReference<Object> out = new AtomicReference<>();
            Thread game = run(host, seed, out);
            assertTrue(pause.await(90, TimeUnit.SECONDS), "хост не получил паузы");
            long until = System.currentTimeMillis() + 5000;
            while (b.pauses.isEmpty() && System.currentTimeMillis() < until) {
                Thread.sleep(20);
            }
            assertTrue(!b.pauses.isEmpty(), "второй игрок не узнал о паузе");
            assertEquals(aSeat, b.pauses.get(0)[0], "пауза не по тому месту");

            host.closeGame();
            assertTrue(b.closed.await(10, TimeUnit.SECONDS), "игрок не узнал, что хост закрыл партию");
            game.join(20000);
            assertTrue(out.get() instanceof kelium.core.GameAborted,
                "партия у хоста не разомкнулась: " + out.get());
            assertTrue(host.closedByHost());
            b.client.close();
        });
    }

    @Test
    void клиентОтменяетСвоёРешениеИЧатВПартии() {
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(4), () -> {
            long seed = 20260929L;
            Player p = new Player("Друг");
            Random rng = new Random(9);
            AtomicReference<String> lastAnswered = new AtomicReference<>();
            AtomicReference<String> expect = new AtomicReference<>();
            AtomicBoolean refusedTried = new AtomicBoolean();
            AtomicInteger refusedSeq = new AtomicInteger(-1);
            AtomicInteger undos = new AtomicInteger();
            AtomicInteger repeats = new AtomicInteger();
            AtomicInteger resetsAtUndo = new AtomicInteger();
            List<String> failures = new CopyOnWriteArrayList<>();
            AtomicBoolean chatted = new AtomicBoolean();
            p.onDecide = (me, q) -> {
                if (chatted.compareAndSet(false, true)) {
                    me.client.chat("привет из партии");
                }
                int seq = ((Number) q.get("seq")).intValue();
                int undo = q.get("undo") instanceof Number u ? u.intValue() : 0;
                String sig = q.get("kind") + " " + q.get("options");
                String want = expect.getAndSet(null);
                if (want != null) {
                    // после отмены тот же вопрос задан заново
                    if (!want.equals(sig)) {
                        failures.add("после отмены другой вопрос: ждали " + want + ", пришёл " + sig);
                    }
                    if (p.resets.get() <= resetsAtUndo.get()) {
                        failures.add("после отмены запись не пришла заново");
                    }
                    repeats.incrementAndGet();
                } else if (undo == 0 && !refusedTried.get() && lastAnswered.get() != null) {
                    // отмена без своих решений в круге — отказ, вопрос тот же
                    refusedTried.set(true);
                    refusedSeq.set(seq);
                    me.client.undo(seq, false);
                    return;
                } else if (undo > 0 && undos.get() < 3 && lastAnswered.get() != null) {
                    undos.incrementAndGet();
                    expect.set(lastAnswered.get());
                    resetsAtUndo.set(p.resets.get());
                    me.client.undo(seq, false);
                    return;
                }
                lastAnswered.set(sig);
                me.answer(q, rng.nextInt(Player.size(q)));
            };
            NetHost host = host(table(2, seed), List.of(p));
            List<String> hostChat = new CopyOnWriteArrayList<>();
            host.chatListeners.add(line -> {
                hostChat.add(line);
                if (line.startsWith("Друг:")) {
                    host.say("принято");
                }
            });
            AtomicReference<Object> out = new AtomicReference<>();
            Thread game = run(host, seed, out);
            game.join(Duration.ofMinutes(3).toMillis());
            assertTrue(out.get() instanceof ReplayRecord, "партия не доиграна: " + out.get());
            assertTrue(p.over.await(20, TimeUnit.SECONDS), "клиент не узнал о конце партии");
            host.close();
            p.client.close();

            assertTrue(failures.isEmpty(), String.valueOf(failures));
            assertTrue(undos.get() > 0, "не нашлось вопроса, где можно отменить");
            assertEquals(undos.get(), repeats.get(), "не на каждую отмену вопрос задан заново");
            assertTrue(refusedTried.get(), "отказ в отмене не проверился");
            assertTrue(p.errors.stream().anyMatch(e -> e.startsWith("нечего отменять")),
                "отмена без своих решений не отклонена: " + p.errors);
            assertTrue(hostChat.contains("Друг: привет из партии"), "чат клиента не дошёл: " + hostChat);
            assertTrue(p.chat.contains("Хост: принято"), "чат хоста не дошёл: " + p.chat);
            assertNotNull(out.get());
            System.out.println("[net] отмен клиентом: " + undos.get() + ", раундов "
                + ((ReplayRecord) out.get()).rounds);
        });
    }
}
