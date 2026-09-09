package kelium;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/** Почему проседает последнее место вчетвером: что именно оно недополучает. */
public final class Место4 {
    private Место4() { }

    public static void main(String[] args) {
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 150;
        String свод = args.length > 1 ? args[1] : GameConfig.DEFAULT_RULESET;
        System.out.println("свод: " + свод + ", партий: " + партий + ", 4 игрока");
        int n = 4;
        double[] движ = new double[n];
        double[] мон = new double[n];
        double[] зад = new double[n];
        double[] пусто = new double[n];
        double[] спецВсего = new double[n];
        double[] по = new double[n];
        double[] ходов = new double[n];
        Map<String, double[]> источники = new LinkedHashMap<>();
        for (int i = 0; i < партий; i++) {
            long seed = 700_000L + 400_000L + i;
            GameState s = Setup.buildGame(GameConfig.buildCached(свод, n, seed, null, null));
            List<Agent> боты = new ArrayList<>();
            for (int seat = 0; seat < n; seat++) {
                боты.add(kelium.agents.Bots.create("balanced", seat, new Random(seed * 31 + seat), n));
            }
            new GameEngine(s, боты, ev -> {
                String t = String.valueOf(ev.get("type"));
                if (!(ev.get("seat") instanceof Number ч)) {
                    return;
                }
                int seat = ч.intValue();
                if ("order_spec".equals(t)) {
                    if (Boolean.TRUE.equals(ev.get("empty"))) {
                        пусто[seat]++;
                        return;
                    }
                    if ("coin".equals(ev.get("spec"))) {
                        мон[seat]++;
                    } else {
                        зад[seat]++;
                    }
                } else if ("maneuver".equals(t)) {
                    движ[seat]++;
                } else if ("turn_orders".equals(t)) {
                    ходов[seat]++;
                }
            }).run();
            for (int seat = 0; seat < n; seat++) {
                var bd = Scoring.scorePlayer(s, seat);
                по[seat] += bd.getOrDefault("total", 0);
                for (var e : bd.entrySet()) {
                    if ("total".equals(e.getKey())) {
                        continue;
                    }
                    источники.computeIfAbsent(e.getKey(), k -> new double[n])[seat] += e.getValue();
                }
            }
        }
        System.out.printf("%-8s %8s %9s %8s %8s %9s %8s%n",
            "место", "ходов", "ДВИЖЕНИЕ", "МОНЕТА", "ЗАДАНИЕ", "впустую", "ПО");
        for (int seat = 0; seat < n; seat++) {
            System.out.printf("место %d  %8.1f %9.2f %8.2f %8.2f %9.2f %8.1f%n", seat + 1,
                ходов[seat] / партий, движ[seat] / партий, мон[seat] / партий,
                зад[seat] / партий, пусто[seat] / партий, по[seat] / партий);
        }
        System.out.println();
        System.out.println("ОТКУДА ОЧКИ по местам (среднее за партию):");
        System.out.printf("%-26s %8s %8s %8s %8s%n", "источник", "место1", "место2", "место3", "место4");
        for (var e : источники.entrySet()) {
            double[] v = e.getValue();
            System.out.printf("%-26s %8.2f %8.2f %8.2f %8.2f%n", e.getKey(),
                v[0] / партий, v[1] / партий, v[2] / партий, v[3] / партий);
        }
    }
}
