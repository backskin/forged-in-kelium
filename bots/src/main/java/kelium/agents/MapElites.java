package kelium.agents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.dataio.Locations;

/**
 * MAP-ELITES — АТЛАС СТРАТЕГИЙ вместо одного чемпиона.
 *
 * <p>Зачем это дизайнеру. Обычный отбор сходится к ОДНОЙ мете: он выводит один
 * лучший геном, и всё, что играет иначе, вымирает — даже если играло почти так же
 * сильно. Для игры это плохо вдвойне: во-первых, нельзя узнать, какие стратегии
 * в игре вообще есть и насколько они равны (а это и есть баланс); во-вторых,
 * боты за столом становятся однообразны, и партии перестают быть интересными.
 *
 * <p>Что делает MAP-Elites. Он ведёт не одного чемпиона, а СЕТКУ чемпионов,
 * разложенную по СТИЛЮ ИГРЫ. Стиль описывается тремя осями поведения (сколько
 * бот воюет, сколько развивает экономику, насколько идёт в науку — см.
 * {@link Fitness#BEHAVIOR_AXES}). Каждая клетка сетки хранит сильнейшего бота
 * ИМЕННО ЭТОГО стиля. На выходе — карта: «чистый эконом выигрывает столько,
 * эконом с поздней осадой столько, ранний раш столько».
 *
 * <p>Это даёт сразу три вещи:
 * <ul>
 *   <li>ответ на балансовый вопрос «какие стратегии рабочие и равны ли они»;</li>
 *   <li>набор НЕПОХОЖИХ соперников для обучения — от этого чемпион становится
 *       устойчивее, потому что учится не против одного стиля;</li>
 *   <li>разные характеры за столом без ручных множителей: характер — это просто
 *       клетка атласа.</li>
 * </ul>
 */
public final class MapElites {

    /** Один житель атласа: сильнейший бот своего стиля. */
    public static final class Elite {
        public final Genome genome;
        public final double fitness;
        public final double winRate;
        public final double avgVp;
        public final double[] behavior;
        public final Map<String, Integer> counters;

        Elite(Genome genome, double fitness, double winRate, double avgVp,
              double[] behavior, Map<String, Integer> counters) {
            this.genome = genome;
            this.fitness = fitness;
            this.winRate = winRate;
            this.avgVp = avgVp;
            this.behavior = behavior;
            this.counters = counters;
        }
    }

    private final int numPlayers;
    private final int bins;
    private final int gamesPerCandidate;
    private final Map<String, Elite> archive = new ConcurrentHashMap<>();
    private final Random rng;
    private final ExecutorService pool;
    private final StringBuilder log = new StringBuilder();

    public MapElites(int numPlayers, int bins, int gamesPerCandidate, long seed) {
        this.numPlayers = numPlayers;
        this.bins = Math.max(2, bins);
        this.gamesPerCandidate = Math.max(4, gamesPerCandidate);
        this.rng = new Random(seed);
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        this.pool = Executors.newFixedThreadPool(threads);
    }

    private void say(String line) {
        System.out.println(line);
        log.append(line).append(System.lineSeparator());
    }

    /** Ключ клетки по поведению: номера корзин по каждой оси. */
    private String cellOf(double[] behavior) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < behavior.length; i++) {
            int b = (int) Math.floor(behavior[i] * bins);
            b = Math.max(0, Math.min(bins - 1, b));
            if (i > 0) {
                sb.append('_');
            }
            sb.append(b);
        }
        return sb.toString();
    }

    /** Проверить одного кандидата: сила и стиль, усреднённые по партиям. */
    private Elite assess(Genome g, List<Genome> rivals, long baseSeed) {
        double fit = 0;
        double vp = 0;
        int wins = 0;
        double[] beh = new double[Fitness.BEHAVIOR_AXES.size()];
        Map<String, Integer> counters = new java.util.HashMap<>();
        for (int i = 0; i < gamesPerCandidate; i++) {
            Fitness.Result r = Fitness.play(numPlayers, baseSeed + i, i % numPlayers,
                g, rivals, 0.0);
            fit += r.fitness();
            vp += r.vp();
            if (r.win()) {
                wins++;
            }
            for (int a = 0; a < beh.length; a++) {
                beh[a] += r.behavior()[a];
            }
            for (var e : r.counters().entrySet()) {
                counters.merge(e.getKey(), e.getValue(), Integer::sum);
            }
        }
        int n = gamesPerCandidate;
        for (int a = 0; a < beh.length; a++) {
            beh[a] /= n;
        }
        return new Elite(g, fit / n, wins / (double) n, vp / n, beh, counters);
    }

    /**
     * Наполнить атлас. {@code iterations} — сколько кандидатов проверить всего.
     *
     * <p>Схема простая и рабочая: пока атлас пуст — мутируем базовые геномы и
     * характеры (чтобы сразу занять разные углы сетки); дальше берём случайного
     * жителя атласа и мутируем его. Кандидат попадает в клетку своего стиля, если
     * СИЛЬНЕЕ прежнего жителя этой клетки. Так каждая клетка монотонно крепнет, а
     * стили не вытесняют друг друга.
     */
    public void run(int iterations) {
        // Соперники ФИКСИРОВАНЫ на весь атлас: иначе сила клеток несравнима.
        List<Genome> rivals = List.of(Genome.defaults(),
            Genome.defaults().withProfile("hawk"),
            Genome.defaults().withProfile("dove"));
        List<Genome> seeds = new ArrayList<>();
        seeds.add(Genome.defaults());
        for (String c : Bots.CHARACTERS) {
            seeds.add(Genome.defaults().withProfile(c));
        }

        int batch = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        int done = 0;
        int improvements = 0;
        while (done < iterations) {
            int size = Math.min(batch, iterations - done);
            List<Future<Elite>> futures = new ArrayList<>();
            for (int b = 0; b < size; b++) {
                Genome parent;
                if (archive.size() < seeds.size() * 2) {
                    parent = seeds.get(rng.nextInt(seeds.size()));
                } else {
                    List<Elite> living = new ArrayList<>(archive.values());
                    parent = living.get(rng.nextInt(living.size())).genome;
                }
                // Мутация КРУПНАЯ: атласу нужны непохожие боты, а не тонкая
                // настройка одного. Тонкую настройку делает EvoTrainer.
                final Genome cand = parent.mutate(rng, 0.35);
                final long seed = 3_000_000L + (done + b) * 613L;
                futures.add(pool.submit((Callable<Elite>) () -> assess(cand, rivals, seed)));
            }
            for (Future<Elite> f : futures) {
                try {
                    Elite e = f.get();
                    String cell = cellOf(e.behavior);
                    Elite cur = archive.get(cell);
                    if (cur == null || e.fitness > cur.fitness) {
                        archive.put(cell, e);
                        improvements++;
                    }
                } catch (Exception ex) {
                    System.err.println("кандидат сорвался: " + ex.getMessage());
                }
            }
            done += size;
            if (done % Math.max(1, iterations / 10) < batch) {
                say(String.format(Locale.ROOT,
                    "  проверено %d/%d, занято клеток %d, улучшений %d",
                    done, iterations, archive.size(), improvements));
            }
        }
        pool.shutdown();
    }

    /** Жители атласа, сильнейшие сверху. */
    public List<Map.Entry<String, Elite>> sorted() {
        List<Map.Entry<String, Elite>> out = new ArrayList<>(archive.entrySet());
        out.sort(Comparator.comparingDouble((Map.Entry<String, Elite> e) -> -e.getValue().fitness));
        return out;
    }

    /** Человеческое имя стиля по его поведению. */
    public static String styleName(double[] b) {
        double war = b[0];
        double econ = b[1];
        double tech = b.length > 2 ? b[2] : 0;
        StringBuilder sb = new StringBuilder();
        if (war >= 0.45) {
            sb.append("воин");
        } else if (war >= 0.25) {
            sb.append("боец при экономике");
        } else {
            sb.append("мирный");
        }
        if (econ >= 0.5) {
            sb.append(", крепкое хозяйство");
        } else if (econ >= 0.3) {
            sb.append(", среднее хозяйство");
        } else {
            sb.append(", слабое хозяйство");
        }
        if (tech >= 0.6) {
            sb.append(", гонка по трекам");
        } else if (tech >= 0.3) {
            sb.append(", наука вполсилы");
        } else {
            sb.append(", науку почти не трогает");
        }
        return sb.toString();
    }

    /** Отчёт-атлас в Markdown (по-русски, для дизайнера). */
    public String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Атлас стратегий\n\n");
        sb.append("Каждая строка — СИЛЬНЕЙШИЙ бот своего СТИЛЯ игры, а не просто ")
          .append("сильнейший бот. Обычный отбор оставляет одну мету и стирает ")
          .append("остальные стратегии, даже равные по силе; атлас показывает, ")
          .append("какие стратегии в игре есть на самом деле и насколько они равны.\n\n");
        sb.append("Стиль задан тремя осями: **доля боевых действий**, ")
          .append("**доля хозяйственных действий**, **продвижение по трекам науки**. ")
          .append("Все боты играли против ОДНОГО И ТОГО ЖЕ состава соперников на ")
          .append("одних и тех же раздачах, поэтому сила сравнима.\n\n");
        sb.append("| стиль | война | хозяйство | наука | % побед | ПО в среднем | сила |\n");
        sb.append("|-------|-------|-----------|-------|---------|--------------|------|\n");
        for (var e : sorted()) {
            Elite el = e.getValue();
            sb.append(String.format(Locale.ROOT,
                "| %s | %.2f | %.2f | %.2f | %.0f%% | %.1f | %.1f |%n",
                styleName(el.behavior), el.behavior[0], el.behavior[1], el.behavior[2],
                100.0 * el.winRate, el.avgVp, el.fitness));
        }
        var all = sorted();
        if (all.size() >= 2) {
            Elite top = all.get(0).getValue();
            sb.append("\n## Что из этого следует\n\n");
            sb.append("- Сильнейший найденный стиль: **").append(styleName(top.behavior))
              .append(String.format(Locale.ROOT, "** — %.0f%% побед, %.1f ПО.%n",
                  100.0 * top.winRate, top.avgVp));
            // Насколько стратегии равны: разброс процента побед среди клеток,
            // которые вообще жизнеспособны (побеждают чаще, чем раз из десяти).
            List<Elite> viable = new ArrayList<>();
            for (var e : all) {
                if (e.getValue().winRate >= 0.10) {
                    viable.add(e.getValue());
                }
            }
            sb.append("- Жизнеспособных стилей (побеждают чаще 10% партий): **")
              .append(viable.size()).append("** из ").append(all.size())
              .append(" найденных.\n");
            if (viable.size() >= 2) {
                double best = viable.get(0).winRate;
                double worst = viable.get(viable.size() - 1).winRate;
                sb.append(String.format(Locale.ROOT,
                    "- Разброс силы среди жизнеспособных: от %.0f%% до %.0f%% побед. ",
                    100.0 * worst, 100.0 * best));
                sb.append(best - worst > 0.25
                    ? "Разброс БОЛЬШОЙ — значит одна линия развития заметно выгоднее "
                      + "остальных, и это балансовый вопрос.\n"
                    : "Разброс умеренный — линии развития примерно равноценны.\n");
            }
        }
        return sb.toString();
    }

    /** Сохранить геномы атласа: по файлу на клетку (их можно сажать за стол). */
    public void saveGenomes(Path dir) throws java.io.IOException {
        Files.createDirectories(dir);
        for (var e : archive.entrySet()) {
            e.getValue().genome.saveJson(dir.resolve("cell_" + e.getKey() + ".json"));
        }
    }

    /**
     * CLI: {@code kelium.agents.MapElites [игроков] [кандидатов] [корзин] [партий]}.
     * По умолчанию 4 / 400 / 4 / 12.
     */
    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 400;
        int bins = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        int games = args.length > 3 ? Integer.parseInt(args[3]) : 12;

        System.out.println("Атлас стратегий: " + players + " игрока, кандидатов "
            + iterations + ", сетка " + bins + "^3, партий на кандидата " + games);
        long t0 = System.nanoTime();
        MapElites me = new MapElites(players, bins, games, 20_260_812L);
        me.run(iterations);
        String report = me.report();
        System.out.println();
        System.out.println(report);
        System.out.printf(Locale.ROOT, "время: %.1f мин%n", (System.nanoTime() - t0) / 6e10);

        Path out = Path.of("reports", "balance", "атлас-стратегий-" + players + "p.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, java.nio.charset.StandardCharsets.UTF_8);
        me.saveGenomes(Locations.botMemory().resolve("atlas" + players + "p"));
        System.out.println("отчёт: " + out.toAbsolutePath());
        System.out.println("геномы стилей: "
            + Locations.botMemory().resolve("atlas" + players + "p").toAbsolutePath());
    }
}
