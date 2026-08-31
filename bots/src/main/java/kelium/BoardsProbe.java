package kelium;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.engine.LayoutLibrary;

/**
 * BoardsProbe — ЧЕМ БОТЫ ПОЛЬЗУЮТСЯ НА ПЛАНШЕТАХ НАУКИ И РЫНКА.
 *
 * <p>Вопрос дизайнера: пользуются ли боты вечными обменами науки и уникальным
 * предложением с карты рынка — и вообще всеми ли печатными обменами. Ответить на
 * это «в среднем сделок N» нельзя: обменов на планшетах восемь, и вырожденным
 * может оказаться любой из них по отдельности. Поэтому каждый обмен и каждый трек
 * считаются ПОРОЗНЬ.
 *
 * <p>Считается:
 * <ul>
 *   <li><b>рынок</b> — сколько раз взят КАЖДЫЙ из четырёх печатных обменов
 *       (монеты / боеприпасы / карты заданий / кубик энергии навсегда) и сколько
 *       раз использовано уникальное предложение с активной карты;</li>
 *   <li><b>наука</b> — сколько раз взят КАЖДЫЙ вечный обмен (трофей→монета,
 *       две карты арсенала, позолота модуля, перемещение модуля) и сколько шагов
 *       сделано ПО КАЖДОМУ треку;</li>
 *   <li>сколько действий Рынка и Науки прошло вообще и сколько из них не дали
 *       ничего.</li>
 * </ul>
 *
 * <p>Запуск: {@code kelium.BoardsProbe [игроков] [партий]}.
 */
public final class BoardsProbe {

    private BoardsProbe() {
    }

    private static final class Tally {
        int games;
        int marketActions;
        int marketEmpty;
        int scienceActions;
        int scienceEmpty;
        final Map<String, Integer> counts = new LinkedHashMap<>();

        void add(String key, int n) {
            counts.merge(key, n, Integer::sum);
        }
    }

    private static synchronized void merge(Tally into, Tally one) {
        into.games += one.games;
        into.marketActions += one.marketActions;
        into.marketEmpty += one.marketEmpty;
        into.scienceActions += one.scienceActions;
        into.scienceEmpty += one.scienceEmpty;
        one.counts.forEach((k, v) -> into.counts.merge(k, v, Integer::sum));
    }

    @SuppressWarnings("unchecked")
    private static Tally playOne(int players, long seed) {
        GameConfig cfg = LayoutLibrary.configFor(players, seed);
        GameState s = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        String[] mixed = {"hawk", "dove", "balanced", "opportunist"};
        int shift = (int) (seed % players);
        for (int i = 0; i < players; i++) {
            agents.add(Bots.create(mixed[(i + shift) % mixed.length], i,
                new Random(seed * 31 + i), players));
        }
        Tally t = new Tally();
        t.games = 1;
        GameEngine.playGame(s, agents, ev -> {
            if (!"action".equals(String.valueOf(ev.get("type")))) {
                return;
            }
            String action = String.valueOf(ev.get("action"));
            boolean market = "market".equals(action);
            boolean science = "science".equals(action);
            Map<String, Object> tel = ev.get("telemetry") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
            // ПЕРЕНОСЫ ЗДАНИЙ: перестройка — законный приём (ЦУ переносится
            // бесплатно, добытчик за свою цену к новой жиле), и надо знать,
            // пользуются ли им боты.
            if ("build".equals(action) && tel.get("moved_building") != null) {
                t.add("стройка: перенос здания", 1);
            }
            if (!market && !science) {
                return;
            }
            if (market) {
                t.marketActions++;
                int deals = tel.get("deals") instanceof Number n ? n.intValue() : 0;
                if (deals == 0) {
                    t.marketEmpty++;
                }
                for (Map.Entry<String, Object> e : tel.entrySet()) {
                    if (e.getKey().startsWith("deal_") && e.getValue() instanceof Number n) {
                        t.add("рынок: " + dealRu(e.getKey().substring(5)), n.intValue());
                    }
                }
                if (Boolean.TRUE.equals(tel.get("card_offer"))) {
                    t.add("рынок: предложение С КАРТЫ", 1);
                }
            } else {
                t.scienceActions++;
                int steps = tel.get("steps") instanceof Number n ? n.intValue() : 0;
                Object exchange = tel.get("exchange");
                if (steps == 0 && exchange == null) {
                    t.scienceEmpty++;
                }
                if (exchange != null) {
                    t.add("наука: обмен " + exchangeRu(String.valueOf(exchange)), 1);
                }
                for (Map.Entry<String, Object> e : tel.entrySet()) {
                    if (e.getKey().startsWith("track_")) {
                        t.add("наука: шаг по треку " + e.getKey().substring(6), 1);
                    }
                }
            }
        });
        return t;
    }

    private static String dealRu(String what) {
        return switch (what) {
            case "coin" -> "монеты";
            case "ammo" -> "боеприпасы";
            case "objective_cards" -> "карты заданий";
            case "energy" -> "кубик энергии НАВСЕГДА";
            default -> what;
        };
    }

    private static String exchangeRu(String id) {
        return switch (id) {
            case "trophy_to_coin" -> "трофей → монета";
            case "draw_arsenal" -> "2 трофея → 2 карты арсенала, одну оставить";
            case "gild" -> "2 обломка → позолотить модуль";
            case "move_module" -> "трофей → переместить модуль";
            default -> id;
        };
    }

    private static Tally run(int players, int games) {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Tally total = new Tally();
        List<Future<Tally>> futures = new ArrayList<>();
        for (int g = 0; g < games; g++) {
            final long seed = 4_400_000L + g;
            futures.add(pool.submit((Callable<Tally>) () -> playOne(players, seed)));
        }
        for (Future<Tally> f : futures) {
            try {
                merge(total, f.get());
            } catch (Exception e) {
                System.err.println("партия сорвалась: " + e.getMessage());
            }
        }
        pool.shutdown();
        return total;
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 500;

        Tally t = run(players, games);
        int g = Math.max(1, t.games);

        StringBuilder sb = new StringBuilder();
        sb.append("# Чем боты пользуются на планшетах науки и рынка\n\n");
        sb.append("По ").append(games).append(" партий, ").append(players)
          .append(" игрока. Каждый обмен считается ПОРОЗНЬ: обменов на двух ")
          .append("планшетах восемь, и вырожденным может оказаться любой из них ")
          .append("по отдельности, а «сделок в среднем N» этого не показывает.\n\n");

        sb.append("## Сколько действий и сколько впустую\n\n");
        sb.append("| действие | сыграно за партию | из них без результата |\n");
        sb.append("|---|---:|---:|\n");
        sb.append(String.format(Locale.ROOT, "| Рынок | %.2f | %.0f%% |%n",
            t.marketActions / (double) g,
            t.marketActions == 0 ? 0 : 100.0 * t.marketEmpty / t.marketActions));
        sb.append(String.format(Locale.ROOT, "| Наука | %.2f | %.0f%% |%n",
            t.scienceActions / (double) g,
            t.scienceActions == 0 ? 0 : 100.0 * t.scienceEmpty / t.scienceActions));

        sb.append("\n## Каждый обмен по отдельности\n\n");
        sb.append("| обмен / трек | раз за партию | доля партий, где взят хоть раз |\n");
        sb.append("|---|---:|---:|\n");
        List<String> keys = new ArrayList<>(t.counts.keySet());
        keys.sort((a, b) -> Integer.compare(t.counts.get(b), t.counts.get(a)));
        for (String k : keys) {
            sb.append(String.format(Locale.ROOT, "| %s | %.2f | — |%n",
                k, t.counts.get(k) / (double) g));
        }
        if (keys.isEmpty()) {
            sb.append("| — | 0 | механика не сработала ни разу |\n");
        }

        sb.append("\n## Чего НЕ видно в таблице\n\n");
        sb.append("Если обмена нет в списке — боты не взяли его НИ РАЗУ за ")
          .append(games).append(" партий. Для механики, напечатанной на планшете, ")
          .append("это повод разобраться: либо она не нужна, либо бот её не умеет ")
          .append("оценить, либо она недоступна из-за условия.\n\n");
        sb.append("**Оговорка про науку.** Движок предлагает ОДИН вечный обмен за ")
          .append("действие Науки, тогда как по СВОДу «каждый вечный курс — не ")
          .append("более одного раза за действие», то есть за одно действие можно ")
          .append("взять до четырёх разных обменов. Это расхождение движка и СВОДа ")
          .append("(есть в аудите) — пока оно не закрыто, числа ниже занижены.\n");

        String report = sb.toString();
        System.out.println(report);
        Path out = Path.of("reports", "balance", "планшеты-наука-и-рынок-"
            + players + "p.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }
}
