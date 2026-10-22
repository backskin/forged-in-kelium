package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * Начальный арсенал на выбор (решение Влада 02.10.2026, свод 1.50.0,
 * {@code setup.start_arsenal_offer: 2}): каждому две разные карты из восьми,
 * одна встаёт на планшет, вторая — в коробку, не в колоду арсенала.
 */
class НачальныйАрсеналНаВыборTest {

    private static boolean начальная(GameState s, String cid) {
        Map<String, Object> card = kelium.dataio.Ctx.cards(s, "arsenal").find(cid);
        return card != null && "starting".equals(card.get("kind"));
    }

    @Test
    void двеНаВыборОднаНаПланшетеВтораяВКоробке() {
        GameState s = Setup.buildGame(GameConfig.buildCached("1.50.0", 4, 21L, null, null));
        Set<String> предложено = new HashSet<>();
        for (PlayerState p : s.players) {
            assertEquals(2, p.startArsenalOffer.size(), "место " + p.seat + ": две карты на выбор");
            предложено.addAll(p.startArsenalOffer);
            for (String cid : p.arsenalInstalled) {
                assertFalse(начальная(s, cid), "до выбора начальная не стоит");
            }
        }
        assertEquals(8, предложено.size(), "восемь разных карт на четверых");

        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < s.numPlayers(); seat++) {
            agents.add(new kelium.support.Fix.FirstChoiceAgent(seat));
        }
        new GameEngine(s, agents, ev -> { }).runToRound(1);

        for (PlayerState p : s.players) {
            assertTrue(p.startArsenalOffer.isEmpty(), "выбор сделан");
            long начальных = p.arsenalInstalled.stream().filter(cid -> начальная(s, cid)).count()
                + p.arsenalHand.stream().filter(cid -> начальная(s, cid)).count();
            assertEquals(1, начальных, "место " + p.seat + ": одна начальная карта");
        }
        for (String cid : предложено) {
            assertFalse(s.decks.get("arsenal").drawPile.contains(cid) || s.decks.get("arsenal").discardPile.contains(cid),
                cid + " — не в колоде арсенала");
        }
    }
}
