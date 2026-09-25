package kelium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.engine.Actions;
import kelium.engine.Modules;
import kelium.engine.TurnContext;
import kelium.support.Fix;

/**
 * ЦУ НЕ НАНИМАЕТ ВЫШКИ БЕЗ СИНЕГО ЖЕТОНА (решение дизайнера 25.09.2026):
 * печатное число вышек на ЦУ — 0; жетон сборки на ЦУ накрывает его своим.
 */
class ВышкиПослеЖетонаTest {

    private static List<String> вариантыСборки(GameState s, PlayerState p) {
        List<String> виды = new ArrayList<>();
        Agent агент = new Agent(0, "сборщик") {
            @Override
            public Choice choose(GameState st, List<Choice> options, Map<String, Object> ctx) {
                if ("assemble".equals(ctx.get("kind"))
                        && "command_center".equals(ctx.get("building_type"))) {
                    for (Choice c : options) {
                        if (c.payload() instanceof Map<?, ?> m) {
                            виды.add(String.valueOf(m.get("kind")));
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
        TurnContext ctx = new TurnContext(0, 1);
        ctx.allPowered = true;
        Actions.create("assembly", s).perform(p, ctx, агент);
        return виды;
    }

    @Test
    void безЖетонаВышекНет() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        assertTrue(Modules.assemblyOutput(s, p, BuildingType.COMMAND_CENTER, "unit") == 0,
            "печатный выпуск вышек у ЦУ — 0");
        List<String> виды = вариантыСборки(s, p);
        assertFalse(виды.contains("unit"), "без синего жетона ЦУ вышку не предлагает: " + виды);
        assertTrue(виды.contains("ammo"), "боеприпас ЦУ по-прежнему делает");
    }

    @Test
    void жетонНаЦуОткрываетВышки() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        p.bluePlacements.put(BuildingType.COMMAND_CENTER,
            new java.util.HashMap<>(Map.of("units", 1, "ammo", 1)));
        assertTrue(вариантыСборки(s, p).contains("unit"), "с жетоном ЦУ нанимает вышку");
    }
}
