package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.engine.Actions;
import kelium.support.Fix;

/**
 * ИСТОЧНИК СНЕСЁН — КАКИЕ КУБИКИ УБРАТЬ, РЕШАЕТ ВЛАДЕЛЕЦ (решение дизайнера
 * 23.09.2026). Станция кормила завод; её сносят — и владелец переносит кубик с
 * казармы на завод: обесточенной остаётся казарма, а не завод.
 */
class УходИсточникаTest {

    @Test
    void владелецВыбираетКтоОстанетсяБезЭнергии() {
        GameState s = Fix.game();
        String свой = s.player(0).startHex;
        String сосед = Fix.freeNeighbour(s, свой);
        BuildingToken станцияА = Fix.building(s, 0, BuildingType.POWER_PLANT, сосед, 1);
        BuildingToken станцияБ = Fix.building(s, 0, BuildingType.POWER_PLANT, сосед, 2);
        BuildingToken завод = Fix.building(s, 0, BuildingType.FACTORY, свой, null);
        BuildingToken казарма = Fix.building(s, 0, BuildingType.BARRACKS, сосед, null);
        завод.addEnergyFrom(станцияА.uid, 1);          // завод держится на станции А
        казарма.addEnergyFrom(станцияБ.uid, 1);        // казарма — на станции Б
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < s.numPlayers(); i++) {
            final int место = i;
            agents.add(new Agent(i, "владелец") {
                @Override
                public Choice choose(GameState st, List<Choice> o, Map<String, Object> c) {
                    if ("energy_loss_shift".equals(c.get("kind"))) {
                        for (Choice ch : o) {
                            if (ch.payload() instanceof Map<?, ?> m
                                    && ((Number) m.get("from")).intValue() == казарма.uid
                                    && ((Number) m.get("to")).intValue() == завод.uid) {
                                return ch;
                            }
                        }
                    }
                    return o.get(o.size() - 1);
                }
            });
        }
        s.agents = agents;
        int было = завод.energyPlaced;
        Actions.returnOwnBuildingToReserve(s, s.player(0), станцияА, true);
        assertEquals(было, завод.energyPlaced, "завод остался с энергией: кубик пришёл с казармы");
        assertEquals(0, казарма.energyPlaced, "обесточена казарма — так решил владелец");
    }
}
