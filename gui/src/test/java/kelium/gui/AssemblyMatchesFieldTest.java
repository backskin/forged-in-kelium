package kelium.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import kelium.gui.BlockAssembler.Cell;
import kelium.gui.BlockAssembler.Placement;
import kelium.gui.BlockAssembler.Result;

/**
 * СБОРКА ИЗ БЛОКОВ ОБЯЗАНА СОВПАДАТЬ С НАРИСОВАННЫМ ПОЛЕМ.
 *
 * <p>Баг дизайнера 12.08.2026: открываешь раскладку — вкладка «Сборка из блоков»
 * показывает сборку ДРУГОГО поля. Причина: «Новая раскладка» подменяла объект
 * модели ({@code model = new Model()}), а вкладка сборки держала ссылку на
 * прежний объект и считала сборку для старого поля. Теперь модель одна на всё
 * приложение и только очищается — поле {@code final}, подменить его нельзя.
 *
 * <p>Тест закрывает саму суть жалобы: для реальной раскладки из данных сборка
 * покрывает РОВНО те гексы, что нарисованы, ни больше ни меньше.
 */
class AssemblyMatchesFieldTest {

    /** Игровые гексы модели — так же, как их берёт вкладка сборки. */
    private static Set<Cell> playable(LayoutEditor.Model m) {
        Set<Cell> out = new LinkedHashSet<>();
        for (LayoutEditor.LHex h : m.hexes.values()) {
            if (!"forbidden".equals(h.content)) {
                out.add(new Cell(h.q, h.r));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstScenario(Path file) throws Exception {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Map<String, Object> doc = new org.yaml.snakeyaml.Yaml().load(text);
        List<Map<String, Object>> scns = (List<Map<String, Object>>) doc.get("scenarios");
        return scns.get(0);
    }

    /** Раскладка дизайнера, на которой баг и заметили. */
    private static Path sample() {
        Path p = kelium.dataio.GameConfig.resolveDataRoot(null).resolve("scenarios")
            .resolve("new").resolve("агрессивное 4 игрока-looksmaxxing.yaml");
        return Files.exists(p) ? p : null;
    }

    @Test
    void assemblyCoversExactlyTheLoadedField() throws Exception {
        Path file = sample();
        if (file == null) {
            return;               // файла нет в этой копии данных — проверять нечего
        }
        LayoutEditor.loadScenarioIntoModel(firstScenario(file));
        Set<Cell> field = playable(LayoutEditor.modelRef());
        assertEquals(29, field.size(), "в этой раскладке 29 игровых гексов");

        List<Result> variants = BlockAssembler.solveVariants(field, 5, 5, 8, 4000, 4);
        assertTrue(!variants.isEmpty(), "сборка должна находиться");
        for (Result r : variants) {
            assertEquals(BlockAssembler.Status.OK, r.status(), "поле собирается из запаса");
            Set<Cell> covered = new HashSet<>();
            for (Placement p : r.blocks()) {
                covered.addAll(p.cells());
            }
            Set<Cell> missing = new LinkedHashSet<>(field);
            missing.removeAll(covered);
            assertTrue(missing.isEmpty(), "блоки не накрыли гексы поля: " + missing);
            Set<Cell> extra = new LinkedHashSet<>(covered);
            extra.removeAll(field);
            for (Cell c : extra) {
                assertTrue(r.blacks().contains(c),
                    "лишний гекс под блоком обязан быть закрыт накладкой: " + c);
            }
        }
    }

    /** Модель одна на всё приложение: вкладка сборки видит то же поле, что полотно. */
    @Test
    void thereIsOnlyOneModelInstance() throws Exception {
        LayoutEditor.Model before = LayoutEditor.modelRef();
        Path file = sample();
        if (file != null) {
            LayoutEditor.loadScenarioIntoModel(firstScenario(file));
        }
        assertTrue(before == LayoutEditor.modelRef(),
            "загрузка раскладки не должна подменять объект модели");
    }
}
