package kelium.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import kelium.core.Deck;
import kelium.core.GameState;
import kelium.dataio.GameConfig;

/**
 * СУПЕР-АРСЕНАЛ — ЗАКРЫТАЯ КОЛОДА (дизайнер 14.10.2026): на подготовке карт
 * на треках нет; вставший на вершину берёт 2 верхние, одну оставляет,
 * вторую кладёт под низ.
 */
class СуперАрсеналКолодаTest {

    @Test
    void двеВерхниеОднаОстаётсяВтораяПодНиз() {
        Path root = GameConfig.resolveDataRoot(null);
        GameConfig cfg = GameConfig.buildCached("1.51.0", 3, 7L, root, null, null, null,
            root.resolve("scenarios/old/сценарий 3 игрока 1.kmap"));
        GameState st = Setup.buildGame(cfg);

        assertTrue(st.superArsenalOffer.isEmpty(), "открытых карт на треках нет");
        Deck deck = st.decks.get("super_arsenal");
        assertNotNull(deck, "колода супер-арсенала собрана");
        int было = deck.drawPile.size();
        assertTrue(было >= 2);
        String верх = deck.drawPile.get(было - 1);
        String второй = deck.drawPile.get(было - 2);

        String взята = Actions.суперАрсеналДваОдна(st, st.players.get(0), null);

        assertEquals(верх, взята, "без агента — первая из двух");
        assertEquals(было - 1, deck.drawPile.size(), "в колоде на одну меньше");
        assertEquals(второй, deck.drawPile.get(0), "вторая — под низом колоды");
    }
}
