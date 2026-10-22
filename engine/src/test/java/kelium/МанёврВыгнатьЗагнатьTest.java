package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.Actions;
import kelium.engine.TurnContext;
import kelium.support.Fix;

/**
 * МАНЁВР «ВЫГНАТЬ И ЗАГНАТЬ» (решение дизайнера 25.09.2026): выбери любой
 * гекс, выведи с него своих, затем введи в него своих, кто дойдёт. Без доплаты.
 */
class МанёврВыгнатьЗагнатьTest {

    @Test
    void сначалаВывестиПотомВвестиБезБоеприпасов() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        String старт = p.startHex;
        String цель = Fix.freeNeighbour(s, старт);
        UnitToken уходит = Fix.unit(s, 0, UnitType.INFANTRY, цель);
        UnitToken приходит = Fix.unit(s, 0, UnitType.INFANTRY, старт);
        int боеприпасов = p.resources.get(Resource.AMMO);

        Agent агент = new Agent(0, "сцена") {
            int ходов = 0;

            @Override
            public Choice choose(GameState st, List<Choice> options, Map<String, Object> ctx) {
                for (Choice c : options) {
                    if ("maneuver_hex".equals(c.kind()) && цель.equals(c.payload())) {
                        return c;
                    }
                }
                if (ходов < 2) {
                    int кто = ходов == 0 ? уходит.uid : приходит.uid;
                    for (Choice c : options) {
                        if ("move".equals(c.kind()) && c.payload() instanceof Map<?, ?> m
                                && ((Number) m.get("uid")).intValue() == кто
                                && (ходов == 1 || !старт.equals(m.get("to")))) {
                            ходов++;
                            return c;
                        }
                    }
                }
                for (Choice c : options) {
                    if ("pass".equals(c.kind())) {
                        return c;
                    }
                }
                return options.get(0);
            }
        };
        Actions.create("movement", s).perform(p, new TurnContext(0, 1), агент);

        assertNotEquals(цель, уходит.hexId, "жетон с выбранного гекса выведен");
        assertEquals(цель, приходит.hexId, "другой жетон введён на выбранный гекс");
        assertEquals(боеприпасов, p.resources.get(Resource.AMMO), "манёвр без доплаты");
    }
}
