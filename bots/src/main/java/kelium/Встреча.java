package kelium;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * ВСТРЕЧА — два состава ботов за одним столом, места по кругу (кто сильнее).
 *
 * <p>Запуск: {@code kelium.Встреча [партий] [игроков] [потоков] [боты А через запятую] [боты Б]}.
 * В каждой партии места делятся пополам: чётные — А, нечётные — Б; в следующей
 * партии наоборот, чтобы место за столом не решало исход.
 */
public final class Встреча {

    private Встреча() {
    }

    public static void main(String[] args) throws Exception {
        int партий = Integer.parseInt(args[0]);
        int игроков = Integer.parseInt(args[1]);
        int потоков = Integer.parseInt(args[2]);
        String[] а = args[3].split(",");
        String[] б = args[4].split(",");
        String свод = System.getProperty("kelium.свод", "1.50.0");
        ExecutorService пул = Executors.newFixedThreadPool(потоков, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        List<Future<double[]>> fs = new ArrayList<>();
        for (int g = 0; g < партий; g++) {
            final int номер = g;
            fs.add(пул.submit(() -> партия(свод, игроков, 7000L + номер, а, б, номер)));
        }
        double[] итог = new double[6];   // побед А, побед Б, очков А, очков Б, мест А, мест Б
        Map<String, Integer> ошибки = new TreeMap<>();
        for (Future<double[]> f : fs) {
            try {
                double[] r = f.get();
                for (int i = 0; i < итог.length; i++) {
                    итог[i] += r[i];
                }
            } catch (Exception e) {
                ошибки.merge(String.valueOf(e.getCause()), 1, Integer::sum);
            }
        }
        System.out.printf(Locale.ROOT, "Встреча: %d партий на %d, свод %s%n", партий, игроков, свод);
        System.out.printf(Locale.ROOT, "  А %s: побед %.1f (%.0f%% при доле мест %.0f%%), очков в среднем %.2f%n",
            String.join(",", а), итог[0], 100 * итог[0] / Math.max(1, итог[0] + итог[1]),
            100 * итог[4] / Math.max(1, итог[4] + итог[5]), итог[2] / Math.max(1, итог[4]));
        System.out.printf(Locale.ROOT, "  Б %s: побед %.1f (%.0f%%), очков в среднем %.2f%n",
            String.join(",", б), итог[1], 100 * итог[1] / Math.max(1, итог[0] + итог[1]),
            итог[3] / Math.max(1, итог[5]));
        if (!ошибки.isEmpty()) {
            System.out.println("  сбои: " + ошибки);
        }
    }

    static double[] партия(String свод, int игроков, long сид, String[] а, String[] б, int номер) {
        GameConfig cfg = GameConfig.buildCached(свод, игроков, сид, null, null);
        GameState s = Setup.buildGame(cfg);
        List<Agent> ags = new ArrayList<>();
        boolean[] этоА = new boolean[игроков];
        for (int i = 0; i < игроков; i++) {
            этоА[i] = (i + номер) % 2 == 0;
            String id = этоА[i] ? а[(i / 2) % а.length] : б[(i / 2) % б.length];
            ags.add(kelium.agents.BotCatalog.create(id, i, new Random(сид * 31 + i), игроков));
        }
        GameEngine.playGame(s, ags, ev -> { });
        int[] очки = new int[игроков];
        int лучше = Integer.MIN_VALUE;
        for (int i = 0; i < игроков; i++) {
            очки[i] = Scoring.scorePlayer(s, i).getOrDefault("total", 0);
            лучше = Math.max(лучше, очки[i]);
        }
        // победитель — по движку, если он его назвал; иначе по очкам (ничья делится)
        List<Integer> победители = new ArrayList<>();
        if (s.winner != null && s.winner >= 0) {
            победители.add(s.winner);
        } else {
            for (int i = 0; i < игроков; i++) {
                if (очки[i] == лучше) {
                    победители.add(i);
                }
            }
        }
        double[] r = new double[6];
        for (int w : победители) {
            r[этоА[w] ? 0 : 1] += 1.0 / победители.size();
        }
        for (int i = 0; i < игроков; i++) {
            r[этоА[i] ? 2 : 3] += очки[i];
            r[этоА[i] ? 4 : 5] += 1;
        }
        return r;
    }
}
