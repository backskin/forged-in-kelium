package kelium;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.agents.Genome;
import kelium.agents.StrategicAgent;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.dataio.GameConfig;
import kelium.dataio.Locations;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * CombatProbe — ПОЧЕМУ БОТЫ МАЛО УБИВАЮТ (вопрос дизайнера 12.08.2026).
 *
 * <p>Разбирает цепочку, которая должна привести к убитому жетону, и показывает,
 * на каком звене она рвётся:
 *
 * <ol>
 *   <li>у игрока вообще ЕСТЬ войска на поле;</li>
 *   <li>приказ с БОЕМ вскрыт (действие «Бой» доступно);</li>
 *   <li>в момент Боя рядом с чужим жетоном СТОИТ своё войско (телеметрия
 *       {@code could_fight});</li>
 *   <li>удар нанесён (хватило БПР и бот выбрал атаку);</li>
 *   <li>жетон добит до конца — урон копится до Обновления, поэтому одного
 *       попадания мало.</li>
 * </ol>
 *
 * <p>Запуск: {@code java -cp ... kelium.CombatProbe [players] [games]}
 */
public final class CombatProbe {

    private CombatProbe() {
    }

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 50;

        Genome genome;
        try {
            genome = Genome.loadJson(Locations.botMemory()
                .resolve("strategic_" + players + "p.json"));
        } catch (Exception e) {
            genome = Genome.defaults();
        }

        int combatPlayed = 0;          // сколько раз сыграно действие «Бой»
        int combatCouldFight = 0;      // из них: цель рядом ЕСТЬ
        int combatWithBattles = 0;     // из них: удар нанесён
        int hits = 0;
        int kills = 0;
        int movementPlayed = 0;
        int movesMade = 0;
        int unitsBuilt = 0;
        int unitsAliveEnd = 0;
        int gamesWithNoKill = 0;
        int ammoEndSum = 0;
        Map<Integer, Integer> nearestAtEnd = new HashMap<>();

        for (int g = 0; g < games; g++) {
            long seed = 3000 + g;
            GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, players,
                seed, null, null);
            GameState s = Setup.buildGame(cfg);
            List<Agent> agents = new ArrayList<>();
            for (int seat = 0; seat < players; seat++) {
                agents.add(new StrategicAgent(seat, new Random(seed * 137 + seat), genome));
            }
            final int[] killsHere = {0};
            GameEngine.playGame(s, agents, ev -> {
                if ("combat_hit".equals(ev.get("type"))) {
                    if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                        killCount[0]++;
                        killsHere[0]++;
                    }
                    return;
                }
                if (!"action".equals(ev.get("type"))) {
                    return;
                }
                String name = String.valueOf(ev.get("action"));
                Map<?, ?> tel = ev.get("telemetry") instanceof Map<?, ?> m ? m : Map.of();
                if ("combat".equals(name)) {
                    combatCount[0]++;
                    if (Boolean.TRUE.equals(tel.get("could_fight"))) {
                        couldCount[0]++;
                    }
                    int battles = num(tel.get("battle"));
                    if (battles > 0) {
                        withBattles[0]++;
                        hitCount[0] += battles;
                    }
                } else if ("movement".equals(name)) {
                    moveActionCount[0]++;
                    moveCount[0] += num(tel.get("moves"));
                }
            });

            combatPlayed += combatCount[0];
            combatCouldFight += couldCount[0];
            combatWithBattles += withBattles[0];
            hits += hitCount[0];
            kills += killCount[0];
            movementPlayed += moveActionCount[0];
            movesMade += moveCount[0];
            combatCount[0] = 0;
            couldCount[0] = 0;
            withBattles[0] = 0;
            hitCount[0] = 0;
            killCount[0] = 0;
            moveActionCount[0] = 0;
            moveCount[0] = 0;
            if (killsHere[0] == 0) {
                gamesWithNoKill++;
            }

            for (PlayerState p : s.players) {
                unitsBuilt += p.units.size();
                unitsAliveEnd += p.unitsOnField().size();
                ammoEndSum += p.resources.ammo();
                for (UnitToken u : p.unitsOnField()) {
                    int d = nearestEnemyDistance(s, p.seat, u.hexId);
                    nearestAtEnd.merge(Math.min(d, 9), 1, Integer::sum);
                }
            }
        }

        System.out.println("=== Зонд боя: " + games + " партий, " + players + " игроков ===");
        System.out.println();
        System.out.println("Цепочка «чтобы жетон погиб» — где она рвётся:");
        System.out.printf("  1. войск на поле к концу ............ %.2f на игрока "
            + "(всего создано %.2f)%n",
            unitsAliveEnd / (double) (games * players), unitsBuilt / (double) (games * players));
        System.out.printf("  2. действие «Бой» сыграно ........... %.2f раз за партию%n",
            combatPlayed / (double) games);
        System.out.printf("  3. из них цель рядом БЫЛА .......... %d из %d (%.1f%%)%n",
            combatCouldFight, combatPlayed, pct(combatCouldFight, combatPlayed));
        System.out.printf("  4. из них удар НАНЕСЁН ............. %d из %d (%.1f%%)%n",
            combatWithBattles, combatCouldFight, pct(combatWithBattles, combatCouldFight));
        System.out.printf("  5. попаданий %.2f за партию → убито %.2f жетонов%n",
            hits / (double) games, kills / (double) games);
        System.out.println();
        System.out.printf("Партий вообще БЕЗ убийств: %d из %d (%.1f%%)%n",
            gamesWithNoKill, games, pct(gamesWithNoKill, games));
        System.out.printf("Движение: %.2f действий за партию, %.2f перемещений%n",
            movementPlayed / (double) games, movesMade / (double) games);
        System.out.printf("Боеприпасов осталось на руках к концу: %.2f на игрока%n",
            ammoEndSum / (double) (games * players));
        System.out.println();
        System.out.println("Насколько далеко войска стоят от ближайшего чужого жетона "
            + "(в гексах, к концу партии):");
        for (int d = 0; d <= 9; d++) {
            Integer c = nearestAtEnd.get(d);
            if (c != null) {
                System.out.printf("  %s%d: %d войск%n", d == 9 ? "≥" : " ", d, c);
            }
        }
    }

    // счётчики одной партии (лямбда требует финальных ссылок)
    private static final int[] combatCount = {0};
    private static final int[] couldCount = {0};
    private static final int[] withBattles = {0};
    private static final int[] hitCount = {0};
    private static final int[] killCount = {0};
    private static final int[] moveActionCount = {0};
    private static final int[] moveCount = {0};

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static double pct(int part, int total) {
        return total == 0 ? 0.0 : 100.0 * part / total;
    }

    /** Расстояние в гексах до ближайшего чужого жетона (9 — дальше 8 или нет цели). */
    private static int nearestEnemyDistance(GameState s, int seat, String from) {
        java.util.Deque<String> queue = new java.util.ArrayDeque<>();
        Map<String, Integer> dist = new HashMap<>();
        queue.add(from);
        dist.put(from, 0);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            int d = dist.get(cur);
            for (PlayerState p : s.players) {
                if (p.seat == seat) {
                    continue;
                }
                for (Token t : all(p)) {
                    if (cur.equals(t.hexId())) {
                        return d;
                    }
                }
            }
            if (d >= 8) {
                continue;
            }
            for (String nb : s.field.neighbors(cur)) {
                if (!dist.containsKey(nb)) {
                    dist.put(nb, d + 1);
                    queue.add(nb);
                }
            }
        }
        return 9;
    }

    private static List<Token> all(PlayerState p) {
        List<Token> out = new ArrayList<>();
        out.addAll(p.unitsOnField());
        out.addAll(p.buildingsOnField());
        return out;
    }
}
