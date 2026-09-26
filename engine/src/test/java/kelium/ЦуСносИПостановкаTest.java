package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * СНОС СВОЕГО ЦУ — РАЗ ЗА ПАРТИЮ (решение дизайнера 25.09.2026, вечер):
 * без монеты, свой пустой модуль боя уходит сопернику жетоном уничтожения ЦУ;
 * нет жетона — сносить нельзя. Из запаса ЦУ ставят на любой гекс.
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
    void безСвоегоЖетонаЦуНеСносится() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        p.ownCuTokenAvailable = false;                   // жетон уже отдан
        Сносчик агент = new Сносчик(0);
        Actions.create("build", s).perform(p, new TurnContext(0, 1), агент);
        assertFalse(агент.предлагалиСносЦу, "без своего жетона снос ЦУ не предлагается");
        assertNotNull(цу(p).hexId, "ЦУ на поле");
    }

    @Test
    void сносЦуОтдаётЖетонСоперникуБезМонеты() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        assertTrue(p.ownCuTokenAvailable, "на старте свой жетон у игрока");
        int монет = p.resources.coin();
        int жетоновУСоседа = s.player(1).cuDestructionTokens;
        Сносчик агент = new Сносчик(0);
        TurnContext ход = new TurnContext(0, 1);
        ход.useSpec();                                   // спец-действие не нужно
        Actions.create("build", s).perform(p, ход, агент);
        assertTrue(агент.предлагалиСносЦу, "снос ЦУ предлагается и без спец-действия");
        assertNull(цу(p).hexId, "ЦУ ушёл в запас, сразу не ставится");
        assertEquals(монет, p.resources.coin(), "за снос ЦУ монеты нет");
        assertFalse(p.ownCuTokenAvailable, "свой жетон ушёл");
        assertEquals(жетоновУСоседа + 1, s.player(1).cuDestructionTokens,
            "сосед получил жетон уничтожения ЦУ");
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
