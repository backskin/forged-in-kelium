package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * КАРТА РЫНКА УХОДИТ ИЗ ИГРЫ НАВСЕГДА.
 *
 * <p>Правило дизайнера (15.08.2026): колода рынка тасуется на подготовке, каждый
 * раунд с неё снимается одна карта и в колоду НЕ возвращается. Восемь карт —
 * восемь раундов, и последняя карта означает последний раунд.
 *
 * <p>Почему нужен сторож. Это правило уже ломали, причём починкой другой ошибки:
 * чинили «карта рынка залипает на несколько раундов» и стали класть отыгравшую
 * карту в сброс. А колода при пустом доборе тасует именно сброс — карты пошли по
 * второму кругу, и за партию одна и та же карта выпадала дважды. Заметил это
 * дизайнер, играя, а не тест: до сих пор никто не проверял, что карты рынка за
 * партию НЕ ПОВТОРЯЮТСЯ.
 */
class MarketDeckTest {

    /** Какие карты рынка были активны по раундам за одну партию. */
    private static List<String> marketCardsOf(long seed) {
        int players = 4;
        GameState s = Setup.buildGame(LayoutLibrary.configFor(players, seed));
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            agents.add(Bots.create("balanced", i, new Random(seed * 31 + i), players));
        }
        List<String> seen = new ArrayList<>();
        GameEngine.playGame(s, agents, ev -> {
            if ("refresh".equals(String.valueOf(ev.get("type")))
                    && s.marketActive != null
                    && (seen.isEmpty() || !seen.get(seen.size() - 1).equals(s.marketActive))) {
                seen.add(s.marketActive);
            }
        });
        return seen;
    }

    @Test
    void картыРынкаЗаПартиюНеПовторяются() {
        for (long seed : new long[]{8_100_001L, 8_100_002L, 8_100_003L, 8_100_004L}) {
            List<String> cards = marketCardsOf(seed);
            Set<String> unique = new HashSet<>(cards);
            assertEquals(cards.size(), unique.size(),
                "карта рынка выпала повторно за одну партию (сид " + seed + "): "
                    + cards + ". Отыгравшая карта обязана уходить из игры, а не "
                    + "возвращаться в колоду через сброс.");
        }
    }

    @Test
    void картыРынкаМеняютсяКаждыйРаунд() {
        // Обратная беда, из-за которой всё и началось: карта залипала на
        // несколько раундов подряд. Партия идёт минимум пару раундов, значит и
        // разных карт должно быть не меньше двух.
        List<String> cards = marketCardsOf(8_100_005L);
        assertTrue(cards.size() >= 2,
            "за партию сменилась меньше чем одна карта рынка: " + cards
                + " — значит смена карты снова залипла");
    }
}
