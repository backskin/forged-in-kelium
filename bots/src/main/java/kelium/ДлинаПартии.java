package kelium;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * СКОЛЬКО РАУНДОВ И СКОЛЬКО ХОДОВ идёт партия на 2/3/4 игроков.
 *
 * <p>Замер под вопрос дизайнера о реальном времени за столом. Раундов мало
 * (замер 1.32.0 дал 5.4 вчетвером), но человеку время съедают не раунды, а
 * ХОДЫ: в раунде четыре круга, значит за раунд каждый ходит четырежды, и
 * ходы идут последовательно. Здесь считается и то и другое.
 */
public final class ДлинаПартии {

    private ДлинаПартии() {
    }

    public static void main(String[] args) {
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 60;
        System.out.println("свод: " + GameConfig.DEFAULT_RULESET + ", партий на состав: " + партий);
        System.out.println();
        System.out.printf("%-9s %8s %8s %8s %10s %10s%n",
            "игроков", "раундов", "мин", "макс", "ходов", "на игрока");
        for (int n = 2; n <= 4; n++) {
            long сумма = 0;
            int мин = Integer.MAX_VALUE;
            int макс = 0;
            for (int i = 0; i < партий; i++) {
                long seed = 1000L + i;
                GameState s = Setup.buildGame(
                    GameConfig.buildCached(GameConfig.DEFAULT_RULESET, n, seed, null, null));
                List<Agent> боты = new ArrayList<>();
                for (int seat = 0; seat < n; seat++) {
                    боты.add(kelium.agents.Bots.create("balanced", seat,
                        new Random(seed * 31 + seat), n));
                }
                new GameEngine(s, боты, ev -> { }).run();
                int r = s.round;
                сумма += r;
                мин = Math.min(мин, r);
                макс = Math.max(макс, r);
            }
            double среднее = сумма / (double) партий;
            // Ходов за партию: раундов x 4 круга x игроков. Круги — структура
            // раунда, а не переменная: каждый играет по одной карте приказа в круг.
            double ходов = среднее * 4 * n;
            System.out.printf("%-9d %8.2f %8d %8d %10.0f %10.0f%n",
                n, среднее, мин, макс, ходов, среднее * 4);
        }
    }
}
