package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.dataio.Ctx;
import kelium.engine.ActionResult;
import kelium.engine.Actions;
import kelium.engine.TurnContext;
import kelium.support.Fix;

/**
 * ПИТАНИЕ БЕЗ ОГРАНИЧЕНИЯ (решение дизайнера 23.09.2026): в одном действии
 * источник может отдать кубики, забрать их обратно и отдать снова — прежнего
 * «каждый источник один раз» больше нет.
 */
class ПитаниеБезОграниченияTest {

    /** Отвечает по сценарию видов вариантов: «energy_give», «energy_take», … */
    private static final class Сценарий extends Agent {
        private final Deque<String> шаги;

        Сценарий(int seat, List<String> шаги) {
            super(seat, "сценарий");
            this.шаги = new ArrayDeque<>(шаги);
        }

        @Override
        public Choice choose(GameState s, List<Choice> options, Map<String, Object> ctx) {
            if ("energy_activation".equals(ctx.get("kind"))) {
                String хочу = шаги.isEmpty() ? "energy_done" : шаги.poll();
                // работаем с САМОЙ станцией: забрать у ЦУ — другое действие
                for (Choice c : options) {
                    if (хочу.equals(c.kind()) && String.valueOf(c.label()).contains("POWER_PLANT")) {
                        return c;
                    }
                }
                for (Choice c : options) {
                    if (хочу.equals(c.kind())) {
                        return c;
                    }
                }
                return options.get(options.size() - 1);    // «закончить»
            }
            for (Choice c : options) {
                if (c.payload() != null) {
                    return c;                               // куда положить кубик
                }
            }
            return options.get(0);
        }
    }

    @Test
    void источникОтдаётЗабираетИСноваОтдаёт() {
        GameState s = Fix.game();
        assertEquals(0, Ctx.rules(s).getInt("actions.energy_swap.activations_per_source", 1),
            "в своде Питание без ограничения");
        Ctx.rules(s).override("energy.plant_off_cell_gives", 2);   // станция даёт 2 где угодно
        String гекс = Fix.freeNeighbour(s, s.player(0).startHex);
        BuildingToken станция = Fix.building(s, 0, BuildingType.POWER_PLANT, гекс, 2);
        станция.energyIdle = kelium.engine.Power.sourceCubes(s, станция);
        assertTrue(станция.energyIdle > 0, "станции есть что отдать");
        Fix.building(s, 0, BuildingType.FACTORY, s.player(0).startHex, null);

        Agent агент = new Сценарий(0, List.of("energy_give", "energy_take", "energy_give"));
        ActionResult r = Actions.create("energy_swap", s)
            .perform(s.player(0), new TurnContext(0, 1), агент);
        assertTrue(r.ok(), "действие состоялось: " + r.detail());
        assertEquals(3, ((Number) r.telemetry().get("activations")).intValue(),
            "станция сработала трижды за одно действие: отдала, забрала, отдала");
    }
}
