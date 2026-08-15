package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.agents.RandomAgent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.core.Agent;

/** Партия доигрывает до конца, победитель в диапазоне, прогон детерминирован. */
class GameE2ETest {

    private Map<String, Object> play(int players, long seed) {
        GameConfig cfg = GameConfig.build(players, seed);
        GameState state = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < players; seat++) {
            agents.add(new RandomAgent(seat, new Random(seed * 1000L + seat)));
        }
        return GameEngine.playGame(state, agents, null);
    }

    @Test
    void gameCompletesWithWinnerInRange() {
        for (int n : new int[]{2, 3, 4}) {
            Map<String, Object> res = play(n, 123L);
            Integer winner = (Integer) res.get("winner");
            assertNotNull(winner, "победитель определён");
            assertTrue(winner >= 0 && winner < n, "победитель в диапазоне 0.." + (n - 1));
            assertNotNull(res.get("condition"), "условие победы задано");
            int rounds = (Integer) res.get("rounds");
            // Резервный предел раундов = 8 (жёсткого лимита в 7 больше нет).
            assertTrue(rounds >= 1 && rounds <= 8, "раундов 1..8, было " + rounds);
        }
    }

    @Test
    void runIsDeterministic() {
        Map<String, Object> a = play(4, 999L);
        Map<String, Object> b = play(4, 999L);
        assertEquals(a.get("winner"), b.get("winner"), "тот же победитель при том же сиде");
        assertEquals(a.get("condition"), b.get("condition"), "то же условие победы");
        assertEquals(a.get("rounds"), b.get("rounds"), "то же число раундов");
    }
}
