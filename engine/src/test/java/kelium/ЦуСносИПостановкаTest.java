package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.engine.Actions;
import kelium.engine.TurnContext;
import kelium.engine.ЦуИзЗапаса;
import kelium.support.Fix;

/**
 * ЦУ НЕ СНОСЯТ И НЕ ПЕРЕНОСЯТ (решение дизайнера 25.09.2026): ни Стройкой,
 * ни картами. Из запаса — после уничтожения — ЦУ ставят на любой гекс.
 */
class ЦуСносИПостановкаTest {

    /** Сносит своё ЦУ, если может; дальше — первый вариант. */
    private static final class Сносчик extends Agent {
        boolean предлагалиСносЦу;

        Сносчик(int seat) {
            super(seat, "сносчик");
        }

        @Override
        public Choice choose(GameState s, List<Choice> options, Map<String, Object> ctx) {
            for (Choice c : options) {
                if ("demolish_pick".equals(c.kind()) && c.label().contains("command_center")) {
                    предлагалиСносЦу = true;
                    return c;
                }
            }
            for (Choice c : options) {
                if ("stop".equals(c.kind()) || c.payload() == null) {
                    return c;
                }
            }
            return options.get(0);
        }
    }

    private static BuildingToken цу(PlayerState p) {
        for (BuildingToken b : p.buildings) {
            if (b.type == BuildingType.COMMAND_CENTER) {
                return b;
            }
        }
        return null;
    }

    @Test
    void безСпецДействияЦуНеСносится() {
        GameState s = Fix.game();
        Сносчик агент = new Сносчик(0);
        TurnContext ход = new TurnContext(0, 1);
        ход.useSpec();                                   // спец-действие уже потрачено
        Actions.create("build", s).perform(s.player(0), ход, агент);
        assertFalse(агент.предлагалиСносЦу, "снос ЦУ без спец-действия не предлагается");
        assertNotNull(цу(s.player(0)).hexId, "ЦУ на поле");
    }

    @Test
    void цуНеСноситсяИСоСпецДействием() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        String гекс = цу(p).hexId;
        Сносчик агент = new Сносчик(0);
        TurnContext ход = new TurnContext(0, 1);
        Actions.create("build", s).perform(p, ход, агент);
        assertFalse(агент.предлагалиСносЦу, "снос ЦУ не предлагается и со спец-действием");
        assertEquals(гекс, цу(p).hexId, "ЦУ стоит на своём гексе");
    }

    @Test
    void цуИзЗапасаВстаётНаЛюбойГекс() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        Actions.returnOwnBuildingToReserve(s, p, цу(p), true);
        assertNotNull(ЦуИзЗапаса.вЗапасе(p), "ЦУ в запасе");
        assertTrue(ЦуИзЗапаса.места(s, 0).size() > 10, "мест под ЦУ много — любой гекс");
        assertTrue(ЦуИзЗапаса.поставить(s, p, new Fix.FirstChoiceAgent(0)));
        assertTrue(цу(p).powered(), "ЦУ пришёл со своей энергией");
    }
}
