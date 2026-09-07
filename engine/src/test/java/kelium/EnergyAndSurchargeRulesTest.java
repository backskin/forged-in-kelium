package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void unpoweredBuildingWorksOnlyWithTheEmergencyPowerCard() {
        GameState s = game();
        PlayerState p = s.player(0);
        p.resources.add(Resource.COIN, 99);
        BuildingToken fac = s.tokenStats.makeBuilding(BuildingType.FACTORY, 0, 9001, null);
        fac.hexId = p.startHex;
        p.buildings.add(fac);

        assertFalse(fac.powered(), "завод только что поставлен — кубиков на нём нет");

        // ОБЩЕГО ПРАВИЛА «закрой ячейку монетой» В ИГРЕ БОЛЬШЕ НЕТ (решение
        // дизайнера 17.08.2026): энергия — главный дефицит, и выкупать его за
        // деньги нельзя, иначе обесцениваются и Смена энергии, и планировка базы.
        int coinsBefore = p.resources.coin();
        assertFalse(kelium.engine.Power.usableForAction(
                s, p, fac, new kelium.support.Fix.FirstChoiceAgent(0)),
            "без карты незапитанное здание недоступно, сколько бы ни было денег");
        assertEquals(coinsBefore, p.resources.coin(),
            "и ни одной монеты за отказ списывать нельзя");

        // Право на доплату даёт РОВНО ОДНА карта арсенала — «Аварийное питание».
        // ПО СПОСОБНОСТИ, А НЕ ПО НОМЕРУ: номер карты меняется с версией
        // колоды (b20 в арсенале 3.0.0, a5_32 в 5.0.0), а проверяем мы правило.
        String карта = kelium.support.Fix.картаСоСпособностью(s, "pay_energy_with_coin");
        assertNotNull(карта, "в действующей колоде есть карта «Аварийное питание»");
        p.arsenalInstalled.add(карта);
        Ruleset rs = kelium.dataio.Ctx.rules(s);
        int rate = rs.getInt("actions.empty_energy_slot_coin_cost", -1);
        assertEquals(1, rate, "цена ячейки для карты: 1 МОН за ячейку");
        assertTrue(kelium.engine.Power.usableForAction(
                s, p, fac, new kelium.support.Fix.FirstChoiceAgent(0)),
            "с картой «Аварийное питание» доплата снова разрешена");
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
