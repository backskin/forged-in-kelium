package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.Actions;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * ПАРТИИ БОТОВ НА СВОДЕ 1.46.0 (пять действий-развилок, приказы 5.0.0)
 * доигрываются до конца без исключений — вдвоём, втроём и вчетвером, на
 * нескольких сидах; боты играют обе ветки развилок.
 */
class ПятьДействийБотыTest {

    private static Map<String, Integer> партия(int игроков, long сид, Bots.Level уровень) {
        GameState s = Setup.buildGame(
            GameConfig.buildCached("1.46.0", игроков, сид, null, null));
        List<Agent> агенты = new ArrayList<>();
        for (int i = 0; i < игроков; i++) {
            агенты.add(Bots.create(Bots.ROSTER_4.get(i % Bots.ROSTER_4.size()), уровень, i,
                new Random(сид * 31 + i), игроков));
        }
        Map<String, Integer> ветки = new TreeMap<>();
        Map<String, Object> итог = new GameEngine(s, агенты, e -> {
            if ("action".equals(e.get("type"))) {
                assertNotNull(e.get("fork"), "в 1.46.0 каждое действие — развилка: " + e);
                ветки.merge(e.get("fork") + ">" + e.get("action"), 1, Integer::sum);
            }
        }).run();
        assertNotNull(итог.get("scores"), "партия доиграна: " + игроков + " игр., сид " + сид);
        assertTrue(s.finished, "партия закончена");
        return ветки;
    }

    @Test
    void новичкиДоигрываютНаДвоихТроихЧетверых() {
        Map<String, Integer> всего = new TreeMap<>();
        for (int игроков = 2; игроков <= 4; игроков++) {
            for (long сид = 1; сид <= 3; сид++) {
                партия(игроков, 100 * игроков + сид, Bots.Level.НОВИЧОК)
                    .forEach((k, v) -> всего.merge(k, v, Integer::sum));
            }
        }
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("target", "ветки-1.46.0.txt"),
                всего.toString());
        } catch (java.io.IOException ignored) {
            // сводка — для глаз, не для проверки
        }
        Set<String> развилки = new java.util.HashSet<>();
        for (String k : всего.keySet()) {
            развилки.add(k.substring(0, k.indexOf('>')));
        }
        assertEquals(Set.copyOf(Actions.FORK_NAMES), развилки, "сыграны все пять действий: " + всего);
        assertTrue(всего.keySet().stream().anyMatch(k -> k.endsWith(">build")),
            "ветку «построить» боты берут: " + всего);
    }

    @Test
    void планировщикиДоигрывают() {
        for (int игроков = 2; игроков <= 4; игроков++) {
            партия(игроков, 700 + игроков, Bots.Level.ЛЮБИТЕЛЬ);
        }
    }
}
