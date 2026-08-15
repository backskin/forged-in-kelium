package kelium.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import kelium.gui.BlockAssembler.Cell;
import kelium.gui.BlockAssembler.Placement;
import kelium.gui.BlockAssembler.Result;

/**
 * «Пересобрать» должна показывать ДРУГУЮ сборку, а не ту же самую
 * (просьба дизайнера 12.08.2026). Проверяем, что вариантов действительно
 * несколько, что они РАЗНЫЕ и что все укладываются в одинаковое —
 * минимальное — число чёрных накладок.
 */
class AssemblyVariantsTest {

    /** Поле-параллелограмм width×height. */
    private static Set<Cell> field(int width, int height) {
        Set<Cell> out = new HashSet<>();
        for (int r = 0; r < height; r++) {
            for (int q = 0; q < width; q++) {
                out.add(new Cell(q, r));
            }
        }
        return out;
    }

    /** Отпечаток сборки: набор блоков без учёта порядка постановки. */
    private static String signature(Result r) {
        List<String> parts = new ArrayList<>();
        for (Placement p : r.blocks()) {
            List<String> cells = new ArrayList<>();
            for (Cell c : p.cells()) {
                cells.add(c.q() + ":" + c.r());
            }
            java.util.Collections.sort(cells);
            parts.add(String.join(",", cells));
        }
        java.util.Collections.sort(parts);
        return String.join("|", parts);
    }

    @Test
    void severalDistinctAssembliesAreFound() {
        Set<Cell> playable = field(5, 4);      // 20 клеток — раскладывается по-разному
        List<Result> variants =
            BlockAssembler.solveVariants(playable, 6, 6, 10, 4000, 6);

        assertTrue(variants.size() >= 2,
            "для такого поля обязано найтись больше одной сборки, найдено " + variants.size());

        Set<String> seen = new HashSet<>();
        for (Result r : variants) {
            assertEquals(BlockAssembler.Status.OK, r.status());
            assertTrue(seen.add(signature(r)), "варианты не должны повторяться");
        }
    }

    @Test
    void allVariantsUseTheSameMinimalNumberOfBlackTiles() {
        Set<Cell> playable = field(5, 4);
        List<Result> variants =
            BlockAssembler.solveVariants(playable, 6, 6, 10, 4000, 6);
        int expected = variants.get(0).blacks().size();
        for (Result r : variants) {
            assertEquals(expected, r.blacks().size(),
                "показывать вариант с лишними накладками смысла нет");
        }
    }

    @Test
    void everyVariantCoversTheWholeField() {
        Set<Cell> playable = field(5, 4);
        for (Result r : BlockAssembler.solveVariants(playable, 6, 6, 10, 4000, 4)) {
            Set<Cell> covered = new HashSet<>();
            for (Placement p : r.blocks()) {
                covered.addAll(p.cells());
            }
            for (Cell c : playable) {
                assertTrue(covered.contains(c),
                    "клетка " + c + " осталась не покрытой блоками");
            }
        }
    }
}
