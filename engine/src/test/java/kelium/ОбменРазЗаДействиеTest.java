package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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
 * КАЖДЫЙ ОБМЕН РЫНКА — ОДИН РАЗ ЗА ДЕЙСТВИЕ (решение дизайнера 23.09.2026):
 * обмен на монеты — одна ступень из трёх; после неё монет за келемий в том же
 * действии не предлагают.
 */
class ОбменРазЗаДействиеTest {

    @Test
    void монетыЗаКелемийТолькоОднаСтупень() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        p.resources.setKelium(3);
        List<List<String>> предложения = new ArrayList<>();
        Agent агент = new Agent(0, "торговец") {
            @Override
            public Choice choose(GameState st, List<Choice> options, Map<String, Object> ctx) {
                if ("market".equals(ctx.get("kind"))) {
                    List<String> монеты = new ArrayList<>();
                    for (Choice c : options) {
                        if ("market_rate".equals(c.kind()) && c.payload() instanceof Map<?, ?> m
                                && "coin".equals(m.get("what"))) {
                            монеты.add(c.label());
                        }
                    }
                    предложения.add(монеты);
                    for (Choice c : options) {
                        if ("market_rate".equals(c.kind()) && c.payload() instanceof Map<?, ?> m
                                && "coin".equals(m.get("what")) && !c.label().startsWith("2")) {
                            return c;              // 1 келемий → монеты
                        }
                    }
                }
                for (Choice c : options) {
                    if ("pass".equals(c.kind())) {
                        return c;
                    }
                }
                return options.get(options.size() - 1);
            }
        };
        Actions.create("market", s).perform(p, new TurnContext(0, 1), агент);
        assertTrue(предложения.size() >= 2, "рынок спросил и после первой сделки");
        assertFalse(предложения.get(0).isEmpty(), "сначала монеты за келемий предлагались");
        assertEquals(List.of(), предложения.get(1), "после обмена на монеты второго такого нет");
    }
}
