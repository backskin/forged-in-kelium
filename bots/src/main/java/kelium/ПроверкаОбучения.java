package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.agents.Bots;
import kelium.agents.Genome;
import kelium.agents.PlannerAgent;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * СТАЛ ЛИ БОТ СИЛЬНЕЕ ПОСЛЕ ОБУЧЕНИЯ — единственная честная проверка.
 *
 * <p>Зачем отдельно от {@link Линейка}. Та сравнивает две настройки ОДНОГО бота.
 * Здесь сравниваются два ГЕНОМА одной линии: обученный против того, что лежал
 * до обучения. И главное — соперники в обоих случаях БЕРУТСЯ ИЗ АРХИВА,
 * необученные. Без этого замер бессмыслен: если обучились все четверо разом,
 * доли побед останутся прежними, и рост силы будет невидим.
 *
 * <p>Сравнение парное: каждая раздача играется дважды, на тех же семенах, тем
 * же составом, с той же посадкой. Разошёлся исход — значит разница в геноме, а
 * не в раскладке. Значимость по критерию Макнемара.
 *
 * <p>Запуск: {@code kelium.ПроверкаОбучения [раздач] [игроков] [папка-архива]}.
 */
public final class ПроверкаОбучения {

    private ПроверкаОбучения() {
    }

    private record Пара(boolean победаСтарого, double отрывСтарого,
                        boolean победаНового, double отрывНового) {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int раздач = args.length > 0 ? Integer.parseInt(args[0]) : 150;
        int игроков = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String архив = args.length > 2 ? args[2] : "archive-pre-retrain-2026-09-19";
        Path папкаАрхива = kelium.dataio.Locations.botMemory().resolve(архив);
        int потоков = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);

        out.printf("ПРОВЕРКА ОБУЧЕНИЯ · свод %s · %d раздач × 2 партии · %d игроков%n",
            GameConfig.DEFAULT_RULESET, раздач, игроков);
        out.println("поля: " + LayoutLibrary.describePool(игроков));
        out.println("соперники в обеих партиях — НЕОБУЧЕННЫЕ, из " + архив);
        out.println();

        ExecutorService пул = Executors.newFixedThreadPool(потоков);
        try {
            for (String характер : Bots.ROSTER_4) {
                Genome старый = взять(папкаАрхива, характер, игроков);
                Genome новый = PlannerAgent.plannerGenome(характер, игроков);
                if (старый == null) {
                    out.printf("%-10s архивного генома нет — пропущен%n", характер);
                    continue;
                }
                Map<String, Genome> соперники = new LinkedHashMap<>();
                for (String ч : Bots.ROSTER_4) {
                    if (!ч.equals(характер)) {
                        Genome g = взять(папкаАрхива, ч, игроков);
                        соперники.put(ч, g == null
                            ? PlannerAgent.plannerGenome(ч, игроков) : g);
                    }
                }

                List<Future<Пара>> будущее = new ArrayList<>();
                for (int g = 0; g < раздач; g++) {
                    final int номер = g;
                    будущее.add(пул.submit(раздача(номер, игроков, характер,
                        старый, новый, соперники)));
                }
                int победСтарого = 0;
                int победНового = 0;
                int толькоНовый = 0;
                int толькоСтарый = 0;
                double суммаРазницы = 0;
                double суммаКвадратов = 0;
                for (Future<Пара> f : будущее) {
                    Пара п = f.get();
                    if (п.победаСтарого()) {
                        победСтарого++;
                    }
                    if (п.победаНового()) {
                        победНового++;
                    }
                    if (п.победаНового() && !п.победаСтарого()) {
                        толькоНовый++;
                    }
                    if (п.победаСтарого() && !п.победаНового()) {
                        толькоСтарый++;
                    }
                    double d = п.отрывНового() - п.отрывСтарого();
                    суммаРазницы += d;
                    суммаКвадратов += d * d;
                }
                int расхождений = толькоНовый + толькоСтарый;
                double z = расхождений == 0 ? 0
                    : (толькоНовый - толькоСтарый) / Math.sqrt(расхождений);
                double среднее = суммаРазницы / раздач;
                double дисп = Math.max(0,
                    суммаКвадратов / раздач - среднее * среднее);
                double ошибка = 1.96 * Math.sqrt(дисп / раздач);

                out.printf("%-10s побед: до %d (%.0f%%) -> после %d (%.0f%%); "
                        + "расхождений %d, z = %+.2f; отрыв %+.2f ПО ± %.2f  %s%n",
                    характер, победСтарого, 100.0 * победСтарого / раздач,
                    победНового, 100.0 * победНового / раздач,
                    расхождений, z, среднее, ошибка,
                    Math.abs(z) > 1.96
                        ? (z > 0 ? "<= СИЛЬНЕЕ" : "<= СЛАБЕЕ")
                        : "(разница не различима)");
            }
        } finally {
            пул.shutdown();
        }
    }

    /** Геном линии из указанной папки: база характера плюс веса планировщика. */
    private static Genome взять(Path папка, String характер, int игроков) {
        Path файл = папка.resolve("planner_" + игроков + "p_" + характер + ".json");
        try {
            Genome сохранённый = Genome.loadJson(файл);
            Genome g = PlannerAgent.plannerDefaults(характер, игроков);
            for (String ключ : PlannerAgent.PL_KEYS) {
                double v = сохранённый.get(ключ, Double.NaN);
                if (!Double.isNaN(v)) {
                    g = g.with(ключ, v);
                }
            }
            return g;
        } catch (Exception нет) {
            return null;
        }
    }

    private static Callable<Пара> раздача(int номер, int игроков, String характер,
                                          Genome старый, Genome новый,
                                          Map<String, Genome> соперники) {
        return () -> {
            long seed = 2_200_000L + номер;
            int место = номер % игроков;
            double[] с = партия(seed, игроков, место, характер, старый, соперники);
            double[] н = партия(seed, игроков, место, характер, новый, соперники);
            return new Пара(с[0] > 0, с[1], н[0] > 0, н[1]);
        };
    }

    /** Одна партия. Возвращает {победа, отрыв}. */
    private static double[] партия(long seed, int игроков, int место, String характер,
                                   Genome мой, Map<String, Genome> соперники) {
        GameState s = Setup.buildGame(LayoutLibrary.configFor(игроков, seed));
        List<String> прочие = new ArrayList<>(соперники.keySet());
        List<Agent> agents = new ArrayList<>();
        int k = 0;
        for (int i = 0; i < игроков; i++) {
            Random r = new Random(seed * 31 + i);
            if (i == место) {
                agents.add(new PlannerAgent(i, r, мой, характер, 10, true, игроков));
            } else {
                String ч = прочие.get(k++ % прочие.size());
                agents.add(new PlannerAgent(i, r, соперники.get(ч), ч, 10, true, игроков));
            }
        }
        new GameEngine(s, agents, ev -> { }).run();
        int мои = Scoring.scorePlayer(s, место).getOrDefault("total", 0);
        int лучшийЧужой = Integer.MIN_VALUE;
        for (PlayerState p : s.players) {
            if (p.seat != место) {
                лучшийЧужой = Math.max(лучшийЧужой,
                    Scoring.scorePlayer(s, p.seat).getOrDefault("total", 0));
            }
        }
        return new double[]{мои > лучшийЧужой ? 1 : 0, мои - лучшийЧужой};
    }
}
