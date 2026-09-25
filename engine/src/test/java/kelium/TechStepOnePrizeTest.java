package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.GameState;
import kelium.dataio.Ctx;
import kelium.rules.Ruleset;
import kelium.support.Fix;

/**
 * ЯЧЕЙКИ СТУПЕНЕЙ НАУКИ: сколько их и что лежит на каждой.
 *
 * <p>Финальный планшет научного отдела (25.09.2026): призы ячеек — только
 * монеты, одинаково на всех трёх треках. Ступень 1 — 1 монета первому,
 * ступень 2 — 2 и 1, ступень 3 — 3, вершина — ничего. Ячейки «4И» без приза.
 */
class TechStepOnePrizeTest {

    /** Ячейки шагов открываются по составу стола. */
    @Test
    void stepCellsOpenUpWithTheTableSize() {
        Ruleset rs = Ctx.rules(Fix.game());
        assertEquals(List.of(3, 3, 2, 1), rs.stepCapacity(4),
            "вчетвером открыты все ячейки всех шагов");
        // Последняя ячейка шагов 1, 2 и 3 открыта ТОЛЬКО вчетвером, поэтому
        // вдвоём и втроём состав ячеек одинаковый: 2/2/1/1.
        assertEquals(List.of(2, 2, 1, 1), rs.stepCapacity(3),
            "втроём закрыты последние ячейки шагов 1, 2 и 3");
        assertEquals(List.of(2, 2, 1, 1), rs.stepCapacity(2),
            "вдвоём открыты те же ячейки, что и втроём");
    }

    /** Призы ячеек — только монеты; прежних боеприпасов и келемия нет. */
    @Test
    void призыЯчеекТолькоМонеты() {
        Ruleset rs = Ctx.rules(Fix.game());
        assertEquals(List.of(List.of(1), List.of(2, 1), List.of(3), List.of()),
            rs.get("tech.step_coin_prize", null));
        Object старые = rs.get("tech.step1_prize", null);
        assertTrue(старые == null || (старые instanceof Map<?, ?> m && m.isEmpty()),
            "приза-ресурса первой ступени больше нет");
        assertNull(rs.get("tech.step1_prize.left.first", null));
    }

    /**
     * Код умеет РАЗДАТЬ приз третьей ячейки. Проверка не про свод, а про
     * движок: ключей рангов должно быть столько же, сколько ячеек у шага 1,
     * иначе прописанный в своде приз останется недостижимым.
     */
    @Test
    void theEngineCanHandOutAPrizeForEveryCellOfTheFirstStep() {
        GameState s = Fix.game();
        int cellsOnStepOne = Ctx.rules(s).stepCapacity(4).get(0);
        assertTrue(kelium.engine.Actions.PRIZE_RANK_KEYS.length >= cellsOnStepOne,
            "у шага 1 " + cellsOnStepOne + " ячеек, а движок знает только "
            + kelium.engine.Actions.PRIZE_RANK_KEYS.length + " ключей приза — "
            + "приз последней ячейки выдать нечем");
    }
}
