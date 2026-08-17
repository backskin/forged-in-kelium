package kelium.gui.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.InteractiveAgent;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;
import kelium.gui.GameRecorder;
import kelium.observe.PublicView;
import kelium.report.Json;
import kelium.report.ReplayRecord;

/**
 * Одна партия-сессия: настройки, места (бот/человек/свободно), и — после того
 * как заполнены все места — сама партия на отдельном потоке.
 *
 * <p>Авторитетность (заказ §4.4): партия строится и играется ЗДЕСЬ, на сервере;
 * подключённый клиент только получает вид {@link PublicView} и присылает номер
 * выбранной опции — как и в консольном {@code HotSeatCli}, но по сети вместо
 * локальных потоков.
 */
final class Lobby {

    private enum Kind { OPEN, BOT, HUMAN }

    private static final class Seat {
        volatile Kind kind = Kind.OPEN;
        volatile String character;
        volatile SeatChannel channel;
        volatile Map<String, Object> lastDecision;
    }

    final String id;
    final int players;
    final String rulesetId;
    final long seed;
    private final Seat[] seats;
    private volatile boolean started;
    private volatile GameState state;
    private final Map<Integer, InteractiveAgent> humanAgents = new ConcurrentHashMap<>();

    Lobby(String id, int players, String rulesetId, long seed) {
        this.id = id;
        this.players = players;
        this.rulesetId = rulesetId;
        this.seed = seed;
        this.seats = new Seat[players];
        for (int i = 0; i < players; i++) {
            seats[i] = new Seat();
        }
    }

    synchronized List<Map<String, Object>> seatsInfo() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            Seat s = seats[i];
            String label = switch (s.kind) {
                case OPEN -> "open";
                case BOT -> "bot:" + s.character;
                case HUMAN -> s.channel != null ? "human:connected" : "human:waiting";
            };
            out.add(Map.of("seat", i, "state", label));
        }
        return out;
    }

    /** Назначить место боту (или снова освободить) — только до старта партии. */
    synchronized void setSeat(int seat, String kind, String character) {
        if (started) {
            throw new IllegalStateException("партия уже началась, места не меняются");
        }
        checkSeat(seat);
        Seat s = seats[seat];
        if ("bot".equals(kind)) {
            if (!Bots.CHARACTERS.contains(character)) {
                throw new IllegalArgumentException("нет такого характера бота: " + character);
            }
            s.kind = Kind.BOT;
            s.character = character;
        } else if ("open".equals(kind)) {
            s.kind = Kind.OPEN;
            s.character = null;
            s.channel = null;
        } else {
            throw new IllegalArgumentException("неизвестный вид места: " + kind);
        }
    }

    /**
     * Подключить сокет к месту (первое подключение — занять свободное место
     * человеком; повторное — переподключение уже занятого места).
     *
     * @param wantedSeat -1 — подобрать свободное место автоматически (только
     *                   до старта партии; для переподключения место обязательно)
     * @return занятое место
     */
    int join(int wantedSeat, Socket socket, BufferedReader in, PrintWriter out) throws IOException {
        int seat;
        boolean reconnect;
        synchronized (this) {
            if (wantedSeat < 0) {
                if (started) {
                    throw new IllegalArgumentException("для переподключения нужно указать место");
                }
                seat = firstOpenSeat();
            } else {
                checkSeat(wantedSeat);
                seat = wantedSeat;
            }
            Seat s = seats[seat];
            if (started) {
                if (s.kind != Kind.HUMAN) {
                    throw new IllegalStateException("место " + seat + " занято не человеком");
                }
                reconnect = true;
            } else {
                if (s.kind == Kind.OPEN) {
                    s.kind = Kind.HUMAN;
                } else if (s.kind != Kind.HUMAN) {
                    throw new IllegalStateException("место " + seat + " занято ботом");
                }
                reconnect = s.channel != null;
            }
        }
        Seat s = seats[seat];
        SeatChannel channel = s.channel;
        if (channel == null) {
            int seatFinal = seat;
            channel = new SeatChannel(seat, msg -> onClientMessage(seatFinal, msg),
                () -> s.lastDecision);
            s.channel = channel;
        }
        channel.attach(in, out);
        if (!reconnect) {
            maybeStart();
        }
        return seat;
    }

    /** Досказать неотвеченную точку решения — вызывать ПОСЛЕ ответа на "join". */
    void sendCatchUp(int seat) {
        SeatChannel ch = seats[seat].channel;
        if (ch != null) {
            ch.sendCatchUp();
        }
    }

    private int firstOpenSeat() {
        for (int i = 0; i < players; i++) {
            if (seats[i].kind == Kind.OPEN) {
                return i;
            }
        }
        throw new IllegalStateException("свободных мест нет");
    }

    private void checkSeat(int seat) {
        if (seat < 0 || seat >= players) {
            throw new IllegalArgumentException("нет места " + seat + " за столом на " + players);
        }
    }

    private void maybeStart() {
        synchronized (this) {
            if (started) {
                return;
            }
            for (Seat s : seats) {
                if (s.kind == Kind.OPEN) {
                    return;
                }
            }
            started = true;
        }
        Thread t = new Thread(this::runGame, "lobby-" + id);
        t.setDaemon(true);
        t.start();
    }

    private void onClientMessage(int seat, Map<String, Object> msg) {
        String cmd = String.valueOf(msg.get("cmd"));
        if (!"choose".equals(cmd)) {
            return;
        }
        InteractiveAgent agent = humanAgents.get(seat);
        Object idx = msg.get("index");
        if (agent == null || !(idx instanceof Number n)) {
            return;
        }
        try {
            agent.submitIndex(n.intValue());
        } catch (RuntimeException e) {
            SeatChannel ch = seats[seat].channel;
            if (ch != null) {
                ch.send(Map.of("type", "error", "message", String.valueOf(e.getMessage())));
            }
        }
    }

    private void runGame() {
        log("партия начинается: " + players + " мест, свод " + rulesetId + ", сид " + seed);
        GameConfig cfg = GameConfig.build(rulesetId, players, seed, null, null);
        GameState st = Setup.buildGame(cfg);
        this.state = st;

        List<Agent> agents = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int seat = 0; seat < players; seat++) {
            Seat s = seats[seat];
            if (s.kind == Kind.HUMAN) {
                int seatFinal = seat;
                InteractiveAgent ia = new InteractiveAgent(seat, "Игрок " + (seat + 1),
                    d -> onDecision(seatFinal, d), ev -> onPublicEvent(seatFinal, ev));
                humanAgents.put(seat, ia);
                agents.add(ia);
                labels.add("human");
            } else {
                agents.add(Bots.create(s.character, seat, new Random(seed * 131 + seat + 1), players));
                labels.add(s.character);
            }
        }

        ReplayRecord rec;
        try {
            rec = GameRecorder.playWithAgents(cfg, st, agents, labels, seed, this::log);
        } catch (Throwable t) {
            log("партия прервана ошибкой: " + t);
            broadcast(Map.of("type", "error", "message", "партия прервана: " + t));
            return;
        }

        try {
            Path out = Path.of("reports", "server-games", "lobby-" + id + ".kelium-replay.json");
            rec.save(out);
            log("журнал записан: " + out.toAbsolutePath());
        } catch (IOException e) {
            log("не удалось записать журнал: " + e.getMessage());
        }

        Map<String, Object> over = new LinkedHashMap<>();
        over.put("type", "game_over");
        over.put("winner", rec.winner);
        over.put("condition", rec.condition);
        over.put("rounds", rec.rounds);
        broadcast(over);
    }

    private void onDecision(int seat, InteractiveAgent.PendingDecision d) {
        Map<String, Object> msg = decisionMessage(seat, d);
        seats[seat].lastDecision = msg;
        SeatChannel ch = seats[seat].channel;
        if (ch != null) {
            ch.send(msg);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decisionMessage(int seat, InteractiveAgent.PendingDecision d) {
        List<Map<String, Object>> options = new ArrayList<>();
        List<Choice> choices = d.options();
        for (int i = 0; i < choices.size(); i++) {
            Choice c = choices.get(i);
            String label = c.label() == null || c.label().isEmpty()
                ? String.valueOf(c.payload()) : c.label();
            options.add(Map.of("i", i, "label", label));
        }
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "decision");
        msg.put("kind", String.valueOf(d.context().get("kind")));
        msg.put("round", d.state().round);
        msg.put("circle", d.state().circle);
        msg.put("options", options);
        try {
            Object view = Json.parse(PublicView.of(d.state(), seat).toJson());
            msg.put("view", view);
        } catch (RuntimeException e) {
            // Вид — вспомогательная информация; список опций важнее и без него
            // клиент всё равно может играть по label'ам, поэтому не прерываем.
            log("не удалось собрать PublicView для места " + seat + ": " + e);
        }
        return msg;
    }

    private void onPublicEvent(int observingSeat, Map<String, Object> event) {
        SeatChannel ch = seats[observingSeat].channel;
        if (ch == null) {
            return;
        }
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "public");
        msg.put("event", String.valueOf(event.get("type")));
        if (event.get("seat") instanceof Number n) {
            msg.put("seat", n.intValue());
        }
        ch.send(msg);
    }

    private void broadcast(Map<String, Object> msg) {
        for (Seat s : seats) {
            if (s.channel != null) {
                s.channel.send(msg);
            }
        }
    }

    private void log(String s) {
        System.out.println("[лобби " + id + "] " + s);
    }
}
