package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import kelium.engine.BlockAssembler;
import kelium.engine.BlockAssembler.Cell;
import kelium.engine.BlockAssembler.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * КАЧЕСТВО СБОРКИ, А НЕ ПЕРВАЯ ПОПАВШАЯСЯ (правило дизайнера 15.09.2026).
 *
 * <p>Сборок с минимальным числом чёрных накладок бывает много, и раньше
 * выдавалась любая из них: блок ложился «вдоль», поле выходило вытянутым.
 * Теперь среди равных по накладкам выбирается самая близкая к квадрату,
 * а при равной форме — с ровным расходом больших и малых блоков.
 */
class КачествоСборкиTest {

    /** Поле из 21 гекса: три ряда по семь — заведомо вытянутая заготовка. */
    private static Set<Cell> полоса() {
        Set<Cell> out = new LinkedHashSet<>();
        for (int r = 0; r < 3; r++) {
            for (int q = 0; q < 7; q++) {
                out.add(new Cell(q - r / 2, r));
            }
        }
        return out;
    }

    @Test
    @DisplayName("варианты отдаются по качеству: первый не хуже остальных")
    void вариантыОтсортированы() {
        List<Result> варианты = BlockAssembler.solveVariants(
            полоса(), 4, 4, 9, 2000, 6);
        assertTrue(варианты.size() > 1, "нужно несколько сборок для сравнения");
        for (Result r : варианты) {
            assertEquals(BlockAssembler.Status.OK, r.status());
        }
        Result первый = варианты.get(0);
        for (Result другой : варианты) {
            assertTrue(BlockAssembler.ПО_КАЧЕСТВУ.compare(первый, другой) <= 0,
                "первый вариант обязан быть лучшим по качеству");
        }
    }

    @Test
    @DisplayName("накладки важнее формы: лишнюю заглушку ради квадрата не берём")
    void накладкиВажнееФормы() {
        Set<Cell> поле = полоса();
        Result r = BlockAssembler.solve(поле, 4, 4, 9, 2000);
        assertEquals(BlockAssembler.Status.OK, r.status());
        // Сверяемся не с арифметическим минимумом (он не всегда достижим
        // геометрически), а с лучшим, что вообще нашлось для этого поля.
        int минимум = r.blacks().size();
        for (Result v : BlockAssembler.solveVariants(поле, 4, 4, 9, 2000, 6)) {
            минимум = Math.min(минимум, v.blacks().size());
        }
        assertEquals(минимум, r.blacks().size(), "число накладок остаётся минимальным");
    }

    @Test
    @DisplayName("сборка без единого большого блока штрафуется")
    void большойБлокПредпочтителен() {
        Set<Cell> поле = полоса();
        Result r = BlockAssembler.solve(поле, 4, 4, 9, 2000);
        assertTrue(r.bigUsed() >= 1, "хотя бы один большой блок должен пойти в дело");
    }
}
