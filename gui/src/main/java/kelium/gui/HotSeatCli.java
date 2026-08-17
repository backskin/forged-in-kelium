package kelium.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.InteractiveAgent;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;
import kelium.report.ReplayRecord;

/**
 * Консольный hot-seat: живые игроки по кругу передают терминал, боты играют
 * сами. Первый рабочий образец того, что живого игрока можно завести через
 * {@link InteractiveAgent} и получить журнал ТЕМ ЖЕ форматом, что у симуляций
 * (заказ «ЗАКАЗ — цифровая версия игры», §8, шаг 2).
 *
 * <p>Пока показывает только список опций и минимальный статус (раунд/круг) —
 * этого достаточно, чтобы доказать связку «блокирующий агент + журнал»; полный
 * обзор своего поля/руки (§4.3 заказа) — отдельная задача для настоящего UI.
 *
 * <p>Запуск: {@code kelium.gui.HotSeatCli <players> <seed> <seat0> <seat1> ...}
 * где место — {@code human} либо имя характера бота ({@link Bots#CHARACTERS}).
 * Без аргументов — двое, место 0 человек, место 1 бот "balanced".
 */
public final class HotSeatCli {

    private HotSeatCli() {
    }

    public static void main(String[] args) throws Exception {
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 2;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42L;
        List<String> seatSpecs = new ArrayList<>();
        for (int seat = 0; seat < players; seat++) {
            String spec = args.length > 2 + seat ? args[2 + seat]
                : (seat == 0 ? "human" : "balanced");
            seatSpecs.add(spec);
        }

        GameConfig cfg = GameConfig.build(GameConfig.DEFAULT_RULESET, players, seed, null, null);
        GameState state = Setup.buildGame(cfg);

        // ОДНА общая очередь на все человеческие места: движок однопоточный и
        // синхронный, поэтому в любой момент ждёт ответа не более чем от одного
        // агента — очередь просто сериализует те decision-точки, что достались
        // людям, в порядке, в котором их прислал движок. Без неё консольный поток
        // и поток движка печатали бы вперемешку и опрос (poll) мог бы поймать
        // decision между тем, как движок её обнулил и завёл следующую — теряя
        // ответ игрока на другую точку решения (см. заметку в памяти сессии).
        BlockingQueue<SeatDecision> pendingQueue = new ArrayBlockingQueue<>(1);
        Map<Integer, InteractiveAgent> humansBySeat = new HashMap<>();
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < players; seat++) {
            String spec = seatSpecs.get(seat);
            if ("human".equals(spec)) {
                int seatFinal = seat;
                InteractiveAgent ia = new InteractiveAgent(seat, "Игрок " + (seat + 1),
                    d -> pendingQueue.add(new SeatDecision(seatFinal, d)),
                    HotSeatCli::printPublicEvent);
                agents.add(ia);
                humansBySeat.put(seat, ia);
            } else {
                agents.add(Bots.create(spec, seat, new Random(seed * 131 + seat + 1), players));
            }
        }

        if (humansBySeat.isEmpty()) {
            System.out.println("Ни одного места \"human\" — партия просто отыграется ботами.");
        }

        AtomicReference<ReplayRecord> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread engineThread = new Thread(() -> {
            try {
                result.set(GameRecorder.playWithAgents(cfg, state, agents, seatSpecs, seed,
                    System.out::println));
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "kelium-engine");
        engineThread.start();

        Scanner in = new Scanner(System.in);
        while (engineThread.isAlive() || !pendingQueue.isEmpty()) {
            SeatDecision sd = pendingQueue.poll(50, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (sd == null) {
                continue;
            }
            printDecision(sd.seat(), sd.decision());
            System.out.print("Игрок " + (sd.seat() + 1) + ", выбор (номер) > ");
            if (!in.hasNextLine()) {
                break;
            }
            String line = in.nextLine().trim();
            try {
                humansBySeat.get(sd.seat()).submitIndex(Integer.parseInt(line));
            } catch (Exception e) {
                System.out.println("Не принято: " + e.getMessage());
            }
        }
        engineThread.join();
        if (failure.get() != null) {
            throw new RuntimeException(failure.get());
        }

        ReplayRecord rec = result.get();
        Path out = Path.of("hotseat-" + seed + ".kelium-replay.json");
        rec.save(out);
        System.out.println("Партия сыграна: победитель " + rec.winner
            + " (" + rec.condition + "), раундов " + rec.rounds
            + ". Журнал записан в " + out.toAbsolutePath());
    }

    private record SeatDecision(int seat, InteractiveAgent.PendingDecision decision) {
    }

    private static void printDecision(int seat, InteractiveAgent.PendingDecision d) {
        GameState s = d.state();
        System.out.println();
        System.out.println("== Раунд " + s.round + ", круг " + s.circle
            + ", место " + (seat + 1) + " — точка решения: " + d.context().get("kind") + " ==");
        List<Choice> options = d.options();
        for (int i = 0; i < options.size(); i++) {
            Choice c = options.get(i);
            String text = c.label() == null || c.label().isEmpty()
                ? String.valueOf(c.payload()) : c.label();
            System.out.println("  [" + i + "] " + text);
        }
    }

    private static void printPublicEvent(Map<String, Object> event) {
        // Тихий по умолчанию — иначе консоль тонет в потоке служебных событий.
        // Точка расширения для будущего "что видно другим игрокам между ходами".
    }
}
