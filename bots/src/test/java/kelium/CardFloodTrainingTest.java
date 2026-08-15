package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;
import kelium.rules.Ruleset;

/**
 * ЗНАКОМСТВО С КАРТАМИ — curriculum-приём обучения (заказ дизайнера
 * 14.08.2026): ключ {@code training.card_flood_rate} должен молчать в обычной
 * партии и реально работать, когда его явно включают для обучающих партий.
 */
class CardFloodTrainingTest {

    private static List<Agent> lineup(long seed, int players) {
        List<Agent> agents = new ArrayList<>();
        List<String> chars = List.of("hawk", "dove", "balanced", "opportunist");
        for (int i = 0; i < players; i++) {
            agents.add(Bots.create(chars.get(i % chars.size()), i, new Random(seed * 31 + i),
                players));
        }
        return agents;
    }

    private static int[] countFlood(GameState s, List<Agent> agents) {
        int[] c = new int[2];
        GameEngine.playGame(s, agents, ev -> {
            if ("arsenal_flood".equals(ev.get("type"))) {
                c[0]++;
            }
            if ("objective_flood".equals(ev.get("type"))) {
                c[1]++;
            }
        });
        return c;
    }

    /** Без явного ключа обычная партия карт не сыплет — правило не меняется. */
    @Test
    void offByDefaultInARealGame() {
        GameConfig cfg = LayoutLibrary.configFor(4, 7L);
        GameState s = Setup.buildGame(cfg);
        int[] c = countFlood(s, lineup(7L, 4));
        assertEquals(0, c[0], "арсенал не должен литься без явного training.card_flood_rate");
        assertEquals(0, c[1], "задания не должны литься без явного training.card_flood_rate");
    }

    /** С включённым ключом карты РЕАЛЬНО приходят — ключ не мёртвый. */
    @Test
    void floodsCardsWhenRateIsSet() {
        GameConfig base = LayoutLibrary.configFor(4, 7L);
        Ruleset rules = base.ruleset.copy();
        rules.override("training.card_flood_rate", 0.6);
        GameConfig cfg = new GameConfig(rules, base.content, 4, 7L, base.dataRoot,
            base.boardSides, base.scenarioId, base.cuFacing, base.scenarioFile);
        GameState s = Setup.buildGame(cfg);
        int[] c = countFlood(s, lineup(7L, 4));
        assertTrue(c[0] > 0, "арсенал должен литься при training.card_flood_rate > 0");
        assertTrue(c[1] > 0, "задания должны литься при training.card_flood_rate > 0");
    }

    /** Правка не протекает в СЛЕДУЮЩУЮ партию (та же грабля, что и в RuleExperiment). */
    @Test
    void overrideDoesNotLeakIntoTheNextGame() {
        GameConfig base = LayoutLibrary.configFor(4, 7L);
        Ruleset rules = base.ruleset.copy();
        rules.override("training.card_flood_rate", 0.6);
        GameConfig floodCfg = new GameConfig(rules, base.content, 4, 7L, base.dataRoot,
            base.boardSides, base.scenarioId, base.cuFacing, base.scenarioFile);
        Setup.buildGame(floodCfg);

        GameConfig plainCfg = LayoutLibrary.configFor(4, 7L);
        GameState plain = Setup.buildGame(plainCfg);
        int[] c = countFlood(plain, lineup(7L, 4));
        assertEquals(0, c[0], "правка одной партии протекла в другую (общий объект ruleset)");
        assertEquals(0, c[1], "правка одной партии протекла в другую (общий объект ruleset)");
    }
}
