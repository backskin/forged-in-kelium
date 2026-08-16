package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.dataio.GameConfig;
import kelium.engine.BlockStamp;

/**
 * НАБОР БЛОКОВ — ФИЗИЧЕСКИЙ КАРТОН, и печать на нём должна сходиться с правилами.
 *
 * <p>Проверяется сам файл набора, а не поле: поле собирается случайной нарезкой,
 * и по одной партии не видно, что напечатано на картоне. Набор же обязан быть
 * правильным целиком — перепечатывать его дорого.
 */
class BlocksPrintTest {

    private static List<BlockStamp.Face> faces() {
        BlockStamp.resetCache();
        List<BlockStamp.Face> all = BlockStamp.faces(GameConfig.resolveDataRoot(null));
        assertTrue(!all.isEmpty(), "набор блоков не прочитался");
        return all;
    }

    /** Двадцать сторон: 5 малых по 5 гексов и 5 больших по 6, каждый двусторонний. */
    @Test
    void theSetHasTwentyFaces() {
        List<BlockStamp.Face> all = faces();
        assertEquals(20, all.size(), "20 сторон: по две у каждого из десяти блоков");
        for (BlockStamp.Face f : all) {
            int want = "small".equals(f.kind()) ? 5 : 6;
            assertEquals(want, f.size(),
                "сторона " + f.blockId() + f.side() + ": гексов должно быть " + want);
        }
    }

    /**
     * ЖЁЛТАЯ ЯЧЕЙКА есть на КАЖДОМ гексе набора, всегда наземная и никогда не
     * та же, что контейнерная. Это главное, ради чего картон перепечатан.
     */
    @Test
    void everyHexOfTheSetCarriesAGroundEnergyCellApartFromTheContainer() {
        int hexes = 0;
        for (BlockStamp.Face f : faces()) {
            for (BlockStamp.Cell c : f.cells()) {
                hexes++;
                String where = "сторона " + f.blockId() + f.side();
                assertTrue(c.energy() >= 0 && c.energy() < 6,
                    where + ": жёлтая ячейка обязана быть наземной (0..5), а не " + c.energy());
                assertTrue(c.container() != c.energy(),
                    where + ": жёлтая ячейка совпала с контейнерной (" + c.energy() + ")");
            }
        }
        assertEquals(110, hexes, "в наборе 5×5×2 + 5×6×2 = 110 гексов");
    }

    /** Контейнеры набора: по 4 на малой стороне и по 5 на большой, ровно один воздушный. */
    @Test
    void containersPerFaceMatchTheRules() {
        for (BlockStamp.Face f : faces()) {
            int containers = 0;
            int air = 0;
            for (BlockStamp.Cell c : f.cells()) {
                if (c.container() >= 0) {
                    containers++;
                }
                if (c.container() == BlockStamp.AIR) {
                    air++;
                }
            }
            String where = "сторона " + f.blockId() + f.side();
            assertEquals("small".equals(f.kind()) ? 4 : 5, containers,
                where + ": контейнеров на стороне");
            assertEquals(1, air, where + ": ровно один контейнер в воздушной ячейке");
        }
    }

    /**
     * Жёлтые ячейки разложены по шести сторонам РОВНО. Перекос — брак печати:
     * если четверть картона смотрит жёлтой ячейкой в одну сторону, поле
     * перестаёт быть одинаковым для всех мест за столом.
     */
    @Test
    void theEnergyCellsAreSpreadEvenlyOverTheSixSides() {
        int[] bySide = new int[6];
        for (BlockStamp.Face f : faces()) {
            for (BlockStamp.Cell c : f.cells()) {
                bySide[c.energy()]++;
            }
        }
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (int n : bySide) {
            min = Math.min(min, n);
            max = Math.max(max, n);
        }
        assertTrue(max - min <= 2,
            "перекос жёлтых ячеек по сторонам: " + java.util.Arrays.toString(bySide));
    }
}
