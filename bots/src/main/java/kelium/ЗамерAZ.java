package kelium;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.agents.Bots;
import kelium.agents.ПоискAZ;
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
 * ЗАМЕР НАСТРОЕК AZ: один бот AlphaZero против трёх прежних ботов на одних и
 * тех же раздачах. Печатает очки, победы, время решения и то, насколько поиск
 * уверен (доля посещений у лучшего варианта корня).
 *
 * <p>Запуск: {@code kelium.ЗамерAZ <папка сетей> <поколение> <партий> <симуляций> <доля доигрывания>}.
 * Поколение 0 — без сетей.
 */
public final class ЗамерAZ {

    private ЗамерAZ() {
    }

    public static void main(String[] args) throws Exception {
        Path папка = Path.of(args[0]);
        int пок = Integer.parseInt(args[1]);
        int партий = Integer.parseInt(args[2]);
        int симуляций = Integer.parseInt(args[3]);
        double доля = Double.parseDouble(args[4]);
        Сеть оценка = пок > 0 ? Сеть.загрузить(папка.resolve("value_" + пок + ".bin")) : null;
        Сеть ходы = пок > 0 ? Сеть.загрузить(папка.resolve("policy_" + пок + ".bin")) : null;
        int ядер = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        ExecutorService пул = Executors.newFixedThreadPool(ядер);
        List<Future<double[]>> ff = new ArrayList<>();
        long t0 = System.currentTimeMillis();
        for (int g = 0; g < партий; g++) {
            final int номер = g;
            ff.add(пул.submit(() -> партия(номер, симуляций, доля, оценка, ходы)));
        }
        double очки = 0, соп = 0, побед = 0, решений = 0, мс = 0, уверенность = 0;
        for (Future<double[]> f : ff) {
            double[] x = f.get();
            очки += x[0];
            соп += x[1];
            побед += x[2];
            решений += x[3];
            мс += x[4];
            уверенность += x[5];
        }
        пул.shutdown();
        System.out.printf("пок %d, сим %d, доля %.2f: AZ %.2f / прежние %.2f, побед %.0f из %d,"
                + " решений на партию %.0f, мс на решение %.0f, доля лучшего варианта %.0f%%, всего %.1f мин%n",
            пок, симуляций, доля, очки / партий, соп / партий, побед, партий, решений / партий,
            мс / Math.max(1, решений), 100 * уверенность / Math.max(1, решений),
            (System.currentTimeMillis() - t0) / 60000.0);
    }

    private static double[] партия(int номер, int симуляций, double доля, Сеть оценка, Сеть ходы) {
        long seed = 7_000_000L + номер;
        int место = номер % 4;
        GameState s = Setup.buildGame(LayoutLibrary.configFor(4, seed));
        Летопись летопись = new Летопись();
        double[] стат = new double[3];   // решений, мс, уверенность
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (i == место) {
                ПоискAZ бот = new ПоискAZ(i, летопись, seed);
                бот.оценка = оценка;
                бот.ходы = ходы;
                бот.симуляций = симуляций;
                бот.доляДоигрывания = доля;
                agents.add(new Agent(i, бот.name) {
                    @Override
                    public Choice choose(GameState st, List<Choice> options, Map<String, Object> ctx) {
                        if (options.size() == 1) {
                            return options.get(0);
                        }
                        long t = System.nanoTime();
                        Choice c = бот.choose(st, options, ctx);
                        стат[1] += (System.nanoTime() - t) / 1e6;
                        int всего = 0, лучший = 0;
                        for (int n : бот.последнийКорень.values()) {
                            всего += n;
                            лучший = Math.max(лучший, n);
                        }
                        if (всего > 0) {
                            стат[0]++;
                            стат[2] += лучший / (double) всего;
                        }
                        return c;
                    }
                });
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
            s.winner != null && s.winner == место ? 1 : 0, стат[0], стат[1], стат[2]};
    }
}
