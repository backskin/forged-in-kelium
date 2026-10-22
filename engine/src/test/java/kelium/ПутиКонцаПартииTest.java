package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import kelium.core.Resource;
import kelium.engine.Actions;
import kelium.engine.CombatResolver;
import kelium.engine.TurnContext;
import kelium.support.Fix;

/**
 * ПУТИ ЗАКОНЧИТЬ ПАРТИЮ (решение дизайнера 25.09.2026): две мгновенные победы —
 * вершины всех трёх треков одним игроком и второй уничтоженный ЦУ (втроём и
 * вчетвером); конец с подсчётом очков — истощены тайлы зарождения или кончились
 * карты рынка. Прокрутка карты рынка кладёт её под низ колоды и партию не
 * укорачивает.
 */
class ПутиКонцаПартииTest {

    /** Берёт вариант нужного вида, иначе — отказ. */
    private static final class Берёт extends Agent {
        final String вид;

        Берёт(int seat, String вид) {
            super(seat, "берёт " + вид);
            this.вид = вид;
        }

        @Override
        public Choice choose(GameState s, List<Choice> options, Map<String, Object> ctx) {
            for (Choice c : options) {
                if (вид.equals(c.kind())) {
                    return c;
                }
            }
            for (Choice c : options) {
                if (c.payload() == null) {
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
    void прокруткаРынкаКладётКартуПодНиз() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        if (s.marketActive == null) {
            s.marketActive = s.decks.get("market").draw(s.rng);   // открыть карту
        }
        String была = s.marketActive;
        assertNotNull(была, "карта рынка открыта");
        int вКолоде = s.decks.get("market").size();
        p.resources.add(Resource.KELIUM, 2);
        Actions.create("market", s).perform(p, new TurnContext(0, 1), new Берёт(0, "market_refresh"));
        assertNotEquals(была, s.marketActive, "открыта следующая карта");
        assertEquals(вКолоде, s.decks.get("market").size(), "карт в колоде столько же");
        assertEquals(была, s.decks.get("market").drawPile.get(0), "прежняя карта — под низом");
    }

    @Test
    void второйУничтоженныйЦуПобеждаетВчетвером() {
        GameState s = Fix.game();
        CombatResolver бой = (CombatResolver) s.combat;
        бой.destroy(цу(s.player(1)), 0);
        assertFalse(s.finished, "первое уничтожение — ещё не победа");
        бой.destroy(цу(s.player(2)), 0);
        assertTrue(s.finished, "второе уничтожение ЦУ — победа");
        assertEquals(0, s.winner);
        assertEquals("military", s.winCondition);
    }

    @Test
    void вдвоёмВтороеУничтожениеНеПобеждает() {
        GameState s = Fix.game(2, 7L);
        CombatResolver бой = (CombatResolver) s.combat;
        String гекс = цу(s.player(1)).hexId;
        бой.destroy(цу(s.player(1)), 0);
        цу(s.player(1)).hexId = гекс;                    // ЦУ снова на поле
        бой.destroy(цу(s.player(1)), 0);
        assertFalse(s.finished, "вдвоём военной победы нет");
    }

    @Test
    void вершиныВсехТрёхТрековПобеждаютСразу() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        var tech = s.tech;
        String последний = tech.tracks.get(tech.tracks.size() - 1);
        for (String t : tech.tracks) {
            for (int step = 1; step < tech.steps; step++) {
                tech.placeCube(t, 0, step);
            }
            p.techSteps.put(t, tech.steps - 1);
            if (!t.equals(последний)) {
                tech.placeCube(t, 0, tech.steps);
                p.techSteps.put(t, tech.steps);
            }
        }
        // вершину уже занял соперник — вершина вмещает сколько угодно
        tech.placeCube(последний, 1, tech.steps);
        p.techCubesLeft = 5;
        p.resources.add(Resource.TROPHY, 10);
        p.resources.add(Resource.KELIUM, 10);
        Actions.create("science", s).perform(p, new TurnContext(0, 1), new Берёт(0, "sci_track"));
        assertTrue(tech.onAllPeaks(0), "кубик на всех трёх вершинах");
        assertTrue(s.finished, "победа наукой — сразу");
        assertEquals(0, s.winner);
        assertEquals("all_peaks", s.winCondition);
    }
}
