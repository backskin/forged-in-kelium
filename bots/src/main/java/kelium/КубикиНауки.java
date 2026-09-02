package kelium;

import java.util.List;
import java.util.Locale;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * КУБИКИ НАУКИ — замер нового правила треков (заказ дизайнера 02.09.2026).
 *
 * <p>Кубик теперь не переставляется, а выкладывается: занятая ячейка держится за
 * игроком до конца партии, а в запасе у него всего восемь кубиков. Правило
 * меняет сразу три вещи, и все три надо мерить, а не предполагать:
 * <ol>
 *   <li>сколько шагов игрок успевает купить и упирается ли он в запас кубиков;
 *   <li>сколько ячеек трека остаётся свободными - ячейки больше не
 *       освобождаются, и трек может «закрыться» сам собой;
 *   <li>доживает ли до конца условие «заняты все три вершины».
 * </ol>
 *
 * <p>Запуск: {@code kelium.КубикиНауки [свод] [партий] [игроков]}
 */
public final class КубикиНауки {

    private КубикиНауки() {
    }

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        String свод = args.length > 0 ? args[0] : "1.33.0";
        int партий = args.length > 1 ? Integer.parseInt(args[1]) : 60;
        int игроков = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        LayoutLibrary.setRulesetOverride(свод);

        long шагов = 0;
        long кубиковОсталось = 0;
        long упёрлись = 0;
        long вершины = 0;
        long занятыхЯчеек = 0;
        long ячеекВсего = 0;
        long раундов = 0;

        for (int i = 0; i < партий; i++) {
            GameConfig cfg = LayoutLibrary.configFor(игроков, 1000L + i);
            GameState s = Setup.buildGame(cfg);
            List<Agent> agents = new java.util.ArrayList<>();
            for (int seat = 0; seat < игроков; seat++) {
                agents.add(Bots.create("builder", seat, new java.util.Random(7L * i + seat), игроков));
            }
            GameEngine.playGame(s, agents, null);
            раундов += s.round;
            for (PlayerState p : s.players) {
                for (int st : p.techSteps.values()) {
                    шагов += st;
                }
                if (p.techCubesLeft >= 0) {
                    кубиковОсталось += p.techCubesLeft;
                    if (p.techCubesLeft == 0) {
                        упёрлись++;
                    }
                }
            }
            if (s.tech.allPeaksOccupied()) {
                вершины++;
            }
            List<Integer> caps = cfg.ruleset.stepCapacity(игроков);
            for (String track : s.tech.tracks) {
                for (int step = 1; step <= s.tech.steps; step++) {
                    Integer cap = caps.get(step - 1);
                    if (cap == null) {
                        continue;
                    }
                    ячеекВсего += cap;
                    занятыхЯчеек += Math.min(cap,
                        s.tech.occupancy.get(track).get(step - 1).size());
                }
            }
        }

        int игр = партий * игроков;
        System.out.printf(Locale.ROOT, "свод %s, партий %d, игроков %d%n", свод, партий, игроков);
        System.out.printf(Locale.ROOT, "  раундов в партии:        %.2f%n", раундов / (double) партий);
        System.out.printf(Locale.ROOT, "  шагов науки на игрока:   %.2f%n", шагов / (double) игр);
        System.out.printf(Locale.ROOT, "  кубиков осталось:        %.2f%n",
            кубиковОсталось / (double) игр);
        System.out.printf(Locale.ROOT, "  упёрлись в запас:        %.1f%% игроков%n",
            100.0 * упёрлись / игр);
        System.out.printf(Locale.ROOT, "  ячеек трека занято:      %.1f%%%n",
            100.0 * занятыхЯчеек / Math.max(1, ячеекВсего));
        System.out.printf(Locale.ROOT, "  заняты все три вершины:  %.1f%% партий%n",
            100.0 * вершины / партий);
    }
}
