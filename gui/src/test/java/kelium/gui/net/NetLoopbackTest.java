package kelium.gui.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.gui.HotSeatWindow;
import kelium.report.Json;
import kelium.report.ReplayRecord;

/**
 * ДВЕ «МАШИНЫ» В ОДНОМ ПРОЦЕССЕ: хост и клиент по localhost доигрывают
 * партию до конца. Сторожится главное:
 * <ul>
 *   <li>партия доходит до конца, с переподключением клиента посреди партии;</li>
 *   <li>клиент НИ РАЗУ не получил чужих закрытых карт — ни в снимках, ни в
 *       строках ленты, ни в вопросах; не получил порядка колод, сида и мыслей
 *       ботов;</li>
 *   <li>сетевая партия — та же партия, что без сети: та же лента решений и
 *       тот же итог.</li>
 * </ul>
 */
class NetLoopbackTest {

    private static final String BOT = "builder:1";

    /** Отвечающий «игрок»: случайный вариант с закреплённым зерном. */
    private static int pick(Random rng, int n) {
        return rng.nextInt(n);
    }

    @Test
    void партияПоСетиДоКонцаБезУтечек() {
        // Сторож: если партия встала, в вывод — стеки всех потоков (где ждём).
        Thread dog = new Thread(() -> {
            try {
                Thread.sleep(Duration.ofMinutes(4).toMillis());
            } catch (InterruptedException e) {
                return;
            }
            Thread.getAllStackTraces().forEach((t, st) -> {
                System.out.println("THREAD " + t.getName());
                for (StackTraceElement el : st) {
                    System.out.println("    at " + el);
                }
            });
        });
        dog.setDaemon(true);
        dog.start();
        try {
            Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), this::play);
        } finally {
            dog.interrupt();
        }
    }

    private void play() throws Exception {
        long seed = 20260926L;
        HotSeatWindow.Options table = new HotSeatWindow.Options(GameConfig.DEFAULT_RULESET, 2, seed,
            List.of("human", "human"), null, null, null, null, null, null, null);

        NetHost host = new NetHost(table, "Хост");
        host.log = s -> { };
        int port = host.listen(0);

        // ---- клиент ----
        List<String> raw = new CopyOnWriteArrayList<>();
        List<Received> got = new CopyOnWriteArrayList<>();
        CountDownLatch welcomed = new CountDownLatch(1);
        CountDownLatch over = new CountDownLatch(1);
        Map<String, Object>[] result = new Map[1];
        Random answerRng = new Random(777);
        AtomicInteger decisions = new AtomicInteger();
        AtomicInteger frameCount = new AtomicInteger();
        AtomicInteger reconnects = new AtomicInteger();
        List<String> errors = new CopyOnWriteArrayList<>();
        NetClient[] client = new NetClient[1];
        client[0] = new NetClient("Друг", new NetClient.Listener() {
            @Override
            public void welcome(int seat, int players, String hostName) {
                welcomed.countDown();
            }

            @Override
            public void reject(String reason) {
                errors.add("отказ: " + reason);
            }

            @Override
            public void record(ReplayRecord part, boolean reset, int from) {
                if (!reset && from != frameCount.get()) {
                    errors.add("кусок не стыкуется: from=" + from + ", есть " + frameCount.get());
                }
                frameCount.set(from + part.frames.size());
            }

            @Override
            public void decide(Map<String, Object> q) {
                int n = decisions.incrementAndGet();
                if (n == 40 && reconnects.get() == 0) {
                    // ОБРЫВ ПОСРЕДИ ПАРТИИ: не отвечаем, переподключаемся —
                    // хост обязан дослать всю запись и этот же вопрос.
                    reconnects.incrementAndGet();
                    try {
                        client[0].reconnect();
                    } catch (Exception e) {
                        errors.add("переподключение: " + e);
                    }
                    return;
                }
                List<?> opts = (List<?>) q.get("options");
                client[0].answer(((Number) q.get("seq")).intValue(), pick(answerRng, opts.size()));
            }

            @Override
            public void over(Map<String, Object> r) {
                result[0] = r;
                over.countDown();
            }

            @Override
            public void error(String text) {
                errors.add(text);
            }
        });
        client[0].rawTap = line -> {
            raw.add(line);
            Object p = Json.parse(line);
            if (p instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mm = (Map<String, Object>) m;
                got.add(new Received(mm, frameCount.get()));
            }
        };
        client[0].connect("127.0.0.1", port);
        assertTrue(welcomed.await(10, TimeUnit.SECONDS), "хост не пустил клиента");
        client[0].ready(true);
        long until = System.currentTimeMillis() + 5000;
        while (host.whyNotStart() != null && System.currentTimeMillis() < until) {
            Thread.sleep(20);
        }
        assertNull(host.whyNotStart(), "стол не готов к старту");

        // ---- хост: партия без окна, своё место — бот ----
        int hostSeat = host.hostSeat();
        Agent hostBot = kelium.agents.BotCatalog.create(BOT, hostSeat,
            new Random(seed * 131 + hostSeat + 1), 2);
        ReplayRecord rec = NetGame.playHosted(host, hostBot);
        assertTrue(over.await(20, TimeUnit.SECONDS), "клиент не узнал о конце партии");
        host.close();
        client[0].close();

        assertTrue(errors.isEmpty(), "ошибки по сети: " + errors);
        assertEquals(1, reconnects.get(), "переподключение не проверилось");
        assertEquals(rec.frames.size(), frameCount.get(), "клиент получил не все кадры");
        assertNotNull(result[0]);
        assertEquals(seed, ((Number) result[0].get("seed")).longValue(),
            "по окончании сид открывается");

        // ---- ни одной чужой закрытой карты ----
        int me = 1 - hostSeat;
        checkNoLeaks(got, rec, me, seed);

        // ---- та же партия без сети ----
        List<Integer> netMoves = new ArrayList<>();
        for (Object o : (List<?>) result[0].get("moves")) {
            netMoves.add(((Number) o).intValue());
        }
        Random localRng = new Random(777);
        List<Integer> localMoves = new ArrayList<>();
        List<String> specs = new ArrayList<>(List.of(BOT, BOT));
        specs.set(me, "human");
        HotSeatWindow.Options local = new HotSeatWindow.Options(GameConfig.DEFAULT_RULESET, 2, seed,
            specs, null, null, null, null, null, null, null);
        ReplayRecord alone = NetGame.play(local, s -> s == me ? new Agent(me, "ответчик") {
            @Override
            public Choice choose(GameState st, List<Choice> options, Map<String, Object> ctx) {
                return options.get(pick(localRng, options.size()));
            }

            @Override
            public boolean specInActionMenu() {
                return true;
            }
        } : null, null, localMoves);
        assertEquals(localMoves, netMoves, "сетевая партия разошлась с той же партией без сети");
        assertEquals(alone.winner, rec.winner);
        assertEquals(alone.rounds, rec.rounds);
        System.out.println("[net] frames " + rec.frames.size() + ", client decisions "
            + decisions.get() + ", rounds " + rec.rounds + ", winner " + rec.winner + ", KB received "
            + raw.stream().mapToLong(String::length).sum() / 1024);
    }

    private record Received(Map<String, Object> msg, int framesBefore) {
    }

    /**
     * Для каждого кадра, пришедшего клиенту: какие карты в этот миг лежали у
     * соперника закрытыми (руки, отложенный приказ, под «Мандатом», закрытые
     * подложенные, колоды) и при этом нигде не были видны клиенту — ни их id,
     * ни названия в этом кадре быть не должно.
     */
    @SuppressWarnings("unchecked")
    private static void checkNoLeaks(List<Received> got, ReplayRecord full, int me, long seed) {
        String seedText = String.valueOf(seed);
        int chunks = 0;
        int decides = 0;
        for (Received r : got) {
            String t = NetProtocol.type(r.msg());
            if (NetProtocol.RECORD.equals(t)) {
                chunks++;
                Map<String, Object> chunk = (Map<String, Object>) r.msg().get("chunk");
                assertEquals(0L, ((Number) chunk.get("seed")).longValue(), "сид ушёл клиенту");
                int from = ((Number) r.msg().get("from")).intValue();
                List<Object> frames = (List<Object>) chunk.get("frames");
                for (int i = 0; i < frames.size(); i++) {
                    Map<String, Object> f = (Map<String, Object>) frames.get(i);
                    int k = from + i;
                    assertTrue(((List<?>) f.get("thoughts")).isEmpty(), "мысли бота ушли клиенту");
                    String text = Json.write(f);
                    String log = String.valueOf(f.get("log"));
                    assertTrue(!log.contains(seedText), "сид в строке ленты: " + log);
                    assertClean(text, log, full, k, k, me, "кадр " + k + " (" + f.get("type") + ")");
                    if (f.get("snap") instanceof Map<?, ?> snap) {
                        for (Object po : (List<Object>) ((Map<String, Object>) snap).get("players")) {
                            Map<String, Object> p = (Map<String, Object>) po;
                            if (((Number) p.get("seat")).intValue() == me) {
                                continue;
                            }
                            for (String hand : List.of("arsHand", "objHand", "ordHand")) {
                                for (Object c : (List<Object>) p.get(hand)) {
                                    assertEquals(Redact.BACK, c, "чужая рука открыта в кадре " + k);
                                }
                            }
                        }
                    }
                }
            } else if (NetProtocol.DECIDE.equals(t)) {
                decides++;
                // Вопрос задан по живому столу — он между последним присланным
                // кадром и следующим. Закрытым в миг вопроса наверняка было то,
                // что закрыто и в том, и в другом.
                int k = Math.max(0, r.framesBefore() - 1);
                String text = Json.write(r.msg());
                assertClean(text, text, full, k, k + 1, me, "вопрос после кадра " + k);
            } else if (!NetProtocol.OVER.equals(t)) {
                assertTrue(!Json.write(r.msg()).contains(seedText), "сид в сообщении " + t);
            }
        }
        assertTrue(chunks > 0 && decides > 0, "клиент не получил ни кадров, ни вопросов");
    }

    private static void assertClean(String json, String words, ReplayRecord full, int k, int k2,
                                    int me, String where) {
        Set<String> visible = new HashSet<>();
        Set<String> hidden = hiddenAt(full, k, me, visible);
        if (k2 != k) {
            hidden.retainAll(hiddenAt(full, k2, me, visible));
        }
        hidden.removeAll(visible);
        Set<String> visibleNames = new HashSet<>();
        for (String v : visible) {
            String n = full.cardNames.get(v);
            if (n != null) {
                visibleNames.add(n);
            }
        }
        for (String id : hidden) {
            int at = json.indexOf("\"" + id + "\"");
            if (at >= 0) {
                fail(where + ": клиенту ушёл id закрытой карты соперника/колоды «" + id + "» … "
                    + json.substring(Math.max(0, at - 160), Math.min(json.length(), at + 60)));
            }
            String name = full.cardNames.get(id);
            if (name != null && name.length() > 3 && !visibleNames.contains(name)
                    && words.contains("«" + name + "»")) {
                fail(where + ": клиенту ушло название закрытой карты «" + name + "»: " + words);
            }
        }
    }

    /** Закрытое от места {@code me} в кадре {@code k}; видимое добавляется в {@code visible}. */
    private static Set<String> hiddenAt(ReplayRecord full, int k, int me, Set<String> visible) {
        ReplayRecord.Snapshot s = full.frames.get(Math.min(k, full.frames.size() - 1)).snapshot;
        Set<String> hidden = new HashSet<>();
        for (ReplayRecord.Player p : s.players) {
            List<String> closed = new ArrayList<>();
            closed.addAll(p.orderHand);
            closed.addAll(p.objectiveHand);
            closed.addAll(p.arsenalHand);
            if (p.orderSetAside != null) {
                closed.add(p.orderSetAside);
            }
            if (p.mandateArsenalCard != null) {
                closed.add(p.mandateArsenalCard);
            }
            for (ReplayRecord.Tucked tu : p.tucked) {
                if (tu.revealed) {
                    visible.add(tu.cardId);
                } else {
                    closed.add(tu.cardId);
                }
            }
            if (p.seat == me) {
                visible.addAll(closed);
            } else {
                hidden.addAll(closed);
            }
            visible.addAll(p.orderPlayed);
            visible.addAll(p.arsenalInstalled);
            visible.addAll(p.superObjectives);
            visible.addAll(p.superDone);
            visible.addAll(p.superArsenal);
        }
        for (ReplayRecord.DeckState d : s.decks.values()) {
            hidden.addAll(d.draw);
            if (!d.discard.isEmpty()) {
                visible.add(d.discard.get(0));
            }
        }
        visible.addAll(s.arsenalDisplay);
        visible.addAll(s.superArsenalOffer.values());
        if (s.market != null) {
            visible.add(s.market);
        }
        hidden.remove(null);
        return hidden;
    }
}
