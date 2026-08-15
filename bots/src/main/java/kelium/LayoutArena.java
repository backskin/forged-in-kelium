package kelium;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
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
import kelium.core.Hex;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * LayoutArena — ЛИГА РАСКЛАДОК: какое поле делает игру агрессивной, а какое мирной.
 *
 * <p>Зачем. Замеры показали неожиданное: выбор поля меняет расклад сил сильнее,
 * чем любые правки боевых правил. На одной авторской раскладке агрессивная линия
 * берёт 42% побед, на другой — 5%. Значит поле — главный балансовый инструмент в
 * игре, и рисовать его надо, зная, что получится.
 *
 * <p>Стенд САМ находит все раскладки для заданного числа игроков — и авторские, и
 * нарисованные конструктором (папки библиотеки настраиваются в окне раннера).
 * Достаточно нарисовать новое поле и запустить.
 *
 * <p>Что считается для каждой раскладки:
 * <ul>
 *   <li><b>агрессия</b> — боёв, ударов и уничтоженных жетонов за партию;</li>
 *   <li><b>окупается ли война</b> — доля побед агрессивной линии против мирной.
 *       Это главное: поле может быть шумным, но выгодной войну не делать;</li>
 *   <li><b>длина партии</b> — она задаётся числом тайлов зарождения и решает,
 *       успевает ли война окупиться;</li>
 *   <li><b>геометрия</b> — гексов, тайлов, расстояние между стартами.</li>
 * </ul>
 *
 * <p>Запуск: {@code kelium.LayoutArena [игроков] [партий на раскладку]}.
 */
public final class LayoutArena {

    private LayoutArena() {
    }

    /** Итог по одной раскладке. */
    private static final class Tally {
        int games;
        long battles;
        long hits;
        long destroyed;
        long rounds;
        int hawkWins;
        int hawkGames;
        int doveWins;
        int doveGames;
        double hawkVp;
        double doveVp;
        int cuRazed;
        int neutralsBigRazed;
        /** Чем кончались партии: условие окончания → сколько раз. */
        final Map<String, Integer> byCondition = new LinkedHashMap<>();
        // геометрия (заполняется один раз)
        int hexes;
        int tiles;
        int minDist;
        double avgDist;
        int neutralsSmall;
        int neutralsBig;

        double per(long v) {
            return games == 0 ? 0 : (double) v / games;
        }

        double hawkRate() {
            return hawkGames == 0 ? 0 : 100.0 * hawkWins / hawkGames;
        }

        double doveRate() {
            return doveGames == 0 ? 0 : 100.0 * doveWins / doveGames;
        }
    }

    private static synchronized void merge(Tally into, Tally one) {
        into.games += one.games;
        into.battles += one.battles;
        into.hits += one.hits;
        into.destroyed += one.destroyed;
        into.rounds += one.rounds;
        into.hawkWins += one.hawkWins;
        into.hawkGames += one.hawkGames;
        into.doveWins += one.doveWins;
        into.doveGames += one.doveGames;
        into.hawkVp += one.hawkVp;
        into.doveVp += one.doveVp;
        into.cuRazed += one.cuRazed;
        into.neutralsBigRazed += one.neutralsBigRazed;
        one.byCondition.forEach((k, v) -> into.byCondition.merge(k, v, Integer::sum));
    }

    private static final List<String> LINEUP =
        List.of("hawk", "dove", "balanced", "opportunist");

    private static Tally playOne(int players, long seed, LayoutLibrary.Entry layout) {
        GameConfig base = LayoutLibrary.configFor(players, seed);
        // Файл раскладки передаётся явно — так работают и авторские поля, и
        // нарисованные конструктором в чужой папке.
        GameConfig cfg = new GameConfig(base.ruleset, base.content, players, seed,
            base.dataRoot, base.boardSides, layout.id(), base.cuFacing, layout.file());
        GameState s = Setup.buildGame(cfg);

        Tally t = new Tally();
        t.games = 1;
        List<Agent> agents = new ArrayList<>();
        List<String> chars = new ArrayList<>();
        int shift = (int) (seed % players);
        for (int i = 0; i < players; i++) {
            String ch = LINEUP.get((i + shift) % LINEUP.size());
            chars.add(ch);
            agents.add(Bots.create(ch, i, new Random(seed * 31 + i), players));
        }

        Map<String, Object> res = GameEngine.playGame(s, agents, ev -> {
            String type = String.valueOf(ev.get("type"));
            if ("combat_hit".equals(type)) {
                t.hits++;
                if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                    t.destroyed++;
                    if (String.valueOf(ev.get("victim")).contains("command_center")) {
                        t.cuRazed++;
                    }
                }
            } else if ("raze_neutral".equals(type) && Boolean.TRUE.equals(ev.get("big"))) {
                t.neutralsBigRazed++;
            } else if ("action".equals(type) && "combat".equals(ev.get("action"))
                    && Boolean.TRUE.equals(ev.get("ok"))) {
                t.battles++;
            }
        });
        t.rounds = res.get("rounds") instanceof Number r ? r.intValue() : 0;
        t.byCondition.merge(String.valueOf(res.get("condition")), 1, Integer::sum);
        int winner = res.get("winner") instanceof Number w ? w.intValue() : -1;
        for (int i = 0; i < players; i++) {
            int vp = Scoring.scorePlayer(s, i).getOrDefault("total", 0);
            if ("hawk".equals(chars.get(i))) {
                t.hawkGames++;
                t.hawkVp += vp;
                if (i == winner) {
                    t.hawkWins++;
                }
            } else if ("dove".equals(chars.get(i))) {
                t.doveGames++;
                t.doveVp += vp;
                if (i == winner) {
                    t.doveWins++;
                }
            }
        }
        return t;
    }

    /** Геометрия раскладки: гексы, тайлы, расстояния между стартами. */
    private static void fillGeometry(Tally t, int players, LayoutLibrary.Entry layout) {
        GameConfig base = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, players, 1L,
            null, null);
        GameConfig cfg = new GameConfig(base.ruleset, base.content, players, 1L,
            base.dataRoot, base.boardSides, layout.id(), base.cuFacing, layout.file());
        GameState s = Setup.buildGame(cfg);
        t.hexes = s.field.size();
        for (Hex h : s.field.hexes.values()) {
            if (h.spawnTile != null) {
                t.tiles++;
            }
            for (Hex.NeutralBuilding nb : h.neutrals) {
                if (nb.big) {
                    t.neutralsBig++;
                } else {
                    t.neutralsSmall++;
                }
            }
        }
        int min = Integer.MAX_VALUE;
        int sum = 0;
        int pairs = 0;
        for (int a = 0; a < players; a++) {
            for (int b = a + 1; b < players; b++) {
                Integer d = bfs(s, s.player(a).startHex, s.player(b).startHex);
                if (d != null) {
                    min = Math.min(min, d);
                    sum += d;
                    pairs++;
                }
            }
        }
        t.minDist = min == Integer.MAX_VALUE ? 0 : min;
        t.avgDist = pairs == 0 ? 0 : sum / (double) pairs;
    }

    private static String condRu(String cond) {
        return switch (cond) {
            case "military" -> "военная (2 ЦУ)";
            case "victory_points" -> "по очкам (8 раундов)";
            case "super_objective" -> "супер-задание";
            case "all_peaks_occupied" -> "все вершины треков";
            case "last_spawn_tile" -> "последний тайл";
            default -> cond;
        };
    }

    private static Integer bfs(GameState s, String from, String to) {
        if (from == null || to == null) {
            return null;
        }
        Map<String, Integer> dist = new LinkedHashMap<>();
        java.util.Deque<String> q = new java.util.ArrayDeque<>();
        dist.put(from, 0);
        q.add(from);
        while (!q.isEmpty()) {
            String cur = q.poll();
            if (cur.equals(to)) {
                return dist.get(cur);
            }
            for (String nb : s.field.neighborsView(cur)) {
                if (!dist.containsKey(nb)) {
                    dist.put(nb, dist.get(cur) + 1);
                    q.add(nb);
                }
            }
        }
        return null;
    }

    private static Tally run(int players, int games, LayoutLibrary.Entry layout) {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Tally total = new Tally();
        List<Future<Tally>> futures = new ArrayList<>();
        for (int g = 0; g < games; g++) {
            final long seed = 9_400_000L + g;   // одни и те же раздачи на всех полях
            futures.add(pool.submit((Callable<Tally>) () -> playOne(players, seed, layout)));
        }
        for (Future<Tally> f : futures) {
            try {
                merge(total, f.get());
            } catch (Exception e) {
                System.err.println("партия сорвалась (" + layout.id() + "): " + e.getMessage());
            }
        }
        pool.shutdown();
        fillGeometry(total, players, layout);
        return total;
    }

    /**
     * Прочитать раскладки на {@code players} игроков ИЗ ОДНОЙ ПАПКИ, не заглядывая
     * в библиотеку. Формат — тот же, что пишет конструктор раскладок.
     */
    private static List<LayoutLibrary.Entry> scanFolder(Path dir, int players,
                                                        List<String> problems) {
        List<LayoutLibrary.Entry> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            problems.add("нет такой папки: " + dir);
            return out;
        }
        List<Path> files = new ArrayList<>();
        try (var s = Files.list(dir)) {
            s.filter(p -> Files.isRegularFile(p) && kelium.dataio.FieldFile.isField(p))
                .sorted().forEach(files::add);
        } catch (java.io.IOException e) {
            problems.add("папка не читается: " + dir);
            return out;
        }
        for (Path f : files) {
            List<Map<String, Object>> variants;
            try {
                variants = kelium.engine.Scenario.loadVariantsFromFile(f);
            } catch (RuntimeException e) {
                problems.add(f.getFileName() + ": не читается — " + e.getMessage());
                continue;
            }
            for (Map<String, Object> v : variants) {
                Object pl = v.get("players");
                int n = pl instanceof Number num ? num.intValue() : -1;
                if (n != players) {
                    continue;
                }
                out.add(new LayoutLibrary.Entry(String.valueOf(v.get("id")), n, f,
                    dir.toString()));
            }
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        // Третий аргумент — ПАПКА С РАСКЛАДКАМИ. Нужен, чтобы прогнать свежие
        // поля, не трогая настройки библиотеки в реестре пользователя: нарисовал в
        // любую папку — сразу проверил.
        Path folder = args.length > 2 ? Path.of(args[2]) : null;

        List<String> problems = new ArrayList<>();
        List<LayoutLibrary.Entry> layouts = folder != null
            ? scanFolder(folder, players, problems)
            : LayoutLibrary.scan(players, problems);
        if (layouts.isEmpty()) {
            System.out.println("Раскладок на " + players + " игроков не найдено.");
            for (String p : problems) {
                System.out.println("  " + p);
            }
            return;
        }
        System.out.println("Найдено раскладок на " + players + " игроков: " + layouts.size()
            + ", по " + games + " партий на каждую (раздачи одни и те же).");
        for (String p : problems) {
            System.out.println("  внимание: " + p);
        }

        Map<String, Tally> results = new LinkedHashMap<>();
        Map<String, LayoutLibrary.Entry> byId = new LinkedHashMap<>();
        for (LayoutLibrary.Entry e : layouts) {
            long t0 = System.nanoTime();
            Tally t;
            try {
                t = run(players, games, e);
            } catch (RuntimeException ex) {
                System.out.println("  " + e.id() + ": не проигралась — " + ex.getMessage());
                continue;
            }
            results.put(e.id(), t);
            byId.put(e.id(), e);
            System.out.printf(Locale.ROOT,
                "  %-28s уничтожено %.2f, ястреб %.0f%% / голубь %.0f%%, раундов %.1f  (%.0f с)%n",
                e.id(), t.per(t.destroyed), t.hawkRate(), t.doveRate(), t.per(t.rounds),
                (System.nanoTime() - t0) / 1e9);
        }
        if (results.isEmpty()) {
            System.out.println("Ни одна раскладка не проигралась.");
            return;
        }

        // Сортировка по АГРЕССИВНОСТИ: сколько жетонов уничтожается за партию.
        List<String> order = new ArrayList<>(results.keySet());
        order.sort(Comparator.comparingDouble(
            (String id) -> -results.get(id).per(results.get(id).destroyed)));

        StringBuilder sb = new StringBuilder();
        sb.append("# Лига раскладок: какое поле делает игру агрессивной\n\n");
        sb.append("По ").append(games).append(" партий на раскладку, ").append(players)
          .append(" игрока. Раздачи у всех раскладок ОДНИ И ТЕ ЖЕ, за столом ")
          .append("ястреб / голубь / сбалансированный / оппортунист с ротацией мест — ")
          .append("значит разница в цифрах идёт от ПОЛЯ, а не от везения.\n\n");
        sb.append("Отсортировано по числу уничтоженных жетонов за партию (сверху — ")
          .append("самые агрессивные поля).\n\n");
        sb.append("| раскладка | уничтожено | боёв | ударов | сносов ЦУ | больших нейтралов "
            + "| ястреб, % побед | голубь, % побед | раундов |\n");
        sb.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (String id : order) {
            Tally t = results.get(id);
            sb.append(String.format(Locale.ROOT,
                "| %s | **%.2f** | %.1f | %.1f | %.2f | %.2f | %.0f%% | %.0f%% | %.1f |%n",
                id, t.per(t.destroyed), t.per(t.battles), t.per(t.hits),
                t.per(t.cuRazed), t.per(t.neutralsBigRazed),
                t.hawkRate(), t.doveRate(), t.per(t.rounds)));
        }

        sb.append("\n## Геометрия — почему поля различаются\n\n");
        sb.append("| раскладка | гексов | на игрока | тайлов | тайлов на игрока "
            + "| нейтралов малых | нейтралов больших | мин. расстояние стартов "
            + "| среднее расстояние |\n");
        sb.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (String id : order) {
            Tally t = results.get(id);
            sb.append(String.format(Locale.ROOT,
                "| %s | %d | %.1f | %d | %.1f | %d | %d | %d | %.1f |%n",
                id, t.hexes, t.hexes / (double) players, t.tiles,
                t.tiles / (double) players, t.neutralsSmall, t.neutralsBig,
                t.minDist, t.avgDist));
        }

        // ЧЕМ КОНЧАЮТСЯ ПАРТИИ — это и объясняет разницу в длине, а через неё в
        // агрессии. Без этой таблицы механизм остаётся догадкой.
        sb.append("\n## Чем кончаются партии\n\n");
        List<String> conds = new ArrayList<>();
        for (Tally t : results.values()) {
            for (String c : t.byCondition.keySet()) {
                if (!conds.contains(c)) {
                    conds.add(c);
                }
            }
        }
        sb.append("| раскладка |");
        for (String c : conds) {
            sb.append(' ').append(condRu(c)).append(" |");
        }
        sb.append("\n|---|");
        for (int i = 0; i < conds.size(); i++) {
            sb.append("---:|");
        }
        sb.append('\n');
        for (String id : order) {
            Tally t = results.get(id);
            int g = Math.max(1, t.games);
            sb.append("| ").append(id).append(" |");
            for (String c : conds) {
                sb.append(String.format(Locale.ROOT, " %.0f%% |",
                    100.0 * t.byCondition.getOrDefault(c, 0) / g));
            }
            sb.append('\n');
        }

        Tally top = results.get(order.get(0));
        Tally bottom = results.get(order.get(order.size() - 1));
        sb.append("\n## Что из этого следует\n\n");
        sb.append(String.format(Locale.ROOT,
            "- Самое агрессивное поле — **%s**: %.2f уничтоженных жетонов за партию. "
            + "Самое мирное — **%s**: %.2f. Разница в %.0f%%.%n",
            order.get(0), top.per(top.destroyed), order.get(order.size() - 1),
            bottom.per(bottom.destroyed),
            bottom.per(bottom.destroyed) <= 0 ? 0
                : 100.0 * (top.per(top.destroyed) / bottom.per(bottom.destroyed) - 1)));
        sb.append(String.format(Locale.ROOT,
            "- Окупаемость войны: на «%s» агрессивная линия берёт %.0f%% побед, "
            + "на «%s» — %.0f%%.%n",
            order.get(0), top.hawkRate(), order.get(order.size() - 1), bottom.hawkRate()));
        sb.append("- Длина партии задаётся ЧИСЛОМ ТАЙЛОВ ЗАРОЖДЕНИЯ: партия ")
          .append("обрывается, когда остаётся последний источник келемия. Меньше ")
          .append("тайлов — короче партия — война не успевает окупиться.\n");
        sb.append("- Если хочется поле агрессивнее: больше тайлов (дольше партия), ")
          .append("старты ближе, меньше свободного места по краям, ценные тайлы в ")
          .append("центре. Проверять — этим же стендом.\n");

        String report = sb.toString();
        System.out.println();
        System.out.println(report);
        Path out = Path.of("reports", "balance", "лига-раскладок-" + players + "p.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }
}
