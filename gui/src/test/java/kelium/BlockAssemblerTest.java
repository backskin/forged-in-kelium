package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import kelium.gui.BlockAssembler;
import kelium.gui.BlockAssembler.Cell;
import kelium.gui.BlockAssembler.Placement;

/**
 * Сборка поля из физических блоков: формы, покрытие, честный отказ.
 */
class BlockAssemblerTest {

    private static Set<Cell> cells(int[][] qr) {
        Set<Cell> s = new HashSet<>();
        for (int[] c : qr) {
            s.add(new Cell(c[0], c[1]));
        }
        return s;
    }

    /** Все клетки покрыты ровно один раз, блоки не пересекаются. */
    private static void assertValid(BlockAssembler.Result r, Set<Cell> playable, int maxBlack) {
        Set<Cell> covered = new HashSet<>();
        for (Placement p : r.blocks()) {
            assertEquals(p.size(), p.cells().size(), "размер блока совпадает с числом клеток");
            for (Cell c : p.cells()) {
                assertTrue(covered.add(c), "клетка " + c + " покрыта дважды — блоки наложились");
            }
        }
        assertTrue(covered.containsAll(playable), "все игровые гексы накрыты блоками");
        int blacks = 0;
        for (Cell c : covered) {
            if (!playable.contains(c)) {
                blacks++;
            }
        }
        assertEquals(blacks, r.blacks().size(), "число чёрных накладок посчитано верно");
        assertTrue(blacks <= maxBlack, "накладок не больше запаса");
    }

    @Test
    void bothShapesHaveSixDistinctOrientations() {
        // Обе формы зеркально симметричны (у малого ось через центр, у большого
        // отражение совпадает с поворотом на 120°), поэтому переворот куска
        // НИЧЕГО нового не даёт: различных положений ровно 6, а не 12.
        List<List<Cell>> small = BlockAssembler.orientations(List.of(
            new Cell(0, 0), new Cell(1, 0), new Cell(2, 0), new Cell(0, 1), new Cell(1, 1)));
        List<List<Cell>> big = BlockAssembler.orientations(List.of(
            new Cell(0, 0), new Cell(1, 0), new Cell(2, 0), new Cell(0, 1), new Cell(1, 1),
            new Cell(1, -1)));
        assertEquals(6, small.size(), "малый блок: 6 положений");
        assertEquals(6, big.size(), "большой блок: 6 положений (отражение = поворот)");
    }

    @Test
    void singleSmallBlockIsAssembledExactly() {
        Set<Cell> playable = cells(new int[][]{{0, 0}, {1, 0}, {2, 0}, {0, 1}, {1, 1}});
        var r = BlockAssembler.solve(playable, 0, 1, 0, 3000);
        assertEquals(BlockAssembler.Status.OK, r.status(), "малый блок ложится точь-в-точь");
        assertEquals(1, r.blocks().size());
        assertEquals(0, r.blacks().size(), "без накладок");
        assertValid(r, playable, 0);
    }

    @Test
    void rotatedShapeIsFoundToo() {
        // тот же малый блок, повёрнутый на 60°: (q,r) -> (-r, q+r)
        Set<Cell> playable = new HashSet<>();
        for (Cell c : List.of(new Cell(0, 0), new Cell(1, 0), new Cell(2, 0),
                new Cell(0, 1), new Cell(1, 1))) {
            playable.add(new Cell(-c.r(), c.q() + c.r()));
        }
        var r = BlockAssembler.solve(playable, 0, 1, 0, 3000);
        assertEquals(BlockAssembler.Status.OK, r.status(), "повороты блока учитываются");
        assertValid(r, playable, 0);
    }

    @Test
    void realisticFieldIsAssembledWithSpareBlocks() {
        // «сотовое» поле из 22 гексов: три ряда 7/8/7 — типичная раскладка
        Set<Cell> playable = new HashSet<>();
        for (int q = 0; q < 7; q++) {
            playable.add(new Cell(q, 0));
        }
        for (int q = -1; q < 7; q++) {
            playable.add(new Cell(q, 1));
        }
        for (int q = -1; q < 6; q++) {
            playable.add(new Cell(q, 2));
        }
        var r = BlockAssembler.solve(playable, 5, 5, 8, 8000);
        assertEquals(BlockAssembler.Status.OK, r.status(),
            "поле из 22 гексов собирается из запаса 5+5 блоков");
        assertValid(r, playable, 8);
    }

    @Test
    void impossibleFieldIsReportedHonestly() {
        // одинокий гекс: любой блок накроет минимум 5 клеток, значит нужно
        // минимум 4 накладки — а их ноль
        Set<Cell> playable = cells(new int[][]{{0, 0}});
        var r = BlockAssembler.solve(playable, 5, 5, 0, 3000);
        assertEquals(BlockAssembler.Status.IMPOSSIBLE, r.status(),
            "честный отказ: собрать нельзя");
        assertTrue(r.blocks().isEmpty(), "при отказе блоков не выдаём");
    }

    @Test
    void solverMinimisesBlackTiles() {
        // Поле из 10 гексов = ровно два малых блока (5+5) без единой накладки.
        // Запас щедрый (5 больших + 5 малых, 16 накладок) — жадное решение легко
        // взяло бы большой блок и потратило накладки; проверяем, что не взяло.
        Set<Cell> playable = new HashSet<>();
        for (int q = 0; q < 5; q++) {
            playable.add(new Cell(q, 0));
        }
        for (int q = 0; q < 5; q++) {
            playable.add(new Cell(q, 1));
        }
        var r = BlockAssembler.solve(playable, 5, 5, 16, 8000);
        assertEquals(BlockAssembler.Status.OK, r.status());
        assertEquals(0, r.blacks().size(), "накладки не нужны — решатель нашёл точную укладку");
        assertTrue(r.optimal(), "минимум доказан");
        assertValid(r, playable, 16);
    }

    @Test
    void minimalBlacksIsArithmeticallyCorrect() {
        // 22 гекса: 2 больших (12) + 2 малых (10) = 22 — ноль накладок
        assertEquals(0, BlockAssembler.minimalBlacks(22, 5, 5));
        // 1 гекс: минимальный блок кроет 5 — четыре клетки лишние
        assertEquals(4, BlockAssembler.minimalBlacks(1, 5, 5));
        // 7 гексов: 5+5=10 (3 лишних) либо 6+5=11 (4) либо 6+6=12 — минимум 3
        assertEquals(3, BlockAssembler.minimalBlacks(7, 5, 5));
    }

    @Test
    void blackTilesCoverSpillover() {
        // тот же одинокий гекс, но накладок хватает: блок ляжет с запасом
        Set<Cell> playable = cells(new int[][]{{0, 0}});
        var r = BlockAssembler.solve(playable, 0, 1, 4, 3000);
        assertEquals(BlockAssembler.Status.OK, r.status(), "с накладками собирается");
        assertEquals(4, r.blacks().size(), "лишние 4 гекса блока закрыты накладками");
        assertValid(r, playable, 4);
    }
}
