package kelium.agents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * ПЕРЕБОР СИЛЫ ПРЕСЛЕДОВАНИЯ ЗАДАНИЙ ({@code objective.pursuit}) — проверка,
 * что вес вообще что-то решает, ДО того как отдавать его отбору.
 *
 * <p>Зачем отдельный инструмент. Вес добавлен по замеру «1075 выполнено против
 * 9710 сожжённых», и первый же соблазн — просто поднять число и объявить победу.
 * Но у веса два края: слишком малый оставляет костёр, слишком большой делает
 * бота рабом карт в руке — он бросает экономику и войну ради двух монет и
 * проигрывает. Поэтому здесь меряются ОБА последствия сразу: и выполнение
 * заданий, и победные очки. Число, поднимающее выполнение и роняющее очки,
 * отбором выбрано не будет, и знать это надо заранее.
 */
public final class PursuitSweep {

    private PursuitSweep() {
    }

    public static void main(String[] args) {
        int games = 150;
        long seed = 7;
        String botMemory = "data/genomes";
        double[] values = {0.0, 0.25, 1.0, 3.0, 8.0};

        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--games" -> games = Integer.parseInt(args[++i]);
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--botmemory" -> botMemory = args[++i];
                default -> { }
            }
        }
        System.setProperty("kelium.botmemory", botMemory);

        List<String> pool = new ArrayList<>();
        for (BotCatalog.Entry e : BotCatalog.ALL) {
            if (!"random".equals(e.id())) {
                pool.add(e.id());
            }
        }

        System.out.printf(Locale.ROOT,
            "%-8s %10s %10s %10s %10s%n",
            "pursuit", "выполнено", "сожжено", "доля толку", "ср. ПО");
        for (double v : values) {
            long done = 0;
            long burned = 0;
            long vpSum = 0;
            long seats = 0;
            for (int g = 0; g < games; g++) {
                long gameSeed = seed * 1_000_003L + g;
                int players = 2 + (g % 3);
                Random pickRng = new Random(gameSeed);
                GameConfig cfg = LayoutLibrary.configFor(players, gameSeed);
                GameState s = Setup.buildGame(cfg);
                List<Agent> agents = new ArrayList<>();
                for (int seat = 0; seat < players; seat++) {
                    String spec = pool.get(pickRng.nextInt(pool.size()));
                    Agent a = BotCatalog.create(spec, seat,
                        new Random(gameSeed * 131 + seat * 17 + 3), players);
                    if (a instanceof HeuristicAgent ha) {
                        ha.setWeight("objective.pursuit", v);
                    }
                    agents.add(a);
                }
                long[] c = {0, 0};
                GameEngine.playGame(s, agents, ev -> {
                    String t = String.valueOf(ev.get("type"));
                    if ("objective".equals(t)) {
                        c[0]++;
                    } else if ("objective_burn".equals(t)) {
                        c[1]++;
                    }
                });
                done += c[0];
                burned += c[1];
                for (int seat = 0; seat < players; seat++) {
                    vpSum += kelium.engine.Scoring.scorePlayer(s, seat)
                        .getOrDefault("total", 0);
                    seats++;
                }
            }
            System.out.printf(Locale.ROOT, "%-8.2f %10d %10d %9.1f%% %10.2f%n",
                v, done, burned, 100.0 * done / Math.max(1, done + burned),
                (double) vpSum / Math.max(1, seats));
        }
    }
}
