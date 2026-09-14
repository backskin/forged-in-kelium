package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.UnitType;
import kelium.engine.GameEngine;
import kelium.support.Fix;

/**
 * РАЗВЯЗКА НИЧЬИ ПО ОЧКАМ (правило дизайнера 14.09.2026): больше гексов, на
 * которых стоят жетоны игрока; затем больше трофеев (жетоны на свалке плюс
 * кубики трофеев); затем больше келемия; иначе победу делят.
 *
 * <p>Прежняя развязка движка (келемий, затем монеты) была его выдумкой.
 */
class РазвязкаНичьиTest {

    /** Партия, доигранная до конца: очки посчитаны, победитель выбран. */
    private static Map<String, Object> доиграть(GameState s) {
        s.round = 99;                       // раундов уже больше предела: партия сразу считается
        return new GameEngine(s, s.agents, null).resume();
    }

    /** Убрать с поля всё, чтобы сцена считалась только тем, что мы поставим. */
    private static void очистить(GameState s) {
        for (PlayerState p : s.players) {
            p.buildings.forEach(b -> b.hexId = null);
            p.units.forEach(u -> u.setHexId(null));
            p.resources.setAmmo(0);
            p.resources.setKelium(0);
            p.resources.pay(Resource.TROPHY, p.resources.trophy());
            p.resources.pay(Resource.COIN, p.resources.coin());
        }
    }

    @Test
    void приРавныхОчкахВышеТотУКогоБольшеГексов() {
        GameState s = Fix.game(2, 42L);
        очистить(s);
        String h0 = s.player(0).startHex;
        String рядом = Fix.freeNeighbour(s, h0);
        // У места 0 жетоны на двух гексах, у места 1 — на одном.
        Fix.unit(s, 0, UnitType.INFANTRY, h0);
        Fix.unit(s, 0, UnitType.INFANTRY, рядом);
        Fix.unit(s, 1, UnitType.INFANTRY, s.player(1).startHex);
        // Келемия больше у проигравшего по гексам: прежняя развязка дала бы его.
        s.player(1).resources.add(Resource.KELIUM, 5);

        Map<String, Object> итог = доиграть(s);
        assertEquals(0, итог.get("winner"), "больше гексов — выше в итоге");
    }

    @Test
    void приРавныхГексахРешаютТрофеи() {
        GameState s = Fix.game(2, 42L);
        очистить(s);
        Fix.unit(s, 0, UnitType.INFANTRY, s.player(0).startHex);
        Fix.unit(s, 1, UnitType.INFANTRY, s.player(1).startHex);
        s.player(1).resources.add(Resource.TROPHY, 2);
        s.player(0).resources.add(Resource.KELIUM, 9);   // келемий ниже трофеев

        Map<String, Object> итог = доиграть(s);
        assertEquals(1, итог.get("winner"), "больше трофеев — выше, келемий спрашивается позже");
    }

    @Test
    void жетонНаСвалкеСчитаетсяЗаОдинТрофей() {
        GameState s = Fix.game(2, 42L);
        очистить(s);
        Fix.unit(s, 0, UnitType.INFANTRY, s.player(0).startHex);
        Fix.unit(s, 1, UnitType.INFANTRY, s.player(1).startHex);
        // У места 0 на свалке жетон завода: он стоит 1 трофей, как и кубик.
        s.player(0).destroyedTokens.add(
            Fix.building(s, 1, BuildingType.FACTORY, s.player(1).startHex, null));
        s.player(1).resources.add(Resource.TROPHY, 1);

        Map<String, Object> итог = доиграть(s);
        assertTrue(итог.get("winners") instanceof List<?> l && l.size() == 2,
            "жетон на свалке равен кубику трофея — полная ничья, победу делят: " + итог);
    }

    @Test
    void полностьюРавныеИгрокиДелятПобеду() {
        GameState s = Fix.game(2, 42L);
        очистить(s);
        Fix.unit(s, 0, UnitType.INFANTRY, s.player(0).startHex);
        Fix.unit(s, 1, UnitType.INFANTRY, s.player(1).startHex);

        Map<String, Object> итог = доиграть(s);
        assertTrue(итог.get("winners") instanceof List<?> l && l.size() == 2,
            "всё равно — победу делят: " + итог);
        assertEquals("shared_victory", итог.get("condition"));
    }
}
