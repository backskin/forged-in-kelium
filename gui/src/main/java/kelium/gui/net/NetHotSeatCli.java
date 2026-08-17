package kelium.gui.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import kelium.report.Json;

/**
 * Тестовый сетевой клиент — тот же консольный интерфейс, что {@code HotSeatCli},
 * но по TCP через {@link GameServer} вместо локальных потоков. Доказывает, что
 * "тот же клиент, что в hot-seat, подключается по сети" (заказ §8, шаг 3).
 *
 * <p>Примеры:
 * <pre>
 * kelium.gui.net.NetHotSeatCli host port create 2 1.12.0
 * kelium.gui.net.NetHotSeatCli host port setSeat LOBBY 1 bot balanced
 * kelium.gui.net.NetHotSeatCli host port join LOBBY 0
 * kelium.gui.net.NetHotSeatCli host port list
 * </pre>
 */
public final class NetHotSeatCli {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Использование: NetHotSeatCli <host> <port> <create|setSeat|join|list> ...");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String action = args[2];

        try (Socket socket = new Socket(host, port)) {
            BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            switch (action) {
                case "list" -> {
                    send(out, Map.of("cmd", "list"));
                    System.out.println(in.readLine());
                }
                case "create" -> {
                    Map<String, Object> req = new LinkedHashMap<>();
                    req.put("cmd", "create");
                    req.put("players", args.length > 3 ? Integer.parseInt(args[3]) : 2);
                    if (args.length > 4) {
                        req.put("ruleset", args[4]);
                    }
                    if (args.length > 5) {
                        req.put("seed", Long.parseLong(args[5]));
                    }
                    send(out, req);
                    System.out.println(in.readLine());
                }
                case "setSeat" -> {
                    Map<String, Object> req = new LinkedHashMap<>();
                    req.put("cmd", "setSeat");
                    req.put("lobbyId", args[3]);
                    req.put("seat", Integer.parseInt(args[4]));
                    req.put("kind", args[5]);
                    if (args.length > 6) {
                        req.put("character", args[6]);
                    }
                    send(out, req);
                    System.out.println(in.readLine());
                }
                case "join" -> playInteractive(in, out, args);
                default -> System.out.println("Неизвестная команда: " + action);
            }
        }
    }

    private static void playInteractive(BufferedReader in, PrintWriter out, String[] args) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("cmd", "join");
        req.put("lobbyId", args[3]);
        if (args.length > 4) {
            req.put("seat", Integer.parseInt(args[4]));
        }
        send(out, req);
        System.out.println(in.readLine()); // ответ на join — {"ok":true,"seat":N,...}

        BlockingQueue<Map<String, Object>> incoming = new LinkedBlockingQueue<>();
        Thread reader = new Thread(() -> readLoop(in, incoming), "net-reader");
        reader.setDaemon(true);
        reader.start();

        Scanner console = new Scanner(System.in);
        while (true) {
            Map<String, Object> msg = incoming.take();
            String type = String.valueOf(msg.get("type"));
            switch (type) {
                case "decision" -> {
                    printDecision(msg);
                    System.out.print("Выбор (номер) > ");
                    if (!console.hasNextLine()) {
                        return;
                    }
                    String line = console.nextLine().trim();
                    try {
                        send(out, Map.of("cmd", "choose", "index", Integer.parseInt(line)));
                    } catch (NumberFormatException e) {
                        System.out.println("Нужен номер опции.");
                    }
                }
                case "public" -> System.out.println("  · " + msg.get("event")
                    + (msg.containsKey("seat") ? " (место " + msg.get("seat") + ")" : ""));
                case "game_over" -> {
                    System.out.println("Партия окончена: победитель " + msg.get("winner")
                        + " (" + msg.get("condition") + "), раундов " + msg.get("rounds"));
                    return;
                }
                case "error" -> System.out.println("[сервер] " + msg.get("message"));
                case "END_OF_STREAM" -> {
                    System.out.println("Соединение с сервером потеряно.");
                    return;
                }
                default -> System.out.println("? " + msg);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void printDecision(Map<String, Object> msg) {
        System.out.println();
        System.out.println("== Раунд " + msg.get("round") + ", круг " + msg.get("circle")
            + " — точка решения: " + msg.get("kind") + " ==");
        List<Object> options = (List<Object>) msg.get("options");
        for (Object o : options) {
            Map<String, Object> opt = (Map<String, Object>) o;
            System.out.println("  [" + opt.get("i") + "] " + opt.get("label"));
        }
    }

    private static void readLoop(BufferedReader in, BlockingQueue<Map<String, Object>> incoming) {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                Object parsed = Json.parse(line);
                if (parsed instanceof Map<?, ?> m) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mm = (Map<String, Object>) m;
                    incoming.add(mm);
                }
            }
        } catch (IOException e) {
            // упадёт в END_OF_STREAM ниже
        }
        incoming.add(Map.of("type", "END_OF_STREAM"));
    }

    private static void send(PrintWriter out, Map<String, Object> msg) {
        out.println(Json.write(msg));
    }
}
