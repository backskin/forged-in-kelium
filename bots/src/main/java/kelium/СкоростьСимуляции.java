package kelium;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import kelium.agents.HeuristicAgent;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * СКОЛЬКО СТОИТ ПРОИГРАТЬ ПАРТИЮ ВПЕРЁД (23.09.2026) — чтобы выбрать, чем
 * заменить ботов. Поиск по ходам (MCTS и родня) держится на двух числах:
 * сколько стоит копия стола и сколько — доиграть партию быстрой политикой.
 *
 * <p>Запуск: {@code kelium.СкоростьСимуляции [партий]}.
 */
public final class СкоростьСимуляции {

    private СкоростьСимуляции() {
    }

    public static void main(String[] args) {
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 30;
        long ходов = 0;
        long всего = 0;
        long копий = 0;
        long наКопии = 0;
        for (int g = 0; g < партий + 5; g++) {
            long seed = 5_500_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(4, seed));
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                agents.add(new HeuristicAgent(i, new Random(seed + i), "balanced"));
            }
            long[] turns = {0};
            long t0 = System.nanoTime();
            GameEngine.playGame(s, agents, ev -> {
                if ("turn_end".equals(ev.get("type"))) {
                    turns[0]++;
                }
            });
            long t1 = System.nanoTime();
            long c0 = System.nanoTime();
            for (int k = 0; k < 20; k++) {
                s.deepCopy(k);
            }
            long c1 = System.nanoTime();
            if (g >= 5) {            // первые пять — прогрев JIT
                всего += t1 - t0;
                ходов += turns[0];
                наКопии += c1 - c0;
                копий += 20;
            }
        }
        System.out.printf("партия быстрой политикой: %.1f мс, ход: %.2f мс, копия стола: %.3f мс%n",
            всего / 1e6 / партий, всего / 1e6 / ходов, наКопии / 1e6 / копий);
    }
}
