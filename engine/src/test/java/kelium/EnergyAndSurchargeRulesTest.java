package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;
import kelium.engine.TurnContext;
import kelium.rules.Ruleset;

/**
 * Три правила, которые дизайнер подтвердил явно (2026-08-12):
 *
 * <ol>
 *   <li>пустую ячейку энергии НЕЛЬЗЯ закрыть монетой — такого правила нет;</li>
 *   <li>Смена энергии: первый гекс-исход бесплатно, КАЖДЫЙ следующий ровно
 *       +1 монета (цена не растёт);</li>
 *   <li>Стройка: первая операция без наценки, каждая следующая ровно
 *       +1 монета (никаких +2/+3).</li>
 * </ol>
 */
class EnergyAndSurchargeRulesTest {

    private static GameState game() {
        return Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 7L, null, null));
    }

    @Test
    void unpoweredBuildingWorksOnlyWhenPaidForInDevelopment() {
        GameState s = game();
        PlayerState p = s.player(0);
        p.resources.add(Resource.COIN, 99);
        BuildingToken fac = s.tokenStats.makeBuilding(BuildingType.FACTORY, 0, 9001, null);
        fac.hexId = p.startHex;
        p.buildings.add(fac);

        assertFalse(fac.powered(), "завод только что поставлен — кубиков на нём нет");
        Ruleset rs = kelium.dataio.Ctx.rules(s);
        int rate = rs.getInt("actions.empty_energy_slot_coin_cost", -1);
        assertEquals(1, rate, "компенсация энергии монетами: 1 МОН за ячейку");

        // Платим за ячейки — здание становится доступным ДЛЯ ЭТОГО действия,
        // но кубик в ячейку НЕ кладётся: правило одноразовое.
        int coinsBefore = p.resources.coin();
        boolean usable = kelium.engine.Power.usableForAction(
            s, p, fac, new kelium.support.Fix.FirstChoiceAgent(0));
        assertTrue(usable, "с деньгами незапитанное здание должно стать доступным");
        assertEquals(coinsBefore - fac.energySlots * rate, p.resources.coin(),
            "оплата обязана списать по " + rate + " МОН за каждую пустую ячейку");
        assertFalse(fac.powered(),
            "кубик в ячейку не кладётся — в следующее действие платить снова");
    }

    @Test
    void surchargesAreFlatPlusOne() {
        GameState s = game();
        Ruleset rs = ((GameConfig) s.config).ruleset;
        for (String key : List.of("actions.build.surcharge_coins",
                                  "actions.energy_swap.surcharge_coins")) {
            TurnContext ctx = new TurnContext(0, 1);
            List<Integer> sched = rs.getIntList(key);
            String act = key.contains("build") ? "build" : "energy_swap";
            // первая операция бесплатна, вторая и ВСЕ последующие — ровно +1
            for (int i = 0; i < 6; i++) {
                int expected = i == 0 ? 0 : 1;
                assertEquals(expected, ctx.nextOpSurcharge(act, sched),
                    key + ": операция №" + (i + 1) + " должна стоить " + expected);
                ctx.recordOp(act);
            }
        }
    }
}
