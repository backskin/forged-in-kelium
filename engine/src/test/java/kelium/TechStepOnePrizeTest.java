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
 * ПЕРВЫЙ ШАГ НАУКИ: сколько на нём ячеек и что лежит на каждой
 * (правила дизайнера 16.08.2026).
 *
 * <p>Приз лежит НЕ на шаге целиком, а на каждой его ячейке по отдельности и
 * убывает от ячейки к ячейке. Ячейки при этом открываются по составу стола, и
 * третья ячейка первого шага открыта только вчетвером — поэтому её приз может
 * молча пропасть, если код умеет раздавать только «первому» и «второму».
 * Ровно так и было до 16.08.2026.
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

    /** Трек красных модулей: 3 боеприпаса на первой ячейке, 1 на второй. */
    @Test
    void theRedModuleTrackPaysThreeThenOneAmmo() {
        Ruleset rs = Ctx.rules(Fix.game());
        assertEquals(3, prize(rs, "left", "first", "ammo"),
            "первая ячейка шага 1 трека красных модулей — 3 боеприпаса");
        assertEquals(1, prize(rs, "left", "second", "ammo"),
            "вторая ячейка — 1 боеприпас");
        assertNull(rs.get("tech.step1_prize.left.third", null),
            "третьей награды на красном треке нет: ячейка есть, приза на ней нет");
    }

    /** Трек синих модулей: 4 · 2 · 1 монета по трём ячейкам. */
    @Test
    void theBlueModuleTrackPaysFourThenTwoThenOneCoin() {
        Ruleset rs = Ctx.rules(Fix.game());
        assertEquals(4, prize(rs, "right", "first", "coin"), "первая ячейка — 4 монеты");
        assertEquals(2, prize(rs, "right", "second", "coin"), "вторая ячейка — 2 монеты");
        assertEquals(1, prize(rs, "right", "third", "coin"),
            "третья ячейка — 1 монета; она открыта только вчетвером, но приз на ней есть");
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

    @SuppressWarnings("unchecked")
    private static int prize(Ruleset rs, String track, String rank, String resource) {
        Object raw = rs.get("tech.step1_prize." + track + "." + rank, null);
        assertTrue(raw instanceof Map, "нет награды " + track + "/" + rank);
        Object v = ((Map<String, Object>) raw).get(resource);
        assertTrue(v instanceof Number, "награда " + track + "/" + rank
            + " не содержит ресурс " + resource);
        return ((Number) v).intValue();
    }
}
