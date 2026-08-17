package kelium.gui.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import kelium.dataio.GameConfig;
import kelium.report.Json;

/**
 * Сетевой сервер (заказ §4.4): лобби/сессии партий поверх TCP, простой протокол
 * — по одной JSON-строке на сообщение. Сервер авторитетен: партия играется
 * здесь ({@link Lobby}), клиент только шлёт номер выбранной опции.
 *
 * <p>Протокол до входа в место (по одному TCP-соединению, до "join"):
 * <pre>
 * → {"cmd":"create","players":2,"ruleset":"1.12.0","seed":123}
 * ← {"ok":true,"lobbyId":"AB3F9K","players":2,"ruleset":"1.12.0"}
 * → {"cmd":"setSeat","lobbyId":"AB3F9K","seat":1,"kind":"bot","character":"balanced"}
 * ← {"ok":true}
 * → {"cmd":"list"}
 * ← {"ok":true,"lobbies":[{"lobbyId":"AB3F9K","players":2,"ruleset":"1.12.0","seats":[...]}]}
 * → {"cmd":"join","lobbyId":"AB3F9K","seat":0}    // seat можно не указывать — подберётся свободное
 * ← {"ok":true,"seat":0,"lobbyId":"AB3F9K"}
 * </pre>
 * После "join" это ЖЕ соединение становится каналом места ({@link SeatChannel}):
 * сервер присылает {@code {"type":"decision",...}}, клиент отвечает
 * {@code {"cmd":"choose","index":N}}. Переподключение — то же "join" с тем же
 * {@code lobbyId}/{@code seat} на партии, которая уже началась.
 *
 * <p>Явно НЕ входит (заказ §4.4/§7): аккаунты, платежи, модерация — лобби
 * защищено только знанием кода (id), как и просит заказ («пригласить по коду»).
 */
public final class GameServer {

    private final Map<String, Lobby> lobbies = new ConcurrentHashMap<>();
    private final Random idRng = new Random();

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 4711;
        new GameServer().run(port);
    }

    public void run(int port) throws IOException {
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Сервер слушает порт " + port + ". Ctrl+C — остановить.");
            while (true) {
                Socket socket = server.accept();
                Thread t = new Thread(() -> handleConnection(socket), "conn-" + socket.getRemoteSocketAddress());
                t.setDaemon(true);
                t.start();
            }
        }
    }

    private void handleConnection(Socket socket) {
        try {
            socket.setTcpNoDelay(true);
            BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                Map<String, Object> req;
                try {
                    Object parsed = Json.parse(line);
                    if (!(parsed instanceof Map<?, ?> m)) {
                        throw new IllegalArgumentException("ожидался объект JSON");
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mm = (Map<String, Object>) m;
                    req = mm;
                } catch (RuntimeException bad) {
                    reply(out, error("не разобрал команду: " + bad.getMessage()));
                    continue;
                }

                String cmd = String.valueOf(req.get("cmd"));
                try {
                    if ("join".equals(cmd)) {
                        joinAndHandOff(req, socket, in, out);
                        return; // соединение теперь принадлежит SeatChannel
                    }
                    reply(out, handleControlCommand(cmd, req));
                } catch (RuntimeException e) {
                    reply(out, error(e.getMessage() == null ? e.toString() : e.getMessage()));
                }
            }
        } catch (IOException disconnected) {
            // отключились, не успев дойти до "join" — не о чём заботиться
        }
    }

    private Map<String, Object> handleControlCommand(String cmd, Map<String, Object> req) {
        switch (cmd) {
            case "create":
                return create(req);
            case "setSeat":
                return setSeat(req);
            case "list":
                return list();
            default:
                return error("неизвестная команда: " + cmd);
        }
    }

    private void joinAndHandOff(Map<String, Object> req, Socket socket,
                                 BufferedReader in, PrintWriter out) throws IOException {
        Lobby lobby = lobby(req);
        int seatWanted = req.get("seat") instanceof Number n ? n.intValue() : -1;
        int seat = lobby.join(seatWanted, socket, in, out);
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("ok", true);
        ok.put("lobbyId", lobby.id);
        ok.put("seat", seat);
        reply(out, ok);
        // ack ушёл первым — теперь можно досказать неотвеченную точку решения
        // (переподключение), не рискуя обогнать этот ack в потоке.
        lobby.sendCatchUp(seat);
    }

    private Map<String, Object> create(Map<String, Object> req) {
        int players = req.get("players") instanceof Number n ? n.intValue() : 2;
        String ruleset = req.get("ruleset") instanceof String s ? s : GameConfig.DEFAULT_RULESET;
        long seed = req.get("seed") instanceof Number n ? n.longValue() : idRng.nextLong();

        String id;
        do {
            id = randomId();
        } while (lobbies.putIfAbsent(id, new Lobby(id, players, ruleset, seed)) != null);

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("ok", true);
        ok.put("lobbyId", id);
        ok.put("players", players);
        ok.put("ruleset", ruleset);
        ok.put("seed", seed);
        return ok;
    }

    private Map<String, Object> setSeat(Map<String, Object> req) {
        Lobby lobby = lobby(req);
        int seat = req.get("seat") instanceof Number n ? n.intValue()
            : Integer.parseInt(String.valueOf(req.get("seat")));
        String kind = String.valueOf(req.get("kind"));
        String character = req.get("character") instanceof String s ? s : null;
        lobby.setSeat(seat, kind, character);
        return Map.of("ok", true);
    }

    private Map<String, Object> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Lobby l : lobbies.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lobbyId", l.id);
            m.put("players", l.players);
            m.put("ruleset", l.rulesetId);
            m.put("seats", l.seatsInfo());
            out.add(m);
        }
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("ok", true);
        ok.put("lobbies", out);
        return ok;
    }

    private Lobby lobby(Map<String, Object> req) {
        String id = String.valueOf(req.get("lobbyId"));
        Lobby lobby = lobbies.get(id);
        if (lobby == null) {
            throw new IllegalArgumentException("нет лобби с кодом " + id);
        }
        return lobby;
    }

    private String randomId() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // без похожих букв/цифр
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(alphabet.charAt(idRng.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("message", message);
        return m;
    }

    private static void reply(PrintWriter out, Map<String, Object> msg) {
        out.println(Json.write(msg));
    }
}
