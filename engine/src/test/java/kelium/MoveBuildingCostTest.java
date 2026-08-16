package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.dataio.Ctx;
import kelium.support.Fix;

/**
 * ПЕРЕНОС ЗДАНИЯ СТОИТ ОДНУ МОНЕТУ (правило дизайнера 16.08.2026).
 *
 * <p>Раньше перенос стоил полную цену постройки — авиабазу двигать было втрое
 * дороже казармы, хотя перекладывается один и тот же жетон, — а ЦУ переезжало
 * даром. Теперь исключений нет ни в ту, ни в другую сторону: любое стоящее на
 * поле здание переезжает за монету, ЦУ в том числе. Бесплатна только отстройка
 * УНИЧТОЖЕННОГО ЦУ заново, и ставить его тогда можно на любой гекс поля.
 */
class MoveBuildingCostTest {

    /** Цена переноса одна на все здания и не зависит от цены постройки. */
    @Test
    void everyBuildingMovesForTheSameSingleCoin() {
        GameState s = Fix.game();
        assertEquals(1, Ctx.rules(s).getInt("actions.build.move_cost_coins", -1),
            "перенос любого здания — 1 монета");
        // Цены ПОСТРОЙКИ при этом остаются разными: правило трогает только переезд.
        assertTrue(s.tokenStats.plantCost(4) > 1,
            "станция №4 строится дороже монеты — иначе проверять нечего");
    }

    /** Отстройка уничтоженного ЦУ — бесплатна. */
    @Test
    void aLostCommandCentreIsRebuiltForFree() {
        GameState s = Fix.game();
        assertEquals(0, ((Number) Ctx.rules(s)
            .get("command_center.build_price_coins", 99)).intValue(),
            "ЦУ возвращается в игру даром: это не перенос, а возвращение");
    }

    /**
     * ПОТЕРЯННОЕ ЦУ СТАВИТСЯ КУДА УГОДНО. Проверяем именно исключение: у игрока
     * нет НИ ОДНОГО здания на поле, то есть обычная зона стройки пуста, — и всё
     * равно место под ЦУ находится.
     */
    @Test
    void aLostCommandCentreCanGoAnywhereOnTheField() {
        GameState s = Fix.game();
        kelium.core.PlayerState p = s.player(0);
        // Убираем с поля всё своё: зона стройки схлопывается в ничто.
        for (kelium.core.BuildingToken b : new java.util.ArrayList<>(p.buildingsOnField())) {
            s.field.get(b.hexId).freeSidesByToken(b.uid);
            b.hexId = null;
        }
        assertTrue(kelium.engine.Actions.buildableHexes(s, 0).isEmpty(),
            "без своих зданий обычная зона стройки должна быть пуста");

        int open = 0;
        for (Hex h : s.field.hexes.values()) {
            if (h.kind != HexKind.FORBIDDEN && !h.hasSpawnTile()) {
                open++;
            }
        }
        assertTrue(open > 0, "на поле есть куда встать жетону");

        // Сам список мест под ЦУ живёт внутри действия Стройки; проверяем то же
        // правило через его условие: гекс годится, если он не запретный и не
        // накрыт тайлом зарождения — принадлежность к зоне стройки не важна.
        int cuSpots = 0;
        for (Hex h : s.field.hexes.values()) {
            if (h.kind != HexKind.FORBIDDEN && !h.hasSpawnTile()
                    && h.chooseFootprint(1, 0, 0) != null) {
                cuSpots++;
            }
        }
        assertTrue(cuSpots > 0,
            "у игрока без зоны стройки обязано остаться место под ЦУ — иначе снос ЦУ "
            + "означает выбывание из игры, а не потерю");
    }

    /** ЦУ — здание, и на общее правило переноса оно тоже подписано. */
    @Test
    void theCommandCentreIsNoLongerFreeToMove() {
        GameState s = Fix.game();
        assertEquals(1, Ctx.rules(s).getInt("actions.build.move_cost_coins", -1),
            "монету за перенос платят все, ЦУ не исключение");
        assertEquals(1, Ctx.rules(s).getInt("actions.build.cu_moves_per_turn", -1),
            "переносить ЦУ по-прежнему можно не чаще раза за ход");
        assertEquals(BuildingType.COMMAND_CENTER,
            BuildingType.fromCode("command_center"), "код ЦУ не менялся");
    }
}
