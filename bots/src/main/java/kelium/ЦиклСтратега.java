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
import java.util.concurrent.atomic.AtomicInteger;

import kelium.agents.Bots;
import kelium.agents.Lookahead;
import kelium.agents.PlannerAgent;
import kelium.agents.сеть.КодировщикСтратега;
import kelium.agents.сеть.Сеть;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * ОБУЧЕНИЕ СТРАТЕГА (30.09.2026) — бот думает ходом целиком и смотрит на ответ
 * соперников, а позицию судит сеть, выученная самоигрой.
 *
 * <p>ПОЧЕМУ НЕ ПРЕЖНИЙ ЦИКЛ ALPHAZERO. Он искал по отдельным мелким решениям
 * (гекс, сторона, ветка): 128 спусков видели два-три решения вперёд — меньше
 * одного своего хода. Сеть ходов за 11 поколений стала лучше случайной на 3%,
 * а замер из 8 партий показывал шум. Здесь поиск — готовый и проверенный
 * планировщик ({@link PlannerAgent}, гроссмейстер): он перебирает порядки своих
 * действий, проигрывает ход на копии и доигрывает ответный раунд соперников.
 * Слабым в нём была РУЧНАЯ ФОРМУЛА оценки позиции — её и дополняет сеть.
 *
 * <p>ЦЕЛЬ — ОТРЫВ, А НЕ ОЧКИ (заказ дизайнера 30.09.2026: «загнать соперника
 * туда, где он набирает строго меньше, чем ты»). Сеть учится предсказывать
 * итог партии для игрока: очки минус лучший чужой счёт (делённое на 10, в
 * пределах ±1). Мгновенная победа — полная победа (+1), мгновенное поражение —
 * полное (−1): условия победы ставятся выше очков. Учится по λ-возвратам
 * (TD(λ)): цель позиции — смесь оценки следующей позиции и итога партии.
 *
 * <p>ЛЕСТНИЦА ДОЛИ СЕТИ. Оценка стратега — доля сети плюс остальное от прежней
 * формулы. Начало — чистая формула (доля 0, это и есть прежний гроссмейстер).
 * Доля растёт ступенями по 0,25 и только тогда, когда замер показывает, что
 * так сильнее: бот не может стать хуже прежнего из-за молодой сети.
 *
 * <p>ЧЕСТНЫЙ ЗАМЕР. В каждом поколении на НОВЫХ раздачах играют три бота —
 * формула, лучший на сейчас и кандидат, — каждый один против трёх прежних
 * гроссмейстеров, на одних и тех же раздачах и местах. Кандидат принимается,
 * если его средняя разница с лучшим по тем же раздачам не меньше нуля. Одни
 * раздачи убирают из сравнения их удачу, новые в каждом поколении — не дают
 * «повезти» один раз и потом навсегда.
 *
 * <p>СМЕНА КОЛОД. Сеть видит карты через их самоописание, а не номера (см.
 * {@link КодировщикСтратега}). Если правила или колоды изменились, цикл не
 * начинает с нуля: сеть дообучается на партиях с новыми картами, а данные
 * партий со старыми колодами в обучение больше не идут.
 *
 * <p>Запуск: {@code kelium.TrainStrateg [поколений] [партий самоигры] [партий замера]}.
 * Папка — {@code data/selfplay/strateg}: отчёт {@code отчёт.md}, журнал
 * {@code train.log}, сети {@code value_N.bin}, лучшая — в {@code лучшая.txt}
 * («поколение доля»; поколение −1 — чистая формула).
 */
public final class ЦиклСтратега {

    private ЦиклСтратега() {
    }

    static final Path ПАПКА = Path.of(System.getProperty("kelium.strateg.dir",
        "data/selfplay/strateg"));
    /** Сколько последних поколений (с теми же колодами) идут в обучение. */
    private static final int ОКНО = 4;
    private static final int СКРЫТЫЙ1 = 192;
    private static final int СКРЫТЫЙ2 = 96;
    /** Ступень доли сети. */
    private static final double СТУПЕНЬ = 0.25;

    private static PrintStream out;

    public static void main(String[] args) throws Exception {
        Files.createDirectories(ПАПКА);
        out = журнал();
        int поколений = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        int партий = args.length > 1 ? Integer.parseInt(args[1]) : 240;
        int партийЗамера = args.length > 2 ? Integer.parseInt(args[2]) : 64;
        int ядер = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        String колоды = отпечатокКолод();
        out.println("============================================================");
        out.println(" ОБУЧЕНИЕ СТРАТЕГА — планировщик ходом целиком + сеть оценки");
        out.printf(" партий самоигры %d, партий замера %d, ядер %d%n", партий, партийЗамера, ядер);
        out.println(" правила и колоды: " + колоды);
        out.println(" остановить — закрыть окно; повторный запуск продолжит");
        out.println("============================================================");

        Path отчёт = ПАПКА.resolve("отчёт.md");
        if (!Files.exists(отчёт)) {
            Files.writeString(отчёт, "# Обучение стратега — отчёт по поколениям\n\n"
                + "Замер: каждый бот играет один против трёх прежних гроссмейстеров на одних"
                + " и тех же раздачах поколения (место — по кругу). Итог — отрыв от лучшего"
                + " соперника плюс 10 за победу, в среднем за партию. «Формула» — сам прежний"
                + " гроссмейстер (точка отсчёта), «лучший» — стратег, принятый раньше,"
                + " «кандидат» — сеть этого поколения при лучшей из двух долей. «Кандидат −"
                + " формула» — средняя разница по тем же раздачам ± её ошибка: стратег"
                + " сильнее прежнего бота, когда разница больше двух ошибок.\n\n"
                + "| поколение | колоды | сеть лучше среднего | доля сети кандидата | итог формулы"
                + " | итог лучшего | итог кандидата | кандидат − формула | побед кандидата"
                + " | принят | лучший | часов |\n"
                + "|---|---|---|---|---|---|---|---|---|---|---|---|\n",
                StandardCharsets.UTF_8);
        }

        // ЛУЧШИЙ: «поколение доля». −1 — чистая формула (доля 0).
        int лучшая = -1;
        double доляЛучшей = 0;
        if (Files.exists(ПАПКА.resolve("лучшая.txt"))) {
            String[] ч = Files.readString(ПАПКА.resolve("лучшая.txt")).trim().split("\\s+");
            лучшая = Integer.parseInt(ч[0]);
            доляЛучшей = ч.length > 1 ? Double.parseDouble(ч[1]) : 0;
        }
        Сеть лучшаяСеть = лучшая >= 0 ? Сеть.загрузить(ПАПКА.resolve("value_" + лучшая + ".bin"))
            : null;
        if (лучшаяСеть != null) {
            выложить(лучшаяСеть, доляЛучшей);
        }
        int последнее = -1;
        while (Files.exists(ПАПКА.resolve("value_" + (последнее + 1) + ".bin"))) {
            последнее++;
        }
        Сеть последняя = последнее >= 0
            ? Сеть.загрузить(ПАПКА.resolve("value_" + последнее + ".bin")) : null;

        // СМЕНА КОЛОД — дообучение, а не начало с нуля.
        Path колодыФ = ПАПКА.resolve("колоды.txt");
        String прежниеКолоды = Files.exists(колодыФ)
            ? Files.readString(колодыФ, StandardCharsets.UTF_8).trim() : колоды;
        if (!прежниеКолоды.equals(колоды)) {
            out.println();
            out.println(" КОЛОДЫ ИЛИ ПРАВИЛА ИЗМЕНИЛИСЬ: было «" + прежниеКолоды + "».");
            out.println(" Сеть не выбрасывается — она ДООБУЧАЕТСЯ на партиях с новыми картами;"
                + " данные партий со старыми колодами в обучение больше не идут.");
        }
        Files.writeString(колодыФ, колоды, StandardCharsets.UTF_8);

        for (int пок = последнее + 1; пок <= последнее + поколений; пок++) {
            long t0 = System.currentTimeMillis();
            Path данные = ПАПКА.resolve("gen" + пок + ".bin");
            Files.deleteIfExists(данные);
            if (последняя == null) {
                int n0 = партий * 2;
                out.printf("%n=== ПОКОЛЕНИЕ %d: партии прежних ботов, %d партий ===%n", пок, n0);
                играть(n0, пок, null, 0, null, данные, true);
            } else {
                out.printf("%n=== ПОКОЛЕНИЕ %d: самоигра %d партий (лучший — %s) ===%n", пок, партий,
                    лучшая < 0 ? "формула" : "поколение " + лучшая + ", доля сети " + доляЛучшей);
                играть(партий, пок, лучшаяСеть, доляЛучшей, последняя, данные, false);
            }
            Files.writeString(ПАПКА.resolve("gen" + пок + ".колоды"), колоды, StandardCharsets.UTF_8);

            List<Path> окно = new ArrayList<>();
            for (int g = пок; g >= 0 && окно.size() < ОКНО; g--) {
                Path ф = ПАПКА.resolve("gen" + g + ".bin");
                if (Files.exists(ф) && колоды.equals(колодыПоколения(g))) {
                    окно.add(ф);
                }
            }
            out.println("обучение на " + окно.size() + " поколениях");
            double[] качество = new double[1];
            Сеть кандидат = обучить(окно, последняя, качество);
            кандидат.сохранить(ПАПКА.resolve("value_" + пок + ".bin"));
            последняя = кандидат;

            // ЗАМЕР НА НОВЫХ РАЗДАЧАХ: формула, лучший и кандидат — на одних и тех же.
            long база = 7_100_000L + 10_000L * пок;
            out.println("замер: формула");
            double[] ф = замер(партийЗамера, null, 0, база);
            double[] л = ф;
            if (лучшая >= 0) {
                out.println("замер: лучший (поколение " + лучшая + ", доля " + доляЛучшей + ")");
                л = замер(партийЗамера, лучшаяСеть, доляЛучшей, база);
            }
            double д1 = Math.max(СТУПЕНЬ, доляЛучшей);
            out.println("замер: кандидат, доля " + д1);
            double[] к = замер(партийЗамера, кандидат, д1, база);
            double доля = д1;
            if (д1 < 1.0) {
                double д2 = Math.min(1.0, д1 + СТУПЕНЬ);
                out.println("замер: кандидат, доля " + д2);
                double[] к2 = замер(партийЗамера, кандидат, д2, база);
                if (среднее(к2) >= среднее(к)) {
                    к = к2;
                    доля = д2;
                }
            }
            double[] кл = разница(к, л);
            double[] кф = разница(к, ф);
            boolean принят = кл[0] >= 0;
            if (принят) {
                лучшая = пок;
                лучшаяСеть = кандидат;
                доляЛучшей = доля;
                Files.writeString(ПАПКА.resolve("лучшая.txt"),
                    пок + " " + String.format(java.util.Locale.ROOT, "%.2f", доля));
                выложить(кандидат, доля);
            }
            String с = String.format(java.util.Locale.ROOT,
                "| %d | %s | %.0f%% | %.2f | %.2f | %.2f | %.2f | %+.2f ± %.2f | %.0f%% | %s | %s | %.2f |%n",
                пок, коротко(колоды), качество[0], доля, среднее(ф), среднее(л), среднее(к),
                кф[0], кф[1], 100 * побед(к), принят ? "да" : "нет",
                лучшая < 0 ? "формула" : лучшая + " (" + String.format(java.util.Locale.ROOT,
                    "%.2f", доляЛучшей) + ")",
                (System.currentTimeMillis() - t0) / 3_600_000.0);
            Files.writeString(отчёт, с, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            out.print("ОТЧЁТ " + с);
        }
    }

    /**
     * Выложить лучшую сеть в память ботов — оттуда её берёт игра (уровень
     * «стратег», {@link kelium.agents.ОбученныйСтратег}).
     */
    private static void выложить(Сеть сеть, double доля) {
        try {
            Path ф = kelium.agents.ОбученныйСтратег.файл();
            Files.createDirectories(ф.toAbsolutePath().getParent());
            Path tmp = ф.resolveSibling(ф.getFileName() + ".tmp");
            сеть.сохранить(tmp);
            Files.writeString(kelium.agents.ОбученныйСтратег.файлДоли(),
                String.format(java.util.Locale.ROOT, "%.2f", доля));
            Files.move(tmp, ф, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            out.println("  лучшая сеть выложена в игру: " + ф);
        } catch (Exception e) {
            out.println("  не выложил сеть в игру: " + e.getMessage());
        }
    }

    private static String коротко(String колоды) {
        return колоды.replaceAll(" (market|containers|super_objectives|super_arsenal|orders)=\\S+", "")
            .replace("objectives=", "задания ").replace("arsenal=", "арсенал ");
    }

    // ======================================================================
    //  Бот
    // ======================================================================

    /** Оценка позиции сетью — в очках отрыва (мгновенная победа ~ +10). */
    public static java.util.function.ToDoubleBiFunction<GameState, Integer> оценщик(Сеть сеть) {
        return (s, место) -> 10.0 * сеть.оценить(КодировщикСтратега.закодировать(s, место));
    }

    /**
     * Стратег: гроссмейстер-планировщик, в чьей оценке позиции доля {@code доля}
     * принадлежит сети. Без сети или с долей 0 — сам прежний гроссмейстер.
     */
    public static Agent стратег(String характер, int место, Random rng, Сеть сеть, double доля) {
        PlannerAgent бот = (PlannerAgent) Bots.create(характер, Bots.Level.ГРОССМЕЙСТЕР, место, rng, 4);
        if (сеть != null && доля > 0) {
            бот.обученная = оценщик(сеть);
            бот.доляСети = доля;
        }
        return бот;
    }

    // ======================================================================
    //  Самоигра и запись позиций
    // ======================================================================

    private static final AtomicInteger СДЕЛАНО = new AtomicInteger();

    /**
     * Сыграть партии и записать позиции: после КАЖДОГО хода — стол глазами
     * каждого из четырёх игроков, с итогом партии для него.
     *
     * @param последняя сеть последнего поколения — одно место в самоигре играет
     *                  ею с долей не меньше половины: так в данные попадают
     *                  партии, где сеть ведёт, даже пока лучший — формула
     * @param прежние   первое поколение: играют прежние боты (без сети)
     */
    private static void играть(int партий, int пок, Сеть лучшая, double доля, Сеть последняя,
                               Path данные, boolean прежние) throws Exception {
        int ядер = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        ExecutorService пул = Executors.newFixedThreadPool(ядер);
        List<Future<?>> ff = new ArrayList<>();
        long t0 = System.currentTimeMillis();
        СДЕЛАНО.set(0);
        var пульс = пульс(партий, t0, "самоигра");
        for (int g = 0; g < партий; g++) {
            final long seed = 50_000_000L * (пок + 1) + g;
            final int номер = g;
            ff.add(пул.submit(() -> {
                byte[] запись = партия(seed, номер, лучшая, доля, последняя, прежние);
                synchronized (ЦиклСтратега.class) {
                    Files.write(данные, запись, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
                СДЕЛАНО.incrementAndGet();
                return null;
            }));
        }
        for (Future<?> f : ff) {
            try {
                f.get();
            } catch (Exception e) {
                out.println("  партия сорвалась: " + e.getCause());
            }
        }
        пул.shutdown();
        пульс.shutdownNow();
        out.printf("  готово: %d партий за %.0f мин%n", партий,
            (System.currentTimeMillis() - t0) / 60000.0);
    }

    private static byte[] партия(long seed, int номер, Сеть лучшая, double доля, Сеть последняя,
                                 boolean прежние) throws Exception {
        GameState s = Setup.buildGame(LayoutLibrary.configFor(4, seed));
        Random жребий = new Random(seed ^ 0x5DEECE66DL);
        int сдвиг = (int) Math.floorMod(seed, 4);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String характер = Bots.ROSTER_4.get(i);
            Random r = new Random(seed * 31 + i);
            int роль = Math.floorMod(i - сдвиг, 4);
            if (прежние) {
                // первое поколение: разные уровни прежних ботов — разные партии
                agents.add(Bots.create(характер, роль == 0 ? Bots.Level.ГРОССМЕЙСТЕР
                    : Bots.Level.ЛЮБИТЕЛЬ, i, r, 4));
            } else if (роль == 3 && жребий.nextBoolean()) {
                agents.add(Bots.create(характер, Bots.Level.ГРОССМЕЙСТЕР, i, r, 4));
            } else if (роль == 2 && последняя != null) {
                agents.add(стратег(характер, i, r, последняя, Math.max(0.5, доля)));
            } else {
                agents.add(стратег(характер, i, r, лучшая, доля));
            }
        }
        List<float[]> столы = new ArrayList<>();
        List<Integer> места = new ArrayList<>();
        GameEngine.playGame(s, agents, ev -> {
            if ("turn_end".equals(ev.get("type"))) {
                for (int k = 0; k < 4; k++) {
                    столы.add(КодировщикСтратега.закодировать(s, k));
                    места.add(k);
                }
            }
        });
        double[] итог = итоги(s);
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(bo)) {
            for (int i = 0; i < столы.size(); i++) {
                dos.writeInt(номер);
                dos.writeFloat((float) итог[места.get(i)]);
                float[] x = столы.get(i);
                dos.writeInt(x.length);
                for (float f : x) {
                    dos.writeFloat(f);
                }
            }
        }
        return bo.toByteArray();
    }

    /**
     * ИТОГ ПАРТИИ ДЛЯ КАЖДОГО ИГРОКА в [−1, 1]: отрыв от лучшего соперника,
     * делённый на 10. Мгновенная победа — +1 победителю и −1 остальным.
     */
    public static double[] итоги(GameState s) {
        int мест = s.numPlayers();
        double[] out = new double[мест];
        boolean мгновенная = s.winner != null && s.winCondition != null
            && (s.winCondition.equals("military") || s.winCondition.startsWith("all_peaks"));
        if (мгновенная) {
            for (int i = 0; i < мест; i++) {
                out[i] = i == s.winner ? 1 : -1;
            }
            return out;
        }
        int[] очки = new int[мест];
        for (int i = 0; i < мест; i++) {
            очки[i] = Scoring.scorePlayer(s, i).getOrDefault("total", 0);
        }
        for (int i = 0; i < мест; i++) {
            int лучшийДругой = Integer.MIN_VALUE;
            for (int k = 0; k < мест; k++) {
                if (k != i) {
                    лучшийДругой = Math.max(лучшийДругой, очки[k]);
                }
            }
            out[i] = Math.max(-1, Math.min(1, (очки[i] - лучшийДругой) / 10.0));
        }
        return out;
    }

    // ======================================================================
    //  Обучение сети
    // ======================================================================

    /**
     * Позиция в записи: партия, чьими глазами, номер хода по порядку, итог
     * партии для этого игрока и цель обучения (итог или λ-возврат).
     */
    private static final class Запись {
        final int партия;
        final int место;
        final int ход;
        final float итог;
        final float[] стол;
        float цель;

        Запись(int партия, int место, int ход, float итог, float[] стол) {
            this.партия = партия;
            this.место = место;
            this.ход = ход;
            this.итог = итог;
            this.стол = стол;
            this.цель = итог;
        }
    }

    private static List<Запись> прочитать(List<Path> файлы) throws Exception {
        int длина = КодировщикСтратега.длина(4);
        List<Запись> все = new ArrayList<>();
        int сдвиг = 0;
        for (Path ф : файлы) {
            int наибольшая = 0;
            int текущая = -1;
            int номер = 0;
            try (DataInputStream in = new DataInputStream(
                    new java.io.BufferedInputStream(Files.newInputStream(ф), 1 << 20))) {
                while (true) {
                    int партия = in.readInt();
                    float итог = in.readFloat();
                    int n = in.readInt();
                    float[] x = new float[n];
                    for (int i = 0; i < n; i++) {
                        x[i] = in.readFloat();
                    }
                    if (партия != текущая) {
                        текущая = партия;
                        номер = 0;
                    }
                    наибольшая = Math.max(наибольшая, партия);
                    // позиции партии записаны ходами: после каждого хода — четыре
                    // стола подряд, глазами мест 0..3
                    if (n == длина) {
                        все.add(new Запись(сдвиг + партия, номер % 4, номер / 4, итог, x));
                    }
                    номер++;
                }
            } catch (EOFException конец) {
                // файл прочитан
            }
            сдвиг += наибольшая + 1;
        }
        return все;
    }

    /** Вес будущего в λ-возврате: 1 — только итог партии, 0 — только следующая позиция. */
    private static final double ЛЯМБДА = 0.7;

    /**
     * λ-ВОЗВРАТ (TD(λ)): цель позиции — смесь оценки СЛЕДУЮЩЕЙ позиции того же
     * игрока (прежней сетью) и итога партии. Итог длинной партии вчетвером
     * шумен; следующая позиция ближе и точнее говорит, стал ли ход лучше. Так
     * сеть учится чувствовать мелкие улучшения, по которым планировщик и
     * выбирает ход.
     */
    private static void λВозвраты(List<Запись> все, Сеть прежняя) {
        Map<Long, List<Запись>> цепочки = new java.util.HashMap<>();
        for (Запись з : все) {
            цепочки.computeIfAbsent(((long) з.партия << 3) | з.место, k -> new ArrayList<>()).add(з);
        }
        for (List<Запись> ц : цепочки.values()) {
            ц.sort((a, b) -> Integer.compare(a.ход, b.ход));
            double g = ц.get(ц.size() - 1).итог;
            ц.get(ц.size() - 1).цель = (float) g;
            for (int t = ц.size() - 2; t >= 0; t--) {
                double следующая = прежняя.оценить(ц.get(t + 1).стол);
                g = (1 - ЛЯМБДА) * следующая + ЛЯМБДА * g;
                ц.get(t).цель = (float) g;
            }
        }
    }

    /**
     * Обучить сеть оценки на окне поколений (дообучая прежнюю, если она есть).
     * Проверка — отдельные ПАРТИИ (каждая десятая), а не отдельные позиции:
     * позиции одной партии похожи, и проверка на них льстила бы сети.
     *
     * @param качество [0] — насколько сеть лучше среднего на проверке, %
     */
    private static Сеть обучить(List<Path> окно, Сеть прежняя, double[] качество) throws Exception {
        List<Запись> все = прочитать(окно);
        if (прежняя != null) {
            λВозвраты(все, прежняя);
        }
        List<Запись> учебные = new ArrayList<>();
        List<Запись> проверка = new ArrayList<>();
        for (Запись з : все) {
            (з.партия % 10 == 7 ? проверка : учебные).add(з);
        }
        out.printf("  позиций %d (учебных %d, проверочных %d)%s%n", все.size(), учебные.size(),
            проверка.size(), прежняя != null ? ", цель — λ-возврат" : ", цель — итог партии");
        int длина = КодировщикСтратега.длина(4);
        Сеть сеть = прежняя != null ? копия(прежняя) : new Сеть(длина, СКРЫТЫЙ1, СКРЫТЫЙ2, true, 11);
        double среднее = 0;
        for (Запись з : учебные) {
            среднее += з.итог;
        }
        среднее /= Math.max(1, учебные.size());
        double база = 0;
        for (Запись з : проверка) {
            база += (з.итог - среднее) * (з.итог - среднее);
        }
        база /= Math.max(1, проверка.size());
        // Проверка — по ИТОГУ партии: это то, что сеть в конце концов должна
        // предсказывать, и так поколения сравнимы между собой. Необученная
        // сеть в соперники себе не годится: её случайные числа могут случайно
        // попасть ближе к среднему, чем первая эпоха.
        double лучшая = прежняя != null ? ошибка(сеть, проверка) : Double.MAX_VALUE;
        byte[] лучшаяКопия = снимок(сеть);
        Random r = new Random(17);
        int безРоста = 0;
        for (int эп = 1; эп <= 20 && безРоста < 3; эп++) {
            Collections.shuffle(учебные, r);
            float скорость = эп <= 5 ? 3e-4f : 1e-4f;
            for (int i = 0; i + 256 <= учебные.size(); i += 256) {
                float[][] xs = new float[256][];
                float[] ys = new float[256];
                for (int k = 0; k < 256; k++) {
                    xs[k] = учебные.get(i + k).стол;
                    ys[k] = учебные.get(i + k).цель;
                }
                сеть.учить(xs, ys, скорость);
            }
            double ош = ошибка(сеть, проверка);
            out.printf("  эпоха %d: ошибка на проверке %.4f (среднее %.4f)%n", эп, ош, база);
            if (ош < лучшая - 1e-5) {
                лучшая = ош;
                лучшаяКопия = снимок(сеть);
                безРоста = 0;
            } else {
                безРоста++;
            }
        }
        качество[0] = 100 * (1 - лучшая / Math.max(1e-9, база));
        return изСнимка(лучшаяКопия);
    }

    private static double ошибка(Сеть с, List<Запись> проверка) {
        double ош = 0;
        for (Запись з : проверка) {
            double d = с.оценить(з.стол) - з.итог;
            ош += d * d;
        }
        return ош / Math.max(1, проверка.size());
    }

    private static Сеть копия(Сеть с) throws Exception {
        return изСнимка(снимок(с));
    }

    private static byte[] снимок(Сеть с) throws Exception {
        Path tmp = Files.createTempFile("стратег", ".bin");
        с.сохранить(tmp);
        byte[] b = Files.readAllBytes(tmp);
        Files.delete(tmp);
        return b;
    }

    private static Сеть изСнимка(byte[] b) throws Exception {
        Path tmp = Files.createTempFile("стратег", ".bin");
        Files.write(tmp, b);
        Сеть с = Сеть.загрузить(tmp);
        Files.delete(tmp);
        return с;
    }

    // ======================================================================
    //  Замер
    // ======================================================================

    /**
     * Один бот (стратег с сетью и долей; без сети — прежний гроссмейстер)
     * против трёх прежних гроссмейстеров на раздачах {@code база + g}. Место —
     * по кругу. Ответ по партиям: [итог 0..n−1 (отрыв + 10 за победу), затем
     * победы 0/1].
     */
    static double[] замер(int партий, Сеть сеть, double доля, long база) throws Exception {
        int ядер = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        ExecutorService пул = Executors.newFixedThreadPool(ядер);
        List<Future<double[]>> ff = new ArrayList<>();
        long t0 = System.currentTimeMillis();
        СДЕЛАНО.set(0);
        var пульс = пульс(партий, t0, "замер");
        for (int g = 0; g < партий; g++) {
            final int номер = g;
            ff.add(пул.submit(() -> {
                long seed = база + номер;
                int место = номер % 4;
                GameState s = Setup.buildGame(LayoutLibrary.configFor(4, seed));
                List<Agent> agents = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    Random r = new Random(seed * 31 + i);
                    agents.add(i == место
                        ? стратег(Bots.ROSTER_4.get(i), i, r, сеть, доля)
                        : Bots.create(Bots.ROSTER_4.get(i), Bots.Level.ГРОССМЕЙСТЕР, i, r, 4));
                }
                GameEngine.playGame(s, agents, null);
                СДЕЛАНО.incrementAndGet();
                boolean победа = s.winner != null && s.winner == место;
                return new double[]{Lookahead.finalScore(s, место), победа ? 1 : 0};
            }));
        }
        double[] итог = new double[2 * партий];
        for (int g = 0; g < партий; g++) {
            double[] x;
            try {
                x = ff.get(g).get();
            } catch (Exception e) {
                out.println("  партия замера сорвалась: " + e.getCause());
                x = new double[]{0, 0};
            }
            итог[g] = x[0];
            итог[партий + g] = x[1];
        }
        пул.shutdown();
        пульс.shutdownNow();
        out.printf("  итог %.2f, побед %.0f%% (%.0f мин)%n", среднее(итог), 100 * побед(итог),
            (System.currentTimeMillis() - t0) / 60000.0);
        return итог;
    }

    /** Средний итог по партиям замера. */
    static double среднее(double[] з) {
        int n = з.length / 2;
        double s = 0;
        for (int i = 0; i < n; i++) {
            s += з[i];
        }
        return s / Math.max(1, n);
    }

    /** Доля побед в замере. */
    static double побед(double[] з) {
        int n = з.length / 2;
        double s = 0;
        for (int i = n; i < 2 * n; i++) {
            s += з[i];
        }
        return s / Math.max(1, n);
    }

    /** Средняя разница итогов по одним и тем же раздачам и её ошибка: [разница, ошибка]. */
    static double[] разница(double[] а, double[] б) {
        int n = Math.min(а.length, б.length) / 2;
        double s = 0;
        for (int i = 0; i < n; i++) {
            s += а[i] - б[i];
        }
        double ср = s / Math.max(1, n);
        double д = 0;
        for (int i = 0; i < n; i++) {
            double x = а[i] - б[i] - ср;
            д += x * x;
        }
        return new double[]{ср, Math.sqrt(д / Math.max(1, n - 1) / Math.max(1, n))};
    }

    // ======================================================================
    //  Служебное
    // ======================================================================

    /** Отпечаток правил и колод: свод и версии всех наборов карт. */
    static String отпечатокКолод() {
        GameConfig cfg = LayoutLibrary.configFor(4, 1L);
        Object версии = cfg.ruleset.raw.get("content_versions");
        StringBuilder sb = new StringBuilder(cfg.ruleset.id);
        if (версии instanceof Map<?, ?> m) {
            for (String вид : List.of("objectives", "arsenal", "market", "containers",
                    "super_objectives", "super_arsenal", "orders")) {
                if (m.get(вид) != null) {
                    sb.append(' ').append(вид).append('=').append(m.get(вид));
                }
            }
        }
        return sb.toString();
    }

    private static String колодыПоколения(int пок) throws Exception {
        Path ф = ПАПКА.resolve("gen" + пок + ".колоды");
        return Files.exists(ф) ? Files.readString(ф, StandardCharsets.UTF_8).trim() : "";
    }

    private static java.util.concurrent.ScheduledExecutorService пульс(int всего, long t0,
                                                                       String что) {
        var п = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "пульс");
            t.setDaemon(true);
            return t;
        });
        п.scheduleAtFixedRate(() -> {
            int n = СДЕЛАНО.get();
            double мин = (System.currentTimeMillis() - t0) / 60000.0;
            String осталось = n == 0 ? "считаю…" : String.format("~%.0f мин", мин / n * (всего - n));
            out.printf("  %s: партий %d/%d · %.0f мин · осталось %s%n", что, n, всего, мин, осталось);
        }, 2, 2, java.util.concurrent.TimeUnit.MINUTES);
        return п;
    }

    private static PrintStream журнал() throws Exception {
        java.io.OutputStream файл = Files.newOutputStream(ПАПКА.resolve("train.log"),
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        java.io.OutputStream консоль = new java.io.FileOutputStream(java.io.FileDescriptor.out);
        return new PrintStream(new java.io.OutputStream() {
            @Override
            public void write(int b) throws java.io.IOException {
                консоль.write(b);
                файл.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws java.io.IOException {
                консоль.write(b, off, len);
                файл.write(b, off, len);
            }

            @Override
            public void flush() throws java.io.IOException {
                консоль.flush();
                файл.flush();
            }
        }, true, StandardCharsets.UTF_8);
    }
}
