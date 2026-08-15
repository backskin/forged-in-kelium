package kelium.agents;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.dataio.Locations;

/**
 * EvoTrainer — эволюционное обучение стратегического бота. ПЕРЕПИСАН НАЧИСТО.
 *
 * <p>Что было сломано в прежней версии (всё это исправлено здесь):
 * <ol>
 *   <li><b>Цель поощряла суету.</b> Победа стоила в целевой функции столько же,
 *       сколько три убийства, а поддержка (удары, произведённые юниты) была
 *       вшита навсегда. Теперь победа — главный член, а поддержка ЗАТУХАЕТ до
 *       нуля к середине обучения (см. {@link Fitness}).</li>
 *   <li><b>Лучший геном выбирался по обучающим партиям.</b> Отчётный fitness был
 *       завышен, и переобучение под конкретные раздачи было невидимо. Теперь
 *       чемпион сохраняется по ОТЛОЖЕННОЙ проверке — партиям с другими зёрнами,
 *       которых отбор не видел.</li>
 *   <li><b>Соперник был слабым или менялся на ходу.</b> Куррикулум упирался в
 *       «случайные → простые эвристики», а самоигра шла против текущего лучшего,
 *       из-за чего популяция могла годами ходить по кругу камень-ножницы-бумага.
 *       Теперь есть ЗАЛ СЛАВЫ: замороженные чемпионы прошлых этапов остаются
 *       спарринг-партнёрами навсегда, и обыграть их всех камнем-ножницами
 *       невозможно.</li>
 *   <li><b>Отбору было почти нечего настраивать.</b> Геном крутил 28 тактических
 *       весов поверх жёстко прошитых оценки позиции и выбора цели. Теперь в
 *       геноме и оценочная функция целиком ({@code eval.*} по признакам
 *       {@link StateFeatures}), и ценности промежуточных целей
 *       ({@code plan.value.*}) — то есть сама стратегия, а не только громкость.</li>
 * </ol>
 *
 * <p>Партии внутри поколения играются ПАРАЛЛЕЛЬНО, но на ОДНИХ И ТЕХ ЖЕ зёрнах:
 * все кандидаты проходят ровно те же раздачи, иначе отбор мерил бы удачу.
 */
public final class EvoTrainer {

    private final int numPlayers;
    private final Random rng;
    /** Линия характера (перекос старта и лёгкий перекос цели) или null. */
    private final String profile;
    private final ExecutorService pool;
    /** ЗАЛ СЛАВЫ: замороженные чемпионы прошлых этапов — вечные спарринги. */
    private final List<Genome> hallOfFame = new ArrayList<>();
    private final StringBuilder log = new StringBuilder();

    public EvoTrainer(int numPlayers, long seed) {
        this(numPlayers, seed, null);
    }

    /** Чем играют боты в обучающих партиях (см. {@link Fitness.Brain}). */
    private final Fitness.Brain brain;

    public EvoTrainer(int numPlayers, long seed, String profile) {
        this(numPlayers, seed, profile, Fitness.Brain.ФОРМУЛА);
    }

    public EvoTrainer(int numPlayers, long seed, String profile, Fitness.Brain brain) {
        this.numPlayers = numPlayers;
        this.rng = new Random(seed);
        this.profile = profile;
        this.brain = brain;
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        this.pool = Executors.newFixedThreadPool(threads);
    }

    /** Что напечатано за обучение (для файла отчёта). */
    public String logText() {
        return log.toString();
    }

    private void say(String line) {
        System.out.println(line);
        log.append(line).append(System.lineSeparator());
    }

    // ==================================================================
    //  Оценка генома
    // ==================================================================

    /**
     * Средняя приспособленность генома по {@code games} партиям с РОТАЦИЕЙ МЕСТА.
     * Ротация обязательна: стартовый порядок в этой игре даёт преимущество, без
     * неё обучение подгоняло бы бота под одно конкретное место.
     */
    private double evaluate(Genome g, List<Genome> rivals, int games, long baseSeed,
                            double shaping) {
        double sum = 0.0;
        for (int i = 0; i < games; i++) {
            sum += Fitness.play(numPlayers, baseSeed + i, i % numPlayers, g, rivals,
                shaping, brain).fitness();
        }
        return sum / games;
    }

    /**
     * ОТЛОЖЕННАЯ ПРОВЕРКА: те же соперники, но зёрна из другого диапазона и
     * поддержка ВЫКЛЮЧЕНА. Именно по этому числу выбирается чемпион — оно
     * показывает силу, а не подгонку под обучающие раздачи.
     */
    private double holdout(Genome g, List<Genome> rivals, int games) {
        double sum = 0.0;
        int wins = 0;
        for (int i = 0; i < games; i++) {
            Fitness.Result r = Fitness.play(numPlayers, 777_000_000L + i,
                i % numPlayers, g, rivals, 0.0, brain);
            sum += r.fitness();
            if (r.win()) {
                wins++;
            }
        }
        lastHoldoutWinRate = (double) wins / games;
        return sum / games;
    }

    private double lastHoldoutWinRate = 0.0;

    /** Спарринг-состав: зал славы плюс базовый геном как нижняя планка. */
    private List<Genome> sparring() {
        List<Genome> out = new ArrayList<>();
        if (hallOfFame.isEmpty()) {
            out.add(Genome.defaults());
        } else {
            // Берём до трёх последних чемпионов и одного самого раннего: так
            // популяция обязана держать и современный уровень, и старые стили.
            int n = hallOfFame.size();
            for (int i = Math.max(0, n - 3); i < n; i++) {
                out.add(hallOfFame.get(i));
            }
            out.add(hallOfFame.get(0));
        }
        return out;
    }

    // ==================================================================
    //  Обучение
    // ==================================================================

    /** Запустить обучение. Вернуть лучший геном ПО ОТЛОЖЕННОЙ ПРОВЕРКЕ. */
    public Genome train(int generations, int population, int gamesPerGenome,
                        double mutationRate, Path savePath) {
        Genome seed0 = profile == null ? Genome.defaults()
            : Genome.defaults().withProfile(profile);
        hallOfFame.add(seed0);

        List<Genome> pop = new ArrayList<>();
        pop.add(seed0);
        for (int i = 1; i < population; i++) {
            pop.add(seed0.mutate(rng, mutationRate * 1.5));
        }

        Genome champion = seed0;
        double championHoldout = Double.NEGATIVE_INFINITY;
        // Отложенный состав соперников ФИКСИРОВАН на всё обучение: иначе
        // проверочное число нельзя сравнивать между поколениями.
        List<Genome> holdoutRivals = List.of(Genome.defaults(),
            Genome.defaults().withProfile("hawk"), Genome.defaults().withProfile("dove"));

        say("поколений " + generations + ", популяция " + population
            + ", партий на геном " + gamesPerGenome
            + (profile == null ? "" : ", линия " + profile));
        say("цель: победа (60) + отрыв (×2) + очки (×0.5) + затухающая поддержка");

        for (int gen = 0; gen < generations; gen++) {
            double progress = generations <= 1 ? 1.0 : gen / (double) (generations - 1);
            double shaping = Fitness.shapingAt(progress, 0.35);
            List<Genome> rivals = sparring();
            // Зёрна ФИКСИРОВАНЫ внутри поколения: все кандидаты играют одни и те
            // же раздачи, поэтому сравнение между ними честное.
            final long baseSeed = 1_000_000L + gen * 9_973L;

            List<Future<Double>> futures = new ArrayList<>();
            for (Genome g : pop) {
                Callable<Double> job = () ->
                    evaluate(g, rivals, gamesPerGenome, baseSeed, shaping);
                futures.add(pool.submit(job));
            }
            List<double[]> scored = new ArrayList<>();
            double sumFit = 0.0;
            for (int i = 0; i < pop.size(); i++) {
                double f;
                try {
                    f = futures.get(i).get();
                } catch (Exception e) {
                    f = Double.NEGATIVE_INFINITY;
                }
                scored.add(new double[]{i, f});
                sumFit += f;
            }
            scored.sort(Comparator.comparingDouble((double[] a) -> -a[1]));
            Genome genBest = pop.get((int) scored.get(0)[0]);

            // ГЛАВНЫЙ СУДЬЯ — отложенная проверка. Обучающая приспособленность
            // печатается только для наблюдения за ходом поиска.
            // Проверочных партий берём БОЛЬШЕ обучающих: по 20 партиям процент
            // побед гуляет на ±10, и чемпион выбирался бы по удаче.
            double hold = holdout(genBest, holdoutRivals, Math.max(24, 2 * gamesPerGenome));
            boolean improved = hold > championHoldout;
            if (improved) {
                championHoldout = hold;
                champion = genBest;
                if (savePath != null) {
                    try {
                        champion.saveJson(savePath);
                    } catch (Exception e) {
                        System.err.println("не удалось сохранить геном: " + e.getMessage());
                    }
                }
            }
            say(String.format(Locale.ROOT,
                "поколение %2d/%d: обучение лучший=%.1f средний=%.1f | проверка=%.1f "
                + "(побед %.0f%%) %s",
                gen + 1, generations, scored.get(0)[1], sumFit / pop.size(), hold,
                100.0 * lastHoldoutWinRate, improved ? "<= НОВЫЙ ЧЕМПИОН" : ""));

            // ЗАЛ СЛАВЫ пополняется четырьмя срезами за обучение: спарринг
            // становится сильнее, но старые стили из него не исчезают.
            int every = Math.max(2, generations / 4);
            if ((gen + 1) % every == 0) {
                hallOfFame.add(genBest);
                say("  в зал славы добавлен чемпион поколения " + (gen + 1)
                    + " (всего спаррингов " + hallOfFame.size() + ")");
            }

            // ОТБОР: выживает лучшая половина, из неё — потомки. Мутация
            // затухает: сперва широкий поиск, потом тонкая настройка.
            int keep = Math.max(2, population / 2);
            List<Genome> elite = new ArrayList<>();
            for (int i = 0; i < keep; i++) {
                elite.add(pop.get((int) scored.get(i)[0]));
            }
            double rate = Math.max(0.05, mutationRate * (1.0 - 0.7 * progress));
            List<Genome> next = new ArrayList<>(elite);
            while (next.size() < population) {
                Genome pa = elite.get(rng.nextInt(elite.size()));
                Genome pb = elite.get(rng.nextInt(elite.size()));
                next.add(Genome.crossover(pa, pb, rng).mutate(rng, rate));
            }
            pop = next;
        }

        say(String.format(Locale.ROOT,
            "ОБУЧЕНИЕ ЗАВЕРШЕНО: чемпион по отложенной проверке = %.1f", championHoldout));
        pool.shutdown();
        return champion;
    }

    /**
     * CLI: {@code kelium.agents.EvoTrainer [players] [generations] [population]
     * [gamesPerGenome] [профиль|-] [out.json]}.
     */
    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int generations = args.length > 1 ? Integer.parseInt(args[1]) : 12;
        int population = args.length > 2 ? Integer.parseInt(args[2]) : 16;
        int gamesPerGenome = args.length > 3 ? Integer.parseInt(args[3]) : 16;
        String profile = args.length > 4 && !"-".equals(args[4]) ? args[4] : null;
        String fname = profile == null
            ? "strategic_" + players + "p.json"
            : "strategic_" + players + "p_" + profile + ".json";
        Path out = Paths.get(args.length > 5 ? args[5]
                : Locations.botMemory().resolve(fname).toString());

        System.out.printf(Locale.ROOT,
            "Эволюция стратега: %dp%s -> %s%n",
            players, profile == null ? "" : " [" + profile + "]", out);
        EvoTrainer trainer = new EvoTrainer(players, 42L, profile);
        trainer.train(generations, population, gamesPerGenome, 0.20, out);
        try {
            Path logPath = Path.of("reports", "balance",
                "обучение-" + players + "p" + (profile == null ? "" : "-" + profile) + ".log");
            java.nio.file.Files.createDirectories(logPath.getParent());
            java.nio.file.Files.writeString(logPath, trainer.logText(),
                java.nio.charset.StandardCharsets.UTF_8);
            System.out.println("лог обучения: " + logPath.toAbsolutePath());
        } catch (Exception e) {
            System.out.println("лог не сохранён: " + e.getMessage());
        }
    }
}
