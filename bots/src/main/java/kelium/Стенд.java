package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.dataio.Вариант;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * СТЕНД — несколько вариантов правил в ОДНОМ процессе, на общей очереди и на
 * ОДНИХ И ТЕХ ЖЕ раздачах.
 *
 * <p><b>Чем он лучше прежней схемы.</b> Раньше каждый вариант требовал своего
 * запуска java, потому что переключатели замеров были системными свойствами.
 * Отсюда три потери:
 *
 * <ul>
 *   <li><b>ядра простаивали неровно.</b> Внутри процесса партии шли по очереди,
 *       и пять процессов на шестнадцати ядрах занимали 85% — а тот, кто
 *       закончил первым, дальше просто стоял;</li>
 *   <li><b>работа делалась заново.</b> Пять прогревов JIT, пять чтений YAML,
 *       пять куч;</li>
 *   <li><b>и главное — выборки были НЕЗАВИСИМЫМИ.</b> Разброс между полями в
 *       этой игре достигает 140%, и чтобы сквозь него разглядеть разницу в
 *       полсноса, независимым выборкам нужны сотни партий на вариант.</li>
 * </ul>
 *
 * <p><b>Общие случайные числа.</b> Здесь все варианты играют ОДНИ И ТЕ ЖЕ
 * раздачи: тот же сид, то же поле, те же характеры на тех же местах. Разница
 * считается ПО РАЗДАЧАМ, и весь разброс поля из неё уходит. Это не удобство, а
 * главный источник скорости: там, где независимым выборкам нужны сотни партий,
 * парным хватает десятков. Ускорение от ядер даёт проценты, ускорение отсюда —
 * разы.
 *
 * <p><b>Запуск.</b>
 * <pre>
 * kelium.Стенд &lt;раздач&gt; &lt;игроков&gt; &lt;уровень&gt; "вариант" "вариант" ...
 * </pre>
 * Первый вариант — эталон, остальные сравниваются с ним. Вариант пишется как
 * {@code имя;часть=значение;часть=значение}:
 * <pre>
 * "база"
 * "бпр+1;правило=actions.assembly.ammo_base=2"
 * "старые поля;поля=old"
 * "быстрые;скорость=infantry:2;скорость=vehicle:2;скорость=aircraft:3"
 * "цели;цель=vehicle:aircraft;цель=aircraft:buildings_towers"
 * </pre>
 */
public final class Стенд {

    private Стенд() {
    }

    /** Одна сыгранная партия: всё, что нужно для сравнений, числами на игрока. */
    private record Итог(double сносов, double действийБой, double холостых,
                        double нечемПлатить, double нетЦели,
                        double бпрСделано, double бпрПотрачено,
                        double трофеев, double раундов,
                        Map<String, Double> производство) {
    }

    /** Накопитель по варианту: суммы и суммы квадратов для доверительных интервалов. */
    private static final class Счёт {
        final String имя;
        final List<Итог> партии = new ArrayList<>();

        Счёт(String имя) {
            this.имя = имя;
        }
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        if (args.length < 4) {
            out.println("kelium.Стенд <раздач> <игроков> <уровень> \"вариант\" [\"вариант\"...]");
            return;
        }
        int раздач = Integer.parseInt(args[0]);
        int игроков = Integer.parseInt(args[1]);
        int уровень = Integer.parseInt(args[2]);
        List<Вариант> варианты = new ArrayList<>();
        for (int i = 3; i < args.length; i++) {
            варианты.add(Вариант.разобрать(args[i]));
        }

        // ПОТОКОВ НА ДВА МЕНЬШЕ ЧИСЛА ЯДЕР. Машина остаётся рабочей: замер,
        // после которого нельзя открыть редактор, дизайнер всё равно прервёт.
        int потоков = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        out.printf("СТЕНД · свод %s · %d раздач × %d вариантов · %d игроков · уровень %d%n",
            GameConfig.DEFAULT_RULESET, раздач, варианты.size(), игроков, уровень);
        out.printf("потоков %d, всего партий %d%n%n", потоков, раздач * варианты.size());

        ExecutorService пул = Executors.newFixedThreadPool(потоков);
        AtomicInteger сделано = new AtomicInteger();
        int всего = раздач * варианты.size();
        try {
            // ОДНА ОЧЕРЕДЬ НА ВСЕ ВАРИАНТЫ. Если бы варианты шли по очереди, в
            // хвосте каждого простаивали бы ядра; общая очередь держит их
            // занятыми до последней партии.
            Map<String, List<Future<Итог>>> задания = new java.util.LinkedHashMap<>();
            for (Вариант в : варианты) {
                List<Future<Итог>> мои = new ArrayList<>();
                for (int g = 0; g < раздач; g++) {
                    final long seed = 8_800_000L + g;
                    мои.add(пул.submit(партия(в, seed, игроков, уровень, сделано, всего, out)));
                }
                задания.put(в.имя(), мои);
            }
            List<Счёт> счета = new ArrayList<>();
            for (Вариант в : варианты) {
                Счёт с = new Счёт(в.имя());
                for (Future<Итог> f : задания.get(в.имя())) {
                    с.партии.add(f.get());
                }
                счета.add(с);
            }
            out.println();
            напечатать(out, счета, раздач);
        } finally {
            пул.shutdownNow();
        }
    }

    private static Callable<Итог> партия(Вариант в, long seed, int игроков, int уровень,
                                         AtomicInteger сделано, int всего, PrintStream out) {
        return () -> в.применить(() -> {
            List<String> состав = Bots.ROSTER_4;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(игроков, seed));
            List<Agent> agents = new ArrayList<>();
            int сдвиг = (int) Math.floorMod(seed, состав.size());
            for (int i = 0; i < игроков; i++) {
                agents.add(Bots.create(состав.get((i + сдвиг) % состав.size()),
                    Bots.Level.of(уровень), i, new Random(seed * 31 + i), игроков));
            }
            long[] счёт = new long[6];   // сносов, боёв, холостых, нечем-платить, нет-цели, —
            double[] бпрСделано = new double[игроков];
            double[] бпрПотрачено = new double[игроков];
            double[] трофеи = new double[игроков];
            Map<String, Double> произведено = new HashMap<>();

            new GameEngine(s, agents, ev -> {
                String тип = String.valueOf(ev.get("type"));
                int место = ev.get("seat") instanceof Number n ? n.intValue() : -1;
                if ("combat_hit".equals(тип)) {
                    if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                        счёт[0]++;
                    }
                    if (место >= 0) {
                        бпрПотрачено[место] += чис(ev.get("ammo"));
                        трофеи[место] += чис(ev.get("trophy"));
                    }
                } else if ("action".equals(тип)
                        && ev.get("telemetry") instanceof Map<?, ?> м) {
                    String действие = String.valueOf(ev.get("action"));
                    if ("combat".equals(действие)) {
                        счёт[1]++;
                        if (чис(м.get("battle")) <= 0) {
                            счёт[2]++;
                            if (Boolean.TRUE.equals(м.get("targets_in_reach"))) {
                                счёт[3]++;
                            } else {
                                счёт[4]++;
                            }
                        }
                    } else if ("assembly".equals(действие)) {
                        if (место >= 0) {
                            бпрСделано[место] += чис(м.get("ammo"));
                        }
                        if (м.get("units_by_type") instanceof Map<?, ?> род) {
                            synchronized (произведено) {
                                for (var e : род.entrySet()) {
                                    произведено.merge(String.valueOf(e.getKey()),
                                        чис(e.getValue()), Double::sum);
                                }
                            }
                        }
                    }
                }
            }).run();

            double бс = 0;
            double бп = 0;
            double тр = 0;
            for (int i = 0; i < игроков; i++) {
                бс += бпрСделано[i];
                бп += бпрПотрачено[i];
                тр += трофеи[i];
            }
            Map<String, Double> наИгрока = new HashMap<>();
            for (var e : произведено.entrySet()) {
                наИгрока.put(e.getKey(), e.getValue() / игроков);
            }
            int n = сделано.incrementAndGet();
            if (n % Math.max(1, всего / 20) == 0) {
                out.printf("  …%d из %d%n", n, всего);
            }
            // Всё — НА ИГРОКА, иначе варианты с разным числом мест несравнимы.
            return new Итог(счёт[0] / (double) игроков, счёт[1] / (double) игроков,
                счёт[2] / (double) игроков, счёт[3], счёт[4],
                бс / игроков, бп / игроков, тр / игроков, s.round, наИгрока);
        });
    }

    private static double чис(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    private static void напечатать(PrintStream out, List<Счёт> счета, int раздач) {
        Счёт эталон = счета.get(0);
        out.printf("%-22s %8s %8s %9s %9s %9s%n",
            "вариант", "сносов", "боёв", "холостых", "БПР сдел", "БПР трат");
        for (Счёт с : счета) {
            out.printf("%-22s %8.2f %8.2f %8.0f%% %9.2f %9.2f%n", с.имя,
                среднее(с, Итог::сносов), среднее(с, Итог::действийБой),
                100 * среднее(с, Итог::холостых) / Math.max(0.001, среднее(с, Итог::действийБой)),
                среднее(с, Итог::бпрСделано), среднее(с, Итог::бпрПотрачено));
        }

        out.println("\nПРОИЗВОДСТВО (жетонов на игрока за партию)");
        List<String> роды = List.of("infantry", "vehicle", "aircraft", "tower");
        out.printf("%-22s", "вариант");
        for (String р : роды) {
            out.printf(" %10s", р);
        }
        out.println();
        for (Счёт с : счета) {
            out.printf("%-22s", с.имя);
            double сумма = 0;
            for (String р : роды) {
                сумма += среднее(с, и -> и.производство().getOrDefault(р, 0.0));
            }
            for (String р : роды) {
                double v = среднее(с, и -> и.производство().getOrDefault(р, 0.0));
                out.printf(" %5.2f/%3.0f%%", v, сумма == 0 ? 0 : 100 * v / сумма);
            }
            out.println();
        }

        out.println("\nПАРНАЯ РАЗНИЦА С ЭТАЛОНОМ «" + эталон.имя + "» по одним раздачам");
        out.println("(± — доверительный интервал 95%; если ноль внутри, разницы не видно)");
        for (int i = 1; i < счета.size(); i++) {
            Счёт с = счета.get(i);
            out.printf("  %-20s сносов %+.2f ± %.2f   БПР сделано %+.2f ± %.2f%n", с.имя,
                разница(с, эталон, Итог::сносов)[0], разница(с, эталон, Итог::сносов)[1],
                разница(с, эталон, Итог::бпрСделано)[0], разница(с, эталон, Итог::бпрСделано)[1]);
        }
        out.printf("%nраздач на вариант: %d%n", раздач);
    }

    private static double среднее(Счёт с, java.util.function.ToDoubleFunction<Итог> что) {
        double сумма = 0;
        for (Итог и : с.партии) {
            сумма += что.applyAsDouble(и);
        }
        return с.партии.isEmpty() ? 0 : сумма / с.партии.size();
    }

    /**
     * Среднее и полуширина интервала для РАЗНИЦЫ ПО ОДНИМ РАЗДАЧАМ. Считать
     * разницу средних было бы неверно: раздачи общие, и парная разница шумит
     * многократно меньше, чем каждое среднее по отдельности.
     */
    private static double[] разница(Счёт а, Счёт б,
                                    java.util.function.ToDoubleFunction<Итог> что) {
        int n = Math.min(а.партии.size(), б.партии.size());
        double сумма = 0;
        double квадратов = 0;
        for (int i = 0; i < n; i++) {
            double d = что.applyAsDouble(а.партии.get(i)) - что.applyAsDouble(б.партии.get(i));
            сумма += d;
            квадратов += d * d;
        }
        double ср = n == 0 ? 0 : сумма / n;
        double дисп = Math.max(0, квадратов / Math.max(1, n) - ср * ср);
        return new double[]{ср, n == 0 ? 0 : 1.96 * Math.sqrt(дисп / n)};
    }
}
