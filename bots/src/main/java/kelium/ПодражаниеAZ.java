package kelium;

import java.io.PrintStream;
import java.nio.file.Path;
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
import kelium.agents.сеть.Кодировщик;
import kelium.agents.сеть.Сеть;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * ПОДРАЖАНИЕ (26.09.2026): стартовое поколение сетей AlphaZero учится на
 * партиях прежних ботов, прежде чем начнётся самоигра.
 *
 * <p>Прежние боты (гроссмейстеры) играют между собой. На каждом их решении
 * записывается стол глазами решающего, все варианты и какой из них бот выбрал;
 * по концу партии — итог. Сеть ходов учится угадывать выбор бота, сеть оценки
 * — итог партии. Это только старт: дальше {@link ЦиклAZ} учит обе сети уже
 * на поиске и итогах самоигры, и от прежних ботов сети больше ничего не берут.
 *
 * <p>Партии не хранятся на диске: пачка партий играется, на ней сети учатся
 * один проход, пачка выбрасывается. Пока сети учатся на одной пачке, следующая
 * уже играется. Проверка — отдельные партии, которых сети не видели.
 * Останавливается, когда угадывание на проверке перестало расти.
 *
 * <p>Итог — {@code value_0.bin} и {@code policy_0.bin} в папке цикла.
 */
public final class ПодражаниеAZ {

    private ПодражаниеAZ() {
    }

    /** Одно решение прежнего бота. */
    private record Решение(int место, float[] стол, float[][] варианты, int выбор) {
    }

    /** Решение с итогом партии для решающего. */
    private record Запись(float итог, float[] стол, float[][] варианты, int выбор) {
    }

    /**
     * Обучить стартовые сети.
     *
     * @param папка          куда положить {@code value_0.bin} и {@code policy_0.bin}
     * @param наибольшеПартий предел партий обучения
     */
    public static void обучить(Path папка, int наибольшеПартий, PrintStream out) throws Exception {
        int ядер = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        int пачка = Math.max(16, ядер * 4);
        ExecutorService пул = Executors.newFixedThreadPool(ядер);
        // следующую пачку раздаёт по пулу отдельный поток, пока этот учит сети
        ExecutorService раздатчик = Executors.newSingleThreadExecutor();
        long t0 = System.currentTimeMillis();
        out.println();
        out.println("=== ПОДРАЖАНИЕ: сети сначала учатся повторять ходы прежних ботов ===");
        out.printf("  пачка %d партий, предел %d партий; остановится, когда угадывание"
            + " перестанет расти%n", пачка, наибольшеПартий);

        List<Запись> проверка = сыграть(пул, 900_000_000L, 32);
        out.printf("  проверочные партии сыграны: 32 партии, %d решений (%.1f мин)%n",
            проверка.size(), (System.currentTimeMillis() - t0) / 60000.0);

        int длина = Кодировщик.длина(4);
        Сеть оценка = new Сеть(длина, 128, 64, true, 1);
        Сеть ходы = new Сеть(длина + Кодировщик.ВАРИАНТ, 128, 64, false, 2);
        double среднееИтога = 0;
        for (Запись з : проверка) {
            среднееИтога += з.итог();
        }
        среднееИтога /= проверка.size();
        double базаОценки = 0;
        for (Запись з : проверка) {
            базаОценки += (з.итог() - среднееИтога) * (з.итог() - среднееИтога);
        }
        базаОценки /= проверка.size();
        double равновесная = 0;
        for (Запись з : проверка) {
            равновесная += Math.log(з.варианты().length);
        }
        равновесная /= проверка.size();

        double лучшееУгадывание = -1;
        int безРоста = 0;
        boolean сохранено = false;
        Random r = new Random(5);
        long сид = 800_000_000L;
        int партий = 0;
        Future<List<Запись>> следующая = раздатчик.submit(() -> сыграть(пул, 800_000_000L, пачка));
        while (партий < наибольшеПартий) {
            List<Запись> учебные = следующая.get();
            партий += пачка;
            сид += пачка;
            final long с = сид;
            следующая = партий < наибольшеПартий ? раздатчик.submit(() -> сыграть(пул, с, пачка)) : null;

            Collections.shuffle(учебные, r);
            // сеть ходов: угадать выбор прежнего бота
            for (int i = 0; i + 64 <= учебные.size(); i += 64) {
                float[][][] решения = new float[64][][];
                float[][] цели = new float[64][];
                for (int k = 0; k < 64; k++) {
                    Запись з = учебные.get(i + k);
                    решения[k] = входы(з);
                    цели[k] = new float[з.варианты().length];
                    цели[k][з.выбор()] = 1;
                }
                ходы.учитьХоды(решения, цели, 5e-4f);
            }
            // сеть оценки: итог партии
            for (int i = 0; i + 256 <= учебные.size(); i += 256) {
                float[][] xs = new float[256][];
                float[] ys = new float[256];
                for (int k = 0; k < 256; k++) {
                    xs[k] = учебные.get(i + k).стол();
                    ys[k] = учебные.get(i + k).итог();
                }
                оценка.учить(xs, ys, 3e-4f);
            }

            double[] к = качество(ходы, оценка, проверка);
            double оценкаЛучше = 100 * (1 - к[2] / базаОценки);
            double ходыЛучше = 100 * (1 - к[1] / равновесная);
            out.printf("  партий %,d · совпадает с ходом прежнего бота %.1f%% · ходы лучше"
                    + " равновесных %.0f%% · оценка лучше среднего %.0f%% · %.0f мин%n",
                партий, 100 * к[0], ходыЛучше, оценкаЛучше,
                (System.currentTimeMillis() - t0) / 60000.0);
            if (к[0] > лучшееУгадывание + 0.002) {
                лучшееУгадывание = к[0];
                безРоста = 0;
                ходы.сохранить(папка.resolve("policy_0.bin"));
                оценка.сохранить(папка.resolve("value_0.bin"));
                сохранено = true;
            } else if (++безРоста >= 6) {
                out.println("  угадывание не растёт шесть пачек подряд — подражание закончено");
                break;
            }
        }
        if (следующая != null) {
            следующая.cancel(true);
        }
        раздатчик.shutdownNow();
        пул.shutdownNow();
        if (!сохранено) {
            ходы.сохранить(папка.resolve("policy_0.bin"));
            оценка.сохранить(папка.resolve("value_0.bin"));
        }
        out.printf("  подражание готово: лучшее совпадение %.1f%%, %.0f мин%n",
            100 * лучшееУгадывание, (System.currentTimeMillis() - t0) / 60000.0);
    }

    private static float[][] входы(Запись з) {
        float[][] x = new float[з.варианты().length][];
        for (int j = 0; j < x.length; j++) {
            x[j] = Кодировщик.вход(з.стол(), з.варианты()[j]);
        }
        return x;
    }

    /** [доля угаданных выборов, перекрёстная энтропия ходов, ошибка оценки]. */
    private static double[] качество(Сеть ходы, Сеть оценка, List<Запись> проверка) {
        double угадано = 0, энтропия = 0, ошибка = 0;
        for (Запись з : проверка) {
            float[] p = ходы.вероятности(входы(з), 1f);
            int лучший = 0;
            for (int j = 1; j < p.length; j++) {
                if (p[j] > p[лучший]) {
                    лучший = j;
                }
            }
            if (лучший == з.выбор()) {
                угадано++;
            }
            энтропия -= Math.log(Math.max(1e-9, p[з.выбор()]));
            double d = оценка.оценить(з.стол()) - з.итог();
            ошибка += d * d;
        }
        int n = Math.max(1, проверка.size());
        return new double[]{угадано / n, энтропия / n, ошибка / n};
    }

    /** Сыграть {@code партий} партий прежних ботов параллельно в пуле. */
    private static List<Запись> сыграть(ExecutorService пул, long сид, int партий) throws Exception {
        List<Запись> все = new ArrayList<>();
        List<Future<List<Запись>>> ff = new ArrayList<>();
        for (int g = 0; g < партий; g++) {
            final long seed = сид + g;
            ff.add(пул.submit(() -> партия(seed)));
        }
        for (Future<List<Запись>> f : ff) {
            все.addAll(f.get());
        }
        return все;
    }

    /** Одна партия четырёх прежних ботов → их решения с итогом. */
    private static List<Запись> партия(long seed) {
        GameState s = Setup.buildGame(LayoutLibrary.configFor(4, seed));
        List<Решение> решения = new ArrayList<>();
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Agent бот = Bots.create(Bots.ROSTER_4.get(i), Bots.Level.ГРОССМЕЙСТЕР, i,
                new Random(seed * 31 + i), 4);
            agents.add(new Agent(i, бот.name) {
                @Override
                public Choice choose(GameState st, List<Choice> options, Map<String, Object> ctx) {
                    Choice c = бот.choose(st, options, ctx);
                    if (options.size() > 1) {
                        int выбор = options.indexOf(c);
                        if (выбор >= 0) {
                            String вид = ctx == null ? "" : String.valueOf(ctx.getOrDefault("kind", ""));
                            float[][] варианты = new float[options.size()][];
                            for (int k = 0; k < options.size(); k++) {
                                варианты[k] = Кодировщик.вариант(st, seat, вид, options.get(k));
                            }
                            решения.add(new Решение(seat, Кодировщик.закодировать(st, seat),
                                варианты, выбор));
                        }
                    }
                    return c;
                }
            });
        }
        GameEngine.playGame(s, agents, null);
        double[] итог = Поиск.итоги(s, 4);
        List<Запись> out = new ArrayList<>(решения.size());
        for (Решение р : решения) {
            out.add(new Запись((float) итог[р.место()], р.стол(), р.варианты(), р.выбор()));
        }
        return out;
    }
}
