package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * ПОДГОТОВИТЕЛЬНЫЙ РАУНД И ЧИСЛО КАРТ РЫНКА (заказ дизайнера 25.09.2026).
 *
 * <p>С галочкой первый раунд идёт без карты рынка, первая карта открывается в
 * Обновлении второго раунда, и партия длится не больше «карт рынка + 1»
 * раундов. Без галочки — прежний порядок: карта с первого раунда.
 */
class PreparatoryRoundTest {

    /** Карта рынка на начало каждого раунда: раунд → id (или null). */
    private static Map<Integer, String> play(boolean prep, int cards, List<Integer> rounds) {
        GameConfig cfg = LayoutLibrary.configFor(2, 5150L);
        cfg.ruleset.override("market.preparatory_round", prep);
        cfg.ruleset.override("market.deck_size", cards);
        GameState s = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            agents.add(Bots.create("balanced", i, new Random(77 + i), 2));
        }
        Map<Integer, String> market = new HashMap<>();
        Map<String, Object> result = new GameEngine(s, agents, ev -> {
            if ("refresh".equals(ev.get("type")) && ev.get("round") instanceof Number r) {
                market.put(r.intValue(), s.marketActive);
            }
        }).run();
        rounds.add(((Number) result.get("rounds")).intValue());
        return market;
    }

    @Test
    void подготовительныйРаундБезКартыРынка() {
        List<Integer> rounds = new ArrayList<>();
        Map<Integer, String> m = play(true, 3, rounds);
        assertTrue(m.containsKey(1), "Обновление первого раунда не отмечено");
        assertNull(m.get(1), "в подготовительном раунде карты рынка быть не должно");
        assertNotNull(m.get(2), "во втором раунде открывается первая карта рынка");
        assertTrue(rounds.get(0) <= 4, "раундов больше, чем 3 карты + подготовительный: "
            + rounds.get(0));
    }

    @Test
    void безПодготовкиКартаСПервогоРаунда() {
        List<Integer> rounds = new ArrayList<>();
        Map<Integer, String> m = play(false, 3, rounds);
        assertNotNull(m.get(1), "без подготовительного раунда карта рынка с первого раунда");
        assertTrue(rounds.get(0) <= 3, "раундов больше, чем карт рынка: " + rounds.get(0));
    }

    @Test
    void набор400ПоПечати() {
        GameConfig cfg = LayoutLibrary.configFor(2, 1L);
        assertEquals(10, cfg.content.get("market").entries.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) cfg.content.get("market").entries.get(0);
        assertEquals("m4_01", first.get("id"));
    }
}
