package kelium;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.agents.Bots;
import kelium.agents.Поиск;
import kelium.agents.ПоискAZ;
import kelium.agents.сеть.Кодировщик;
import kelium.agents.сеть.Сеть;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.engine.step.Летопись;

/**
 * ЦИКЛ ALPHAZERO (23.09.2026): самоигра → обучение двух сетей → замер → отчёт,
 * поколение за поколением, пока не остановят.
 *
 * <p>Всё лежит в папке цикла ({@code data/selfplay/az/}): данные поколений
 * {@code genN.bin}, сети {@code value_N.bin} и {@code policy_N.bin}, отчёт
 * {@code отчёт.md}. Цикл продолжает с последнего готового поколения, если его
 * перезапустить.
 *
 * <p>Запуск: {@code kelium.ЦиклAZ [поколений] [партий в поколении] [симуляций]
 * [доля доигрывания]} — или двойным щелчком по «Обучение ботов.cmd» в корне
 * проекта (класс-ярлык {@link TrainAZ}).
 */
public final class ЦиклAZ {

    private ЦиклAZ() {
    }

    private static final Path ПАПКА = Path.of("data/selfplay/az");
    private static final int ОКНО = 3;          // поколений данных в обучении
    private static final double ДОЛЯ_ХОДОВ = 0.5; // доля решений в записи сети ходов
    /** Доля доигрывания в оценке позиции (см. ПоискAZ.доляДоигрывания). */
    private static double ДОЛЯ = 0.5;

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int поколений = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        int партий = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        int симуляций = args.length > 2 ? Integer.parseInt(args[2]) : 64;
        ДОЛЯ = args.length > 3 ? Double.parseDouble(args[3]) : 0.5;
        Files.createDirectories(ПАПКА);
        int старт = 1;
        while (Files.exists(ПАПКА.resolve("value_" + старт + ".bin"))) {
            старт++;
        }
        Сеть оценка = старт > 1 ? Сеть.загрузить(ПАПКА.resolve("value_" + (старт - 1) + ".bin")) : null;
        Сеть ходы = старт > 1 ? Сеть.загрузить(ПАПКА.resolve("policy_" + (старт - 1) + ".bin")) : null;
        Path отчёт = ПАПКА.resolve("отчёт.md");
        if (!Files.exists(отчёт)) {
            Files.writeString(отчёт, "# Цикл AlphaZero — отчёт по поколениям\n\n"
                + "Сети учатся только на партиях самоигры. «Оценка» и «ходы» — насколько сети"
                + " лучше простого угадывания на партиях, которых они не видели. Замер — один"
                + " бот AlphaZero против трёх прежних ботов (мерка, не цель).\n\n"
                + "| поколение | партий | оценка лучше среднего | ходы лучше равновесных"
                + " | замер: очки AZ / прежних | побед AZ | часов |\n|---|---|---|---|---|---|---|\n",
                StandardCharsets.UTF_8);
        }
        for (int пок = старт; пок < старт + поколений; пок++) {
            long t0 = System.currentTimeMillis();
            Path данные = ПАПКА.resolve("gen" + пок + ".bin");
            Files.deleteIfExists(данные);
            out.printf("%n=== ПОКОЛЕНИЕ %d: самоигра %d партий, %d симуляций на решение ===%n",
                пок, партий, симуляций);
            List<Сеть[]> прошлые = new ArrayList<>();
            for (int g = Math.max(1, пок - 5); g < пок; g++) {
                Path v = ПАПКА.resolve("value_" + g + ".bin");
                Path h = ПАПКА.resolve("policy_" + g + ".bin");
                if (Files.exists(v) && Files.exists(h)) {
                    прошлые.add(new Сеть[]{Сеть.загрузить(v), Сеть.загрузить(h)});
                }
            }
            самоигра(партий, симуляций, оценка, ходы, прошлые, данные, пок, out);

            List<Path> окно = new ArrayList<>();
            for (int g = Math.max(1, пок - ОКНО + 1); g <= пок; g++) {
                окно.add(ПАПКА.resolve("gen" + g + ".bin"));
            }
            out.println("обучение на поколениях " + окно);
            Сеть[] сети = new Сеть[2];
            double[] качество = обучить(окно, оценка, ходы, сети, out);
            оценка = сети[0];
            ходы = сети[1];
            оценка.сохранить(ПАПКА.resolve("value_" + пок + ".bin"));
            ходы.сохранить(ПАПКА.resolve("policy_" + пок + ".bin"));

            double[] замер = замер(8, симуляций, оценка, ходы, пок);
            double часов = (System.currentTimeMillis() - t0) / 3_600_000.0;
            String строка = String.format("| %d | %d | %.0f%% | %.0f%% | %.2f / %.2f | %.0f из 8 | %.2f |%n",
                пок, партий, качество[0], качество[1], замер[0], замер[1], замер[2], часов);
            Files.writeString(отчёт, строка, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            out.print("ОТЧЁТ " + строка);
        }
    }

    // ======================================================================
    //  Самоигра
    // ======================================================================

    private static void самоигра(int партий, int симуляций, Сеть оценка, Сеть ходы,
                                 List<Сеть[]> прошлые, Path данные,
                                 int пок, PrintStream out) throws Exception {
        int ядер = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        ExecutorService пул = Executors.newFixedThreadPool(ядер);
        List<Future<?>> ff = new ArrayList<>();
        long t0 = System.currentTimeMillis();
        int[] сделано = {0};
        for (int g = 0; g < партий; g++) {
            final long seed = 100_000_000L * пок + g;
            ff.add(пул.submit(() -> {
                byte[] запись = партия(seed, симуляций, оценка, ходы, прошлые);
                synchronized (ЦиклAZ.class) {
                    try {
                        Files.write(данные, запись, StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND);
                    } catch (java.io.IOException e) {
                        throw new RuntimeException(e);
                    }
                    сделано[0]++;
                    if (сделано[0] % 10 == 0) {
                        out.printf("  самоигра %d/%d, %.0f с на партию%n", сделано[0], партий,
                            (System.currentTimeMillis() - t0) / 1000.0 / сделано[0] * ядер);
                    }
                }
                return null;
            }));
        }
        for (Future<?> f : ff) {
            f.get();
        }
        пул.shutdown();
    }

    /** Одно решение самоигры до того, как стал известен итог партии. */
    private record Решение(int место, float[] стол, float[][] варианты, float[] доли) {
    }

    /**
     * Партия самоигры → байты записи. Запись: итог (float), место (int),
     * вектор стола, число вариантов k (int; 0 — решение идёт только в сеть
     * оценки), затем k раз «вектор варианта + доля посещений».
     */
    private static byte[] партия(long seed, int симуляций, Сеть оценка, Сеть ходы,
                                 List<Сеть[]> прошлые) {
        GameState s = Setup.buildGame(LayoutLibrary.configFor(4, seed));
        Летопись летопись = new Летопись();
        List<Решение> решения = new ArrayList<>();
        Random жребий = new Random(seed ^ 77);
        // ЛИГА СОПЕРНИКОВ: двое — текущее поколение; третий — одно из прошлых
        // поколений (если они есть); четвёртый через раз — прежний бот. Места
        // крутятся от партии к партии. Записываются решения только текущего
        // поколения: учится оно, остальные — разные соперники, чтобы не
        // выучить стиль, который бьёт лишь собственные копии.
        int сдвиг = (int) Math.floorMod(seed, 4);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int роль = Math.floorMod(i - сдвиг, 4);   // 0,1 — текущее; 2 — прошлое; 3 — прежний/текущее
            if (роль == 3 && жребий.nextBoolean()) {
                agents.add(Bots.create(Bots.ROSTER_4.get(i), Bots.Level.ГРОССМЕЙСТЕР, i,
                    new Random(seed * 31 + i), 4));
                continue;
            }
            if (роль == 2 && !прошлые.isEmpty()) {
                Сеть[] п = прошлые.get(жребий.nextInt(прошлые.size()));
                ПоискAZ старый = new ПоискAZ(i, летопись, seed * 31 + i);
                старый.оценка = п[0];
                старый.ходы = п[1];
                старый.симуляций = симуляций;
                старый.доляДоигрывания = ДОЛЯ;
                agents.add(старый);
                continue;
            }
            ПоискAZ бот = new ПоискAZ(i, летопись, seed * 31 + i);
            бот.оценка = оценка;
            бот.ходы = ходы;
            бот.симуляций = симуляций;
            бот.самоигра = true;
            бот.доляДоигрывания = ДОЛЯ;
            agents.add(new Agent(i, бот.name) {
                @Override
                public Choice choose(GameState st, List<Choice> options, Map<String, Object> ctx) {
                    if (options.size() == 1) {
                        return options.get(0);
                    }
                    float[] стол = Кодировщик.закодировать(st, seat);
                    Choice c = бот.choose(st, options, ctx);
                    float[][] варианты = null;
                    float[] доли = null;
                    if (жребий.nextDouble() < ДОЛЯ_ХОДОВ && !бот.последнийКорень.isEmpty()) {
                        String вид = ctx == null ? "" : String.valueOf(ctx.getOrDefault("kind", ""));
                        варианты = new float[options.size()][];
                        доли = new float[options.size()];
                        int всего = 0;
                        for (int k = 0; k < options.size(); k++) {
                            варианты[k] = Кодировщик.вариант(st, seat, вид, options.get(k));
                            доли[k] = бот.последнийКорень.getOrDefault(ПоискAZ.ключ(options.get(k)), 0);
                            всего += (int) доли[k];
                        }
                        if (всего > 0) {
                            for (int k = 0; k < доли.length; k++) {
                                доли[k] /= всего;
                            }
                        } else {
                            варианты = null;
                            доли = null;
                        }
                    }
                    решения.add(new Решение(seat, стол, варианты, доли));
                    return c;
                }
            });
        }
        GameEngine.playGame(s, летопись.подключить(s, agents), null);
        double[] итог = Поиск.итоги(s, 4);
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(bo)) {
            for (Решение р : решения) {
                dos.writeFloat((float) итог[р.место()]);
                dos.writeInt(р.место());
                for (float x : р.стол()) {
                    dos.writeFloat(x);
                }
                int k = р.варианты() == null ? 0 : р.варианты().length;
                dos.writeInt(k);
                for (int i = 0; i < k; i++) {
                    for (float x : р.варианты()[i]) {
                        dos.writeFloat(x);
                    }
                    dos.writeFloat(р.доли()[i]);
                }
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        return bo.toByteArray();
    }

    // ======================================================================
    //  Обучение
    // ======================================================================

    private record Запись(float итог, float[] стол, float[][] варианты, float[] доли) {
    }

    private static List<Запись> прочитать(List<Path> файлы) throws Exception {
        int длина = Кодировщик.длина(4);
        List<Запись> все = new ArrayList<>();
        for (Path ф : файлы) {
            if (!Files.exists(ф)) {
                continue;
            }
            try (DataInputStream in = new DataInputStream(
                    new java.io.BufferedInputStream(Files.newInputStream(ф), 1 << 20))) {
                while (true) {
                    float итог = in.readFloat();
                    in.readInt();
                    float[] стол = new float[длина];
                    for (int i = 0; i < длина; i++) {
                        стол[i] = in.readFloat();
                    }
                    int k = in.readInt();
                    float[][] в = k == 0 ? null : new float[k][];
                    float[] д = k == 0 ? null : new float[k];
                    for (int i = 0; i < k; i++) {
                        в[i] = new float[Кодировщик.ВАРИАНТ];
                        for (int j = 0; j < Кодировщик.ВАРИАНТ; j++) {
                            в[i][j] = in.readFloat();
                        }
                        д[i] = in.readFloat();
                    }
                    все.add(new Запись(итог, стол, в, д));
                }
            } catch (EOFException конец) {
                // файл прочитан
            }
        }
        return все;
    }

    /**
     * Обучить обе сети на окне поколений (дообучая прежние, если они есть).
     * Проверка — последние 10% записей. Возвращает, насколько сети лучше
     * простого угадывания: [оценка, ходы] в процентах.
     */
    private static double[] обучить(List<Path> окно, Сеть прежняяОценка, Сеть прежниеХоды,
                                    Сеть[] итог, PrintStream out) throws Exception {
        List<Запись> все = прочитать(окно);
        int отложено = Math.max(1, все.size() / 10);
        List<Запись> учебные = new ArrayList<>(все.subList(0, все.size() - отложено));
        List<Запись> проверка = new ArrayList<>(все.subList(все.size() - отложено, все.size()));
        out.printf("  записей %d (учебных %d, проверочных %d)%n", все.size(), учебные.size(),
            проверка.size());

        // --- сеть оценки ---
        int длина = Кодировщик.длина(4);
        Сеть оценка = прежняяОценка != null ? прежняяОценка : new Сеть(длина, 128, 64, true, 1);
        double среднее = 0;
        for (Запись з : учебные) {
            среднее += з.итог();
        }
        среднее /= учебные.size();
        double базовая = 0;
        for (Запись з : проверка) {
            базовая += (з.итог() - среднее) * (з.итог() - среднее);
        }
        базовая /= проверка.size();
        double лучшаяОценка = ошибкаОценки(оценка, проверка);
        byte[] лучшаяКопия = снимок(оценка);
        Random r = new Random(3);
        for (int эп = 1; эп <= 6; эп++) {
            Collections.shuffle(учебные, r);
            for (int i = 0; i + 256 <= учебные.size(); i += 256) {
                float[][] xs = new float[256][];
                float[] ys = new float[256];
                for (int k = 0; k < 256; k++) {
                    xs[k] = учебные.get(i + k).стол();
                    ys[k] = учебные.get(i + k).итог();
                }
                оценка.учить(xs, ys, 3e-4f);
            }
            double ош = ошибкаОценки(оценка, проверка);
            out.printf("  оценка, эпоха %d: проверочная %.4f (среднее %.4f)%n", эп, ош, базовая);
            if (ош < лучшаяОценка) {
                лучшаяОценка = ош;
                лучшаяКопия = снимок(оценка);
            }
        }
        итог[0] = изСнимка(лучшаяКопия);

        // --- сеть ходов ---
        List<Запись> учХоды = new ArrayList<>();
        for (Запись з : учебные) {
            if (з.варианты() != null) {
                учХоды.add(з);
            }
        }
        List<Запись> прХоды = new ArrayList<>();
        for (Запись з : проверка) {
            if (з.варианты() != null) {
                прХоды.add(з);
            }
        }
        Сеть ходы = прежниеХоды != null ? прежниеХоды
            : new Сеть(длина + Кодировщик.ВАРИАНТ, 128, 64, false, 2);
        double равновесная = энтропияРавновесных(прХоды);
        double лучшиеХоды = ошибкаХодов(ходы, прХоды);
        byte[] лучшиеКопия = снимок(ходы);
        for (int эп = 1; эп <= 6; эп++) {
            Collections.shuffle(учХоды, r);
            for (int i = 0; i + 64 <= учХоды.size(); i += 64) {
                float[][][] решения = new float[64][][];
                float[][] цели = new float[64][];
                for (int k = 0; k < 64; k++) {
                    Запись з = учХоды.get(i + k);
                    решения[k] = new float[з.варианты().length][];
                    for (int j = 0; j < з.варианты().length; j++) {
                        решения[k][j] = Кодировщик.вход(з.стол(), з.варианты()[j]);
                    }
                    цели[k] = з.доли();
                }
                ходы.учитьХоды(решения, цели, 3e-4f);
            }
            double ош = ошибкаХодов(ходы, прХоды);
            out.printf("  ходы, эпоха %d: проверочная %.4f (равновесные %.4f)%n", эп, ош, равновесная);
            if (ош < лучшиеХоды) {
                лучшиеХоды = ош;
                лучшиеКопия = снимок(ходы);
            }
        }
        итог[1] = изСнимка(лучшиеКопия);
        return new double[]{100 * (1 - лучшаяОценка / базовая),
            100 * (1 - лучшиеХоды / Math.max(1e-9, равновесная))};
    }

    private static double ошибкаОценки(Сеть с, List<Запись> проверка) {
        double ош = 0;
        for (Запись з : проверка) {
            double d = с.оценить(з.стол()) - з.итог();
            ош += d * d;
        }
        return ош / Math.max(1, проверка.size());
    }

    /** Перекрёстная энтропия сети ходов с долями поиска. */
    private static double ошибкаХодов(Сеть с, List<Запись> проверка) {
        double ош = 0;
        for (Запись з : проверка) {
            float[][] входы = new float[з.варианты().length][];
            for (int j = 0; j < входы.length; j++) {
                входы[j] = Кодировщик.вход(з.стол(), з.варианты()[j]);
            }
            float[] p = с.вероятности(входы, 1f);
            for (int j = 0; j < p.length; j++) {
                if (з.доли()[j] > 0) {
                    ош -= з.доли()[j] * Math.log(Math.max(1e-9, p[j]));
                }
            }
        }
        return ош / Math.max(1, проверка.size());
    }

    /** Та же энтропия, если доверять всем вариантам поровну. */
    private static double энтропияРавновесных(List<Запись> проверка) {
        double ош = 0;
        for (Запись з : проверка) {
            ош += Math.log(з.варианты().length);
        }
        return ош / Math.max(1, проверка.size());
    }

    private static byte[] снимок(Сеть с) throws Exception {
        Path tmp = Files.createTempFile("сеть", ".bin");
        с.сохранить(tmp);
        byte[] b = Files.readAllBytes(tmp);
        Files.delete(tmp);
        return b;
    }

    private static Сеть изСнимка(byte[] b) throws Exception {
        Path tmp = Files.createTempFile("сеть", ".bin");
        Files.write(tmp, b);
        Сеть с = Сеть.загрузить(tmp);
        Files.delete(tmp);
        return с;
    }

    // ======================================================================
    //  Замер
    // ======================================================================

    /** Один AZ против трёх прежних ботов: [очки AZ, очки прежних, побед AZ]. */
    private static double[] замер(int партий, int симуляций, Сеть оценка, Сеть ходы, int пок)
            throws Exception {
        int ядер = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        ExecutorService пул = Executors.newFixedThreadPool(ядер);
        List<Future<double[]>> ff = new ArrayList<>();
        for (int g = 0; g < партий; g++) {
            final int номер = g;
            ff.add(пул.submit(() -> {
                long seed = 7_000_000L + номер;          // одни и те же раздачи во всех поколениях
                int место = номер % 4;
                GameState s = Setup.buildGame(LayoutLibrary.configFor(4, seed));
                Летопись летопись = new Летопись();
                List<Agent> agents = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    if (i == место) {
                        ПоискAZ бот = new ПоискAZ(i, летопись, seed);
                        бот.оценка = оценка;
                        бот.ходы = ходы;
                        бот.симуляций = симуляций;
                        бот.доляДоигрывания = ДОЛЯ;
                        agents.add(бот);
                    } else {
                        agents.add(Bots.create(Bots.ROSTER_4.get(i), Bots.Level.ГРОССМЕЙСТЕР, i,
                            new Random(seed * 31 + i), 4));
                    }
                }
                GameEngine.playGame(s, летопись.подключить(s, agents), null);
                double соп = 0;
                for (int i = 0; i < 4; i++) {
                    if (i != место) {
                        соп += Scoring.scorePlayer(s, i).getOrDefault("total", 0) / 3.0;
                    }
                }
                return new double[]{Scoring.scorePlayer(s, место).getOrDefault("total", 0), соп,
                    s.winner != null && s.winner == место ? 1 : 0};
            }));
        }
        double[] итог = new double[3];
        for (Future<double[]> f : ff) {
            double[] x = f.get();
            итог[0] += x[0] / партий;
            итог[1] += x[1] / партий;
            итог[2] += x[2];
        }
        пул.shutdown();
        return итог;
    }
}
