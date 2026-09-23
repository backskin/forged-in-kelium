package kelium;

import java.io.DataOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import kelium.agents.Поиск;
import kelium.agents.сеть.Кодировщик;
import kelium.agents.сеть.Сеть;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;
import kelium.engine.step.Летопись;

/**
 * САМОИГРА (23.09.2026): четыре бота поиска играют друг с другом, и из каждой
 * позиции, где решал бот, записывается «стол глазами решающего → чем для него
 * кончилась партия». На этих записях учится {@link Сеть}.
 *
 * <p>Файл данных дописывается: запись = итог (float) + вектор кодировщика.
 *
 * <p>Запуск: {@code kelium.СамоИгра [партий] [розыгрышей] [горизонт] [файл данных]
 * [файл сети или -]}.
 */
public final class СамоИгра {

    private СамоИгра() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 28;
        int розыгрышей = args.length > 1 ? Integer.parseInt(args[1]) : 32;
        int горизонт = args.length > 2 ? Integer.parseInt(args[2]) : 99;
        Path данные = Path.of(args.length > 3 ? args[3] : "data/selfplay/gen0.bin");
        Сеть сеть = args.length > 4 && !"-".equals(args[4]) ? Сеть.загрузить(Path.of(args[4])) : null;
        Files.createDirectories(данные.toAbsolutePath().getParent());
        int ядер = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        ExecutorService пул = Executors.newFixedThreadPool(ядер);
        AtomicInteger сделано = new AtomicInteger();
        AtomicInteger записей = new AtomicInteger();
        long t0 = System.currentTimeMillis();
        List<Future<?>> ff = new ArrayList<>();
        long база = System.currentTimeMillis() % 1_000_000L * 1000;
        for (int g = 0; g < партий; g++) {
            final long seed = база + g;
            ff.add(пул.submit(() -> {
                List<float[]> записи = партия(seed, розыгрышей, горизонт, сеть);
                synchronized (СамоИгра.class) {
                    try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(данные,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
                        for (float[] з : записи) {
                            for (float x : з) {
                                dos.writeFloat(x);
                            }
                        }
                    } catch (java.io.IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                записей.addAndGet(записи.size());
                int n = сделано.incrementAndGet();
                out.printf("  партия %d/%d: %d записей, всего %d, %.0f с на партию%n", n, партий,
                    записи.size(), записей.get(),
                    (System.currentTimeMillis() - t0) / 1000.0 / n * ядер);
                return null;
            }));
        }
        for (Future<?> f : ff) {
            f.get();
        }
        пул.shutdown();
        out.printf("готово: %d партий, %d записей → %s%n", партий, записей.get(), данные);
    }

    /** Одна партия самоигры; вернёт записи «итог + вектор». */
    static List<float[]> партия(long seed, int розыгрышей, int горизонт, Сеть сеть) {
        GameState s = Setup.buildGame(LayoutLibrary.configFor(4, seed));
        Летопись летопись = new Летопись();
        List<Agent> agents = new ArrayList<>();
        List<float[]> векторы = new ArrayList<>();
        List<Integer> чьи = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Поиск п = new Поиск(i, летопись, seed * 31 + i);
            п.розыгрышей = розыгрышей;
            п.горизонтКругов = горизонт;
            п.сеть = сеть;
            agents.add(new Agent(i, п.name) {
                @Override
                public Choice choose(GameState st, List<Choice> options, Map<String, Object> ctx) {
                    if (options.size() > 1) {
                        векторы.add(Кодировщик.закодировать(st, seat));
                        чьи.add(seat);
                    }
                    return п.choose(st, options, ctx);
                }
            });
        }
        GameEngine.playGame(s, летопись.подключить(s, agents), null);
        double[] итог = Поиск.итоги(s, 4);
        List<float[]> out = new ArrayList<>();
        for (int k = 0; k < векторы.size(); k++) {
            float[] v = векторы.get(k);
            float[] з = new float[v.length + 1];
            з[0] = (float) итог[чьи.get(k)];
            System.arraycopy(v, 0, з, 1, v.length);
            out.add(з);
        }
        return out;
    }
}
