package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import kelium.cards.objectives.ЗаданиеИзЯзыка;
import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.TurnJournal;
import kelium.dataio.GameConfig;
import kelium.engine.Actions;
import kelium.engine.Effects;
import kelium.engine.Setup;
import kelium.engine.TurnContext;
import kelium.engine.cards.EngineCardContext;

/**
 * ЭНЕРГИЯ И УРОВНИ ЗДАНИЙ В КАРТАХ (заказ Влада 02.10.2026): подъём уровня
 * добытчика и требования об уровнях.
 */
class ЭнергияИУровниTest {

    @AfterAll
    static void вернутьКолодуПоУмолчанию() {
        kelium.engine.cards.CardRegistry.reset();
        Setup.buildGame(GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 1L, null, null));
    }

    /** Ставит добытчик 1-го уровня; на остальное — первый вариант. */
    private static final class Строитель extends Agent {
        Строитель() {
            super(0, "тест");
        }

        @Override
        public Choice choose(GameState s, List<Choice> options, Map<String, Object> ctx) {
            if ("build_pick".equals(ctx.get("kind"))) {
                for (Choice c : options) {
                    if ("build_pick".equals(c.kind()) && c.payload() instanceof Map<?, ?> m
                            && Integer.valueOf(1).equals(m.get("level"))) {
                        return c;
                    }
                }
                for (Choice c : options) {
                    if ("pass".equals(c.kind())) {
                        return c;
                    }
                }
            }
            return options.get(0);
        }
    }

    private static BuildingToken добытчик(PlayerState p) {
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.type == BuildingType.MINER) {
                return b;
            }
        }
        return null;
    }

    @Test
    void подъёмУровняМеняетЖетонНаМестеЗаМонету() {
        GameState s = Setup.buildGame(GameConfig.buildCached("1.49.0", 2, 7L, null, null));
        s.journal = new TurnJournal(2);
        s.journal.startTurn(0);
        PlayerState p = s.player(0);
        p.resources.add(kelium.core.Resource.COIN, 10);
        kelium.engine.GameEngine.bind(s, List.of(new Строитель(), new Строитель()));
        Actions.create("build_miner", s).perform(p, new TurnContext(0, 1), new Строитель());
        BuildingToken был = добытчик(p);
        assertNotNull(был, "добытчик поставлен");
        assertEquals(1, был.level);
        String гекс = был.hexId;
        Map<String, Object> треб = Map.of("узел", "уровни", "вид", "miner", "сумма", 2);
        ЗаданиеИзЯзыка карта = new ЗаданиеИзЯзыка("t", Map.of("имя", "т", "требование", треб,
            "награда", Map.of("действие", "mining|combat")));
        assertTrue(!карта.satisfied(new EngineCardContext(s, 0)), "сумма уровней 1 < 2");
        int монет = p.resources.coin();
        Map<String, Object> итог = Effects.apply("upgrade_building", s, 0, Map.of("type", "miner", "cost", 1));
        assertEquals(1, итог.get("upgraded"), String.valueOf(итог) + " запас: " + p.buildings.stream().filter(b -> b.hexId == null).map(b -> b.type + ":" + b.level).toList());
        BuildingToken стал = добытчик(p);
        assertEquals(2, стал.level, "на поле добытчик 2-го уровня");
        assertEquals(гекс, стал.hexId, "на том же гексе");
        assertEquals(null, был.hexId, "прежний жетон в запасе");
        assertEquals(монет - 1, p.resources.coin(), "подъём стоил монету");
        assertTrue(карта.satisfied(new EngineCardContext(s, 0)), "сумма уровней теперь 2");
    }
}
