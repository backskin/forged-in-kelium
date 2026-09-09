package kelium;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * НЕТ ЛИ ИМБОВОЙ КОЛОДЫ ПРИКАЗОВ.
 *
 * <p>Замер под итоговый лист дизайнера от 08.09.2026 (набор orders 2.0.0,
 * латинский квадрат спец-плашек). Колода даёт игроку две вещи: свой цикл нижних
 * приказов и свой набор спецов. Вопрос ровно один — не выигрывает ли какая-то
 * из четырёх заметно чаще прочих.
 *
 * <p>Цвета колод раздаются местам перемешиванием по сиду ({@code dealStart}),
 * так что за много партий каждая колода посидит на каждом месте. Чтобы это
 * проверить, а не предполагать, здесь считается и победность МЕСТА: если
 * разброс по местам того же порядка, что по колодам, значит видно шум хода, а
 * не силу колоды.
 *
 * <p>Победа — грубая мера: партий надо очень много, чтобы разница в пару
 * процентов перестала быть шумом. Поэтому рядом считается СРЕДНЕЕ ПРЕВЫШЕНИЕ ПО
 * над средним по партии: у него дисперсия меньше, и перегиб он ловит раньше.
 *
 * <p>Запуск: {@code mvn -pl bots exec:java -Dexec.mainClass=kelium.КолодыПриказов
 * -Dexec.args="400"} — партий на каждый состав (2/3/4 игрока).
 */
public final class КолодыПриказов {

    private static final String[] ЦВЕТА = {"blue", "scarlet", "green", "yellow"};
    private static final Map<String, String> ПОРУССКИ = Map.of(
        "blue", "голубая", "scarlet", "алая", "green", "зелёная", "yellow", "жёлтая");

    /** Накопитель на одну колоду (или на одно место). */
    private static final class Счёт {
        int партий;
        double побед;              // ничьи делятся поровну
        double ожидаемых;          // сумма 1/n по партиям: сколько побед даёт чистый случай
        double суммаПревышения;    // ПО игрока минус среднее ПО по партии
        long суммаПо;
        final Map<String, Integer> плашки = new LinkedHashMap<>();
    }

    private static String СВОД = GameConfig.DEFAULT_RULESET;

    private КолодыПриказов() {
    }

    public static void main(String[] args) {
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        // Второй аргумент — свод: нужен, чтобы сравнить одну и ту же раскладку
        // колод с разными наборами приказов (A/B по content_versions.orders).
        СВОД = args.length > 1 ? args[1] : GameConfig.DEFAULT_RULESET;
        System.out.println("свод: " + СВОД
            + ", партий на состав: " + партий + ", составы: 2/3/4");
        System.out.println();

        Map<String, Счёт> поЦвету = new LinkedHashMap<>();
        for (String c : ЦВЕТА) {
            поЦвету.put(c, new Счёт());
        }
        Map<Integer, Счёт> поМесту = new LinkedHashMap<>();
        for (int m = 0; m < 4; m++) {
            поМесту.put(m, new Счёт());
        }

        for (int n = 2; n <= 4; n++) {
            for (int i = 0; i < партий; i++) {
                long seed = 700_000L + n * 100_000L + i;
                сыграть(n, seed, поЦвету, поМесту);
            }
        }

        печать("КОЛОДА", поЦвету, ПОРУССКИ::get, партий * 3);
        System.out.println();
        Map<String, Счёт> места = new LinkedHashMap<>();
        поМесту.forEach((m, с) -> места.put(String.valueOf(m), с));
        печать("МЕСТО", места, м -> "место " + (Integer.parseInt(м) + 1), партий * 3);

        System.out.println();
        System.out.println("СПЕЦ-ПЛАШКИ: сколько раз сыграна за партию (у колоды их три,");
        System.out.println("но спец один за ход и за него конкурируют задание, арсенал,");
        System.out.println("контейнер и возврат ЦУ — сыграется далеко не каждая).");
        System.out.printf("%-10s %8s %10s %10s %10s%n",
            "колода", "партий", "ДВИЖЕНИЕ", "МОНЕТА", "ЗАДАНИЕ");
        for (String c : ЦВЕТА) {
            Счёт с = поЦвету.get(c);
            System.out.printf("%-10s %8d %10.2f %10.2f %10.2f%n", ПОРУССКИ.get(c), с.партий,
                с.плашки.getOrDefault("movement", 0) / (double) Math.max(1, с.партий),
                с.плашки.getOrDefault("coin", 0) / (double) Math.max(1, с.партий),
                с.плашки.getOrDefault("objective", 0) / (double) Math.max(1, с.партий));
        }
    }

    private static void сыграть(int n, long seed,
                                Map<String, Счёт> поЦвету, Map<Integer, Счёт> поМесту) {
        GameState s = Setup.buildGame(
            GameConfig.buildCached(СВОД, n, seed, null, null));
        List<Agent> боты = new ArrayList<>();
        for (int seat = 0; seat < n; seat++) {
            боты.add(kelium.agents.Bots.create("balanced", seat, new Random(seed * 31 + seat), n));
        }
        // Плашки считаются по событиям: order_spec (монета/задание) и maneuver
        // (движение — у него своё событие, плашка его единственный источник).
        Map<Integer, Map<String, Integer>> плашкиМест = new HashMap<>();
        new GameEngine(s, боты, ev -> {
            String t = String.valueOf(ev.get("type"));
            int seat = ev.get("seat") instanceof Number ч ? ч.intValue() : -1;
            if (seat < 0) {
                return;
            }
            if ("order_spec".equals(t)) {
                плашкиМест.computeIfAbsent(seat, k -> new HashMap<>())
                    .merge(String.valueOf(ev.get("spec")), 1, Integer::sum);
            } else if ("maneuver".equals(t)) {
                плашкиМест.computeIfAbsent(seat, k -> new HashMap<>())
                    .merge("movement", 1, Integer::sum);
            }
        }).run();

        int[] по = new int[n];
        double сумма = 0;
        for (int seat = 0; seat < n; seat++) {
            по[seat] = Scoring.scorePlayer(s, seat).getOrDefault("total", 0);
            сумма += по[seat];
        }
        double среднее = сумма / n;

        // Победа: по объявленному победителю; если движок не объявил (партия
        // догорела до предела) — по максимуму ПО, ничья делится поровну.
        double[] доля = new double[n];
        if (s.winner != null && s.winner >= 0) {
            доля[s.winner] = 1;
        } else {
            int лучшее = Integer.MIN_VALUE;
            int сколько = 0;
            for (int v : по) {
                if (v > лучшее) {
                    лучшее = v;
                    сколько = 1;
                } else if (v == лучшее) {
                    сколько++;
                }
            }
            for (int seat = 0; seat < n; seat++) {
                if (по[seat] == лучшее) {
                    доля[seat] = 1.0 / сколько;
                }
            }
        }

        for (int seat = 0; seat < n; seat++) {
            PlayerState p = s.player(seat);
            Счёт[] куда = {поЦвету.get(String.valueOf(p.orderColor)), поМесту.get(seat)};
            for (Счёт с : куда) {
                if (с == null) {
                    continue;
                }
                с.партий++;
                с.побед += доля[seat];
                с.ожидаемых += 1.0 / n;
                с.суммаПо += по[seat];
                с.суммаПревышения += по[seat] - среднее;
                плашкиМест.getOrDefault(seat, Map.of())
                    .forEach((вид, к) -> с.плашки.merge(вид, к, Integer::sum));
            }
        }
    }

    private static <K> void печать(String заголовок, Map<K, Счёт> данные,
                                   java.util.function.Function<K, String> имя, int партийВсего) {
        // ИНДЕКС, А НЕ ГОЛАЯ ДОЛЯ. Партии идут на 2, 3 и 4 игрока, и честная доля
        // побед в них разная (50 / 33 / 25 %). Поэтому доля делится на
        // ожидаемую: 1.00 — ровно по случаю, 1.20 — на пятую часть чаще.
        // Колодам это нужно на всякий случай (цвета раздаются жребием, составы
        // у них выходят почти одинаковые), а МЕСТАМ — обязательно: место 4
        // существует только вчетвером, и его голая доля с местом 1 несравнима.
        System.out.println(заголовок + " — победность и превышение ПО над средним по партии");
        System.out.printf("%-12s %8s %9s %8s %9s %10s %10s%n",
            заголовок.toLowerCase(), "партий", "побед", "индекс", "±95%", "ПО", "превыш.");
        double минИ = Double.MAX_VALUE;
        double максИ = -Double.MAX_VALUE;
        double минПрев = Double.MAX_VALUE;
        double максПрев = -Double.MAX_VALUE;
        for (var e : данные.entrySet()) {
            Счёт с = e.getValue();
            if (с.партий == 0) {
                continue;
            }
            double доля = с.побед / с.партий;
            double индекс = с.ожидаемых > 0 ? с.побед / с.ожидаемых : 0;
            // 95% для доли: 1.96 * sqrt(p(1-p)/n), пересчитанные в индекс
            double ci = 1.96 * Math.sqrt(Math.max(доля * (1 - доля), 1e-9) / с.партий)
                * с.партий / Math.max(с.ожидаемых, 1e-9);
            double прев = с.суммаПревышения / с.партий;
            минИ = Math.min(минИ, индекс);
            максИ = Math.max(максИ, индекс);
            минПрев = Math.min(минПрев, прев);
            максПрев = Math.max(максПрев, прев);
            System.out.printf("%-12s %8d %8.1f%% %8.2f %8.2f %10.1f %10.2f%n",
                имя.apply(e.getKey()), с.партий, доля * 100, индекс, ci,
                с.суммаПо / (double) с.партий, прев);
        }
        System.out.printf("  разброс: индекс %.2f, превышение %.2f ПО%n",
            максИ - минИ, максПрев - минПрев);
    }
}
