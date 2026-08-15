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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.dataio.Locations;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.engine.LayoutLibrary;

/**
 * ValueTrainer — обучение ОЦЕНКИ ПОЗИЦИИ с учителем. И заодно самый прямой ответ
 * дизайнеру на вопрос «что в этой игре РЕАЛЬНО приносит победу».
 *
 * <p>Почему так, а не «нейросеть выбирает ходы». Прежняя попытка учила сеть
 * выбирать ходы методом REINFORCE и проиграла обычному боту: на партию приходится
 * одна награда, решений — сотня, и приписать заслугу конкретному решению почти
 * невозможно. Здесь задача другая: по расстановке предсказать ИСХОД. Это обычная
 * регрессия, у неё есть учитель на каждом примере и — главное — её качество
 * ПРОВЕРЯЕМО на партиях, которых модель не видела.
 *
 * <p>Как собираются данные. Играются самоигры; на каждом ходу каждого игрока
 * снимается вектор признаков позиции ({@link StateFeatures}), а в конце партии
 * каждому снимку приписывается ИТОГ его владельца (отрыв от лидера плюс премия за
 * победу). Получается набор «позиция → чем это кончилось».
 *
 * <p>Учатся ДВЕ модели на одних и тех же данных:
 * <ul>
 *   <li><b>линейная</b> — её коэффициенты читаются словами: «шаг трека науки
 *       стоит столько-то итога, лишний незапитанный слот стоит столько-то». Это
 *       и есть извлечённое из тысяч партий понимание игры, пригодное для
 *       дизайнерских решений;</li>
 *   <li><b>сеть</b> ({@link ValueNet}) — она сильнее, потому что видит
 *       нелинейности («келемий без места в хранилище бесполезен»), но словами
 *       не читается.</li>
 * </ul>
 * Обе проверяются на отложенных партиях, и в отчёте видно, кто точнее и насколько.
 */
public final class ValueTrainer {

    private ValueTrainer() {
    }

    /** Один пример: признаки позиции и то, чем эта партия для него кончилась. */
    private record Sample(double[] x, double y) {
    }

    /**
     * Собрать примеры из одной самоигры. Снимок берётся в конце каждого хода
     * каждого игрока — это естественные «моменты решения».
     */
    private static List<Sample> collect(int players, long seed, List<Genome> lineup,
                                        boolean deepBots) {
        GameConfig cfg = LayoutLibrary.configFor(players, seed);
        GameState s = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            Genome g = lineup.get(i % lineup.size());
            Random rng = new Random(seed * 37 + i);
            // ПРОСЧИТЫВАЮЩИЕ БОТЫ нужны, чтобы отделить «механика не работает» от
            // «боты ею не умеют пользоваться». Если при сильной игре военные
            // признаки по-прежнему ничего не предсказывают — дело в правилах.
            agents.add(deepBots
                ? SearchAgent.deep(i, rng, g, "deep")
                : new StrategicAgent(i, rng, g));
        }
        List<double[]> snaps = new ArrayList<>();
        List<Integer> owners = new ArrayList<>();
        GameEngine.playGame(s, agents, ev -> {
            if (!"turn_end".equals(String.valueOf(ev.get("type")))) {
                return;
            }
            if (ev.get("seat") instanceof Number n) {
                int seat = n.intValue();
                snaps.add(StateFeatures.normalized(s, seat));
                owners.add(seat);
            }
        });
        // ИТОГ для каждого места: отрыв от сильнейшего соперника плюс премия за
        // победу. Ровно та величина, которую бот и должен максимизировать.
        double[] outcome = new double[players];
        for (int i = 0; i < players; i++) {
            outcome[i] = Lookahead.finalScore(s, i) / 10.0;   // масштаб под tanh-сеть
        }
        List<Sample> out = new ArrayList<>();
        for (int i = 0; i < snaps.size(); i++) {
            out.add(new Sample(snaps.get(i), outcome[owners.get(i)]));
        }
        return out;
    }

    /** Набрать примеры из {@code games} самоигр (партии считаются параллельно). */
    private static List<Sample> dataset(int players, int games, long baseSeed,
                                       List<Genome> lineup, boolean deepBots) {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<List<Sample>>> futures = new ArrayList<>();
        for (int g = 0; g < games; g++) {
            final long seed = baseSeed + g;
            futures.add(pool.submit(
                (Callable<List<Sample>>) () -> collect(players, seed, lineup, deepBots)));
        }
        List<Sample> all = new ArrayList<>();
        for (Future<List<Sample>> f : futures) {
            try {
                all.addAll(f.get());
            } catch (Exception e) {
                System.err.println("партия сорвалась: " + e.getMessage());
            }
        }
        pool.shutdown();
        return all;
    }

    /**
     * Линейная модель — решается ТОЧНО, нормальными уравнениями с регуляризацией
     * (гребневая регрессия). Признаков всего три десятка, поэтому система 30×30
     * решается мгновенно и без единой настройки.
     *
     * <p>Сперва здесь стоял градиентный спуск, и он РАЗОШЁЛСЯ: признаки сильно
     * связаны между собой (очки, отрыв, келемий тянут друг друга), шаг 0.5 оказался
     * велик, и коэффициенты улетели в 10^126. Отчёт при этом выглядел «нормально»
     * — таблица заполнялась, просто числами-монстрами. Это ровно тот случай, когда
     * точное решение и надёжнее, и проще подобранного шага.
     */
    private static double[] fitLinear(List<Sample> data, double ridge) {
        int dim = StateFeatures.DIM;
        int n = dim + 1;                    // +1 — свободный член
        double[][] a = new double[n][n + 1];
        for (Sample sm : data) {
            double[] x = new double[n];
            System.arraycopy(sm.x(), 0, x, 0, dim);
            x[dim] = 1.0;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    a[i][j] += x[i] * x[j];
                }
                a[i][n] += x[i] * sm.y();
            }
        }
        // Гребень: небольшая добавка к диагонали. Она делает систему заведомо
        // решаемой, даже когда два признака почти дублируют друг друга.
        for (int i = 0; i < dim; i++) {
            a[i][i] += ridge * Math.max(1, data.size());
        }
        return solve(a, n);
    }

    /** Метод Гаусса с выбором главного элемента. */
    private static double[] solve(double[][] a, int n) {
        for (int col = 0; col < n; col++) {
            int piv = col;
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(a[r][col]) > Math.abs(a[piv][col])) {
                    piv = r;
                }
            }
            double[] t = a[col];
            a[col] = a[piv];
            a[piv] = t;
            double d = a[col][col];
            if (Math.abs(d) < 1e-12) {
                continue;   // вырожденный столбец — коэффициент останется нулём
            }
            for (int j = col; j <= n; j++) {
                a[col][j] /= d;
            }
            for (int r = 0; r < n; r++) {
                if (r == col || a[r][col] == 0.0) {
                    continue;
                }
                double f = a[r][col];
                for (int j = col; j <= n; j++) {
                    a[r][j] -= f * a[col][j];
                }
            }
        }
        double[] w = new double[n];
        for (int i = 0; i < n; i++) {
            w[i] = a[i][n];
        }
        return w;
    }

    private static double linearError(List<Sample> data, double[] w) {
        int dim = StateFeatures.DIM;
        double sum = 0;
        for (Sample sm : data) {
            double pred = w[dim];
            for (int i = 0; i < dim; i++) {
                pred += w[i] * sm.x()[i];
            }
            sum += (pred - sm.y()) * (pred - sm.y());
        }
        return Math.sqrt(sum / Math.max(1, data.size()));
    }

    private static double netError(List<Sample> data, ValueNet net) {
        double sum = 0;
        for (Sample sm : data) {
            double d = net.forward(sm.x()) - sm.y();
            sum += d * d;
        }
        return Math.sqrt(sum / Math.max(1, data.size()));
    }

    /** Разброс самих итогов — с ним и надо сравнивать ошибку модели. */
    private static double spread(List<Sample> data) {
        double mean = 0;
        for (Sample sm : data) {
            mean += sm.y();
        }
        mean /= Math.max(1, data.size());
        double sum = 0;
        for (Sample sm : data) {
            sum += (sm.y() - mean) * (sm.y() - mean);
        }
        return Math.sqrt(sum / Math.max(1, data.size()));
    }

    /**
     * CLI: {@code kelium.agents.ValueTrainer [игроков] [партий] [эпох] [скрытых]}.
     * По умолчанию 4 / 300 / 400 / 24.
     */
    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 300;
        int epochs = args.length > 2 ? Integer.parseInt(args[2]) : 400;
        int hidden = args.length > 3 ? Integer.parseInt(args[3]) : 24;
        // Пятый аргумент "deep" — собирать данные ПРОСЧИТЫВАЮЩИМИ ботами.
        boolean deepBots = args.length > 4 && "deep".equalsIgnoreCase(args[4]);

        // Состав самоигр — РАЗНЫЕ характеры: если данные собирать одним стилем,
        // модель выучит «как выигрывает этот стиль», а не «как выигрывают».
        List<Genome> lineup = new ArrayList<>();
        for (String c : Bots.CHARACTERS) {
            lineup.add(Bots.genome(c, players));
        }

        System.out.println("Сбор данных: " + games + " самоигр (" + players + " игрока"
            + (deepBots ? ", боты С ПРОСЧЁТОМ ВПЕРЁД" : "") + ")…");
        long t0 = System.nanoTime();
        List<Sample> train = dataset(players, games, 4_000_000L, lineup, deepBots);
        // ОТЛОЖЕННЫЕ партии — другие зёрна. Без них любые цифры точности врут.
        List<Sample> test = dataset(players, Math.max(40, games / 4), 9_000_000L, lineup,
            deepBots);
        System.out.printf(Locale.ROOT, "примеров: обучение %d, проверка %d (%.1f мин)%n",
            train.size(), test.size(), (System.nanoTime() - t0) / 6e10);

        System.out.println("Обучение линейной модели (точное решение)…");
        double[] w = fitLinear(train, 1e-4);

        System.out.println("Обучение сети…");
        ValueNet net = new ValueNet(StateFeatures.DIM, hidden, new Random(42));
        Random shuffleRng = new Random(7);
        List<Sample> order = new ArrayList<>(train);
        int batch = 64;
        for (int e = 0; e < epochs; e++) {
            java.util.Collections.shuffle(order, shuffleRng);
            for (int i = 0; i < order.size(); i += batch) {
                int end = Math.min(order.size(), i + batch);
                for (int j = i; j < end; j++) {
                    net.accumulate(order.get(j).x(), order.get(j).y());
                }
                net.applyBatch(0.08);
            }
            if ((e + 1) % Math.max(1, epochs / 5) == 0) {
                System.out.printf(Locale.ROOT, "  эпоха %d/%d: ошибка проверки %.4f%n",
                    e + 1, epochs, netError(test, net));
            }
        }

        double spreadTest = spread(test);
        double linTrain = linearError(train, w);
        double linTest = linearError(test, w);
        double netTrain = netError(train, net);
        double netTest = netError(test, net);

        Path netPath = Locations.botMemory().resolve("value_" + players + "p"
            + (deepBots ? "_deep" : "") + ".txt");
        net.save(netPath);

        // ==================== отчёт ====================
        StringBuilder sb = new StringBuilder();
        sb.append("# Что в игре приносит победу (из ").append(games)
          .append(deepBots ? " самоигр СИЛЬНЫХ ботов с просчётом вперёд)\n\n"
                           : " самоигр)\n\n");
        if (deepBots) {
            sb.append("> Данные собраны ботами, которые ПРОСЧИТЫВАЮТ ходы вперёд ")
              .append("(доигрывают копию партии). Это нужно, чтобы отделить ")
              .append("«механика не работает» от «боты ею не умеют пользоваться»: ")
              .append("если признак ничего не предсказывает и при сильной игре — ")
              .append("дело в правилах.\n\n");
        }
        sb.append("Модель училась предсказывать ИСХОД партии по расстановке. ")
          .append("Признаки — те же, которыми оценивает позицию бот. ")
          .append("Итог измеряется в «отрыве от сильнейшего соперника плюс премия ")
          .append("за победу», делённом на 10.\n\n");
        sb.append("## Точность\n\n");
        sb.append("| модель | ошибка на обучении | ошибка на ОТЛОЖЕННЫХ партиях |\n");
        sb.append("|--------|--------------------|------------------------------|\n");
        sb.append(String.format(Locale.ROOT, "| линейная | %.4f | %.4f |%n", linTrain, linTest));
        sb.append(String.format(Locale.ROOT, "| сеть | %.4f | %.4f |%n", netTrain, netTest));
        sb.append(String.format(Locale.ROOT,
            "%nДля сравнения: сам разброс итогов %.4f. Модель полезна ровно "
            + "настолько, насколько её ошибка МЕНЬШЕ разброса.%n", spreadTest));
        double gain = spreadTest <= 0 ? 0 : 100.0 * (1.0 - linTest / spreadTest);
        double gainNet = spreadTest <= 0 ? 0 : 100.0 * (1.0 - netTest / spreadTest);
        sb.append(String.format(Locale.ROOT,
            "Линейная модель объясняет %.0f%% разброса, сеть — %.0f%%.%n",
            gain, gainNet));

        sb.append("\n## Чем определяется исход — по важности\n\n");
        sb.append("Коэффициент = насколько меняется предсказанный итог, если ")
          .append("признак вырастет с нуля до своего типичного максимума. ")
          .append("Знак минус значит «это ВРЕДИТ».\n\n");
        sb.append("| признак | что это | коэффициент |\n");
        sb.append("|---------|---------|-------------|\n");
        List<int[]> idx = new ArrayList<>();
        for (int i = 0; i < StateFeatures.DIM; i++) {
            idx.add(new int[]{i});
        }
        idx.sort(Comparator.comparingDouble(a -> -Math.abs(w[a[0]])));
        for (int[] a : idx) {
            int i = a[0];
            if (Math.abs(w[i]) < 0.01) {
                continue;
            }
            sb.append(String.format(Locale.ROOT, "| %s | %s | %+.3f |%n",
                StateFeatures.NAMES.get(i), describe(i), w[i]));
        }
        sb.append("\n**Как читать.** Признаки сверху — то, за что в этой игре ")
          .append("действительно платят очками. Если у механики, на которую в ")
          .append("правилах отведено много места, коэффициент около нуля — она ")
          .append("не влияет на исход, и это повод к ней вернуться.\n");
        sb.append("\nСеть оценки сохранена: `").append(netPath).append("` — её ")
          .append("можно включить ботам (тогда позицию оценивает она, а не ")
          .append("линейная сумма весов генома).\n");

        String report = sb.toString();
        System.out.println();
        System.out.println(report);
        Path out = Path.of("reports", "balance", "что-приносит-победу-" + players + "p"
            + (deepBots ? "-сильные-боты" : "") + ".md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    /** Короткое человеческое пояснение к признаку — чтобы отчёт читался. */
    private static String describe(int i) {
        return switch (StateFeatures.NAMES.get(i)) {
            case "vp" -> "победные очки уже на руках";
            case "margin" -> "отрыв от сильнейшего соперника";
            case "coin" -> "монеты — топливо стройки";
            case "kelium" -> "келемий: и очко, и товар";
            case "ammo" -> "боеприпасы — топливо войны";
            case "trophy_pool" -> "трофеи, ещё не сданные в науку";
            case "miners_working" -> "запитанные добытчики У ЖИВОЙ жилы";
            case "kelium_reachable" -> "келемий, до которого я дотягиваюсь";
            case "storage_room" -> "свободное место в хранилище";
            case "power_plants" -> "энергостанции";
            case "energy_idle" -> "простаивающие кубики энергии (запас гибкости)";
            case "energy_hungry" -> "незапитанные ячейки — стоящие без дела здания";
            case "military_powered" -> "запитанные военные здания";
            case "strike_buildings" -> "заводы и авиабазы (только их войска бьют здания)";
            case "units" -> "войск на поле";
            case "strike_units" -> "войск, способных бить здания";
            case "tech_steps" -> "шагов по трекам науки";
            case "tech_peaks" -> "занятых вершин треков";
            case "objectives_hand" -> "карт заданий в руке";
            case "super_progress" -> "прогресс супер-задания";
            case "arsenal_installed" -> "установленных карт арсенала";
            case "containers" -> "невскрытых контейнеров";
            case "cu_tokens" -> "жетоны разрушения ЦУ (второй = победа)";
            case "enemy_cu_damage" -> "урон на чужих ЦУ — осада идёт";
            case "killable_in_range" -> "чужих жетонов, которых можно убить сейчас";
            case "my_exposed" -> "своих жетонов под ударом";
            case "tiles_flipped" -> "выработанных тайлов зарождения";
            case "tempo_economy" -> "экономика с поправкой на РАННОСТЬ раунда";
            case "buildings" -> "зданий на поле";
            default -> "";
        };
    }
}
