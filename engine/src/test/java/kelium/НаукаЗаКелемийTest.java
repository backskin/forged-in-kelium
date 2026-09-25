package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.engine.Actions;
import kelium.engine.TurnContext;
import kelium.support.Fix;

/**
 * НАУКА ЗА КЕЛЕМИЙ ИЛИ ТРОФЕИ (решение дизайнера 25.09.2026): шаг трека можно
 * оплатить келемием, трофеями или вперемешку — по выбору игрока.
 */
class НаукаЗаКелемийTest {

    /** Делает один шаг по первому предложенному треку и платит, как велено. */
    private static Agent учёный(int келемием) {
        return new Agent(0, "учёный") {
            boolean шагнул;

            @Override
            public Choice choose(GameState st, List<Choice> options, Map<String, Object> ctx) {
                for (Choice c : options) {
                    if ("sci_pay_kelium".equals(c.kind()) && Integer.valueOf(келемием).equals(c.payload())) {
                        return c;
                    }
                    // синий трек: его приз — монеты, хранилище не трогает
                    if (!шагнул && "sci_track".equals(c.kind()) && c.label().startsWith("right")) {
                        шагнул = true;
                        return c;
                    }
                }
                for (Choice c : options) {
                    if ("pass".equals(c.kind()) || c.payload() == null) {
                        return c;
                    }
                }
                return options.get(0);
            }
        };
    }

    @Test
    void шагТолькоЗаКелемий() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        p.resources.setKelium(1);
        Actions.create("science", s).perform(p, new TurnContext(0, 1), учёный(1));
        assertEquals(1, p.techSteps.values().stream().mapToInt(Integer::intValue).sum(),
            "первая ступень куплена келемием");
        assertEquals(0, p.resources.kelium(), "келемий потрачен");
    }

    @Test
    void игрокВыбираетТрофеиВместоКелемия() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        p.resources.setKelium(1);
        p.resources.add(kelium.core.Resource.TROPHY, 1);
        Actions.create("science", s).perform(p, new TurnContext(0, 1), учёный(0));
        assertEquals(1, p.techSteps.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, p.resources.kelium(), "келемий сохранён — заплачено трофеем");
        assertEquals(0, p.resources.trophy());
    }
}
