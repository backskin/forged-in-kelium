package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.Ctx;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * НАЧАЛЬНЫЙ АРСЕНАЛ ОТКРЫВАЮТ НА ПОДГОТОВКЕ (решение дизайнера 30.09.2026):
 * после подготовки карта стоит на полке, в руке арсенала её нет, а монеты,
 * келемий и боеприпасы игрока — ровно набор с её верха.
 */
class StartArsenalSetupTest {

    @Test
    void startArsenalIsInstalledAndItsKitIsTheStartingResources() {
        for (int players = 2; players <= 4; players++) {
            for (long seed = 1; seed <= 5; seed++) {
                GameState s = Setup.buildGame(
                    GameConfig.buildCached(GameConfig.DEFAULT_RULESET, players, seed, null, null));
                for (PlayerState p : s.players) {
                    long вРуке = p.arsenalHand.stream().filter(c -> starting(s, c)).count();
                    assertEquals(0, вРуке, "начальный арсенал остался в руке, место " + p.seat);
                    String cid = p.arsenalInstalled.stream().filter(c -> starting(s, c))
                        .findFirst().orElse(null);
                    assertTrue(cid != null, "начальный арсенал не установлен, место " + p.seat);
                    Map<String, Object> top = GameEngine.стартовыйНабор(s, cid);
                    assertTrue(top != null, "у " + cid + " нет набора на верху");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> params = (Map<String, Object>) top.get("params");
                    assertEquals(число(params, "coin"), p.resources.coin(), cid + ": монеты");
                    assertEquals(число(params, "kelium"), p.resources.kelium(), cid + ": келемий");
                }
            }
        }
    }

    private static boolean starting(GameState s, String cid) {
        Map<String, Object> card = Ctx.cards(s, "arsenal").find(cid);
        return card != null && "starting".equals(card.get("kind"));
    }

    private static int число(Map<String, Object> params, String key) {
        return params.get(key) instanceof Number n ? n.intValue() : 0;
    }
}
