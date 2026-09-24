package kelium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/** Двух одноимённых карт арсенала у игрока не бывает (правило дизайнера). */
class ОдноимённыйАрсеналTest {

    @Test
    void второйОдноимённыйНеУстанавливается() {
        GameState s = Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 4242L, null, null));
        String первая = null;
        String вторая = null;
        String чужая = null;
        Map<String, String> поИмени = new java.util.HashMap<>();
        for (var card : kelium.dataio.Ctx.cards(s, "arsenal").entries) {
            String id = String.valueOf(card.get("id"));
            String name = String.valueOf(card.get("name"));
            String было = поИмени.putIfAbsent(name, id);
            if (было != null && первая == null) {
                первая = было;
                вторая = id;
            }
        }
        for (var e : поИмени.entrySet()) {
            if (!e.getValue().equals(первая)) {
                чужая = e.getValue();
                break;
            }
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(первая != null,
            "в колоде нет одноимённых карт — проверять нечем");
        PlayerState p = s.player(0);
        p.arsenalInstalled.add(первая);
        assertTrue(GameEngine.sameNameInstalled(s, p, вторая),
            "одноимённая уже установлена — вторую ставить нельзя");
        assertFalse(GameEngine.sameNameInstalled(s, p, чужая),
            "карта с другим именем ставится как обычно");
    }
}
