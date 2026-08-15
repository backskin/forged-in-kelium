package kelium;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import kelium.agents.HeuristicAgent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.core.Agent;

/** Эвристические боты доигрывают партию и реально воюют (combat_hit > 0). */
class HeuristicGameTest {

    private static final String[] PERS = {"aggressor", "defender", "economist", "aggressor"};

    private long playCountingHits(int players, long seed) {
        GameConfig cfg = GameConfig.build(players, seed);
        GameState state = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < players; seat++) {
            agents.add(new HeuristicAgent(seat, new Random(seed * 1000L + seat),
                PERS[seat % PERS.length]));
        }
        AtomicLong hits = new AtomicLong();
        Consumer<Map<String, Object>> counter = e -> {
            if ("combat_hit".equals(e.get("type"))) {
                hits.incrementAndGet();
            }
        };
        Map<String, Object> res = GameEngine.playGame(state, agents, counter);
        assertNotNull(res.get("winner"), "победитель определён");
        return hits.get();
    }

    @Tag("balance")
    @Test
    void heuristicGamesCompleteAndFight() {
        long totalHits = 0;
        for (int g = 0; g < 20; g++) {
            totalHits += playCountingHits(4, 2000L + g);
        }
        assertTrue(totalHits > 0, "за 20 партий эвристики хотя бы раз состоялся бой, было=" + totalHits);
    }

    @Test
    void heuristicDeterministic() {
        long a = playCountingHits(4, 777L);
        long b = playCountingHits(4, 777L);
        assertTrue(a == b, "детерминизм по сиду: " + a + " vs " + b);
    }
}
