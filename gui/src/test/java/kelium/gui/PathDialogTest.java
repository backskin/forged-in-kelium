package kelium.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * ИМЯ ФАЙЛА В ДИАЛОГЕ: расширение дописывается ТОЛЬКО при сохранении.
 *
 * <p>Баг дизайнера 14.08.2026: открыл «сценарий Ч игрока 6.yaml» двойным
 * кликом в списке — имя подставилось верно, но по кнопке «Открыть» дописалось
 * «.kmap» поверх готового имени, получился несуществующий путь
 * «…6.yaml.kmap» и «Файл не найден» на файле, который только что был на
 * экране.
 */
class PathDialogTest {

    @Test
    void openingDoesNotAppendExtensionToAnAlreadyNamedFile() {
        assertEquals("сценарий 6.yaml",
            PathDialog.withExtIfSaving("сценарий 6.yaml", false, "kmap"),
            "открытие не трогает готовое имя, даже с чужим расширением");
    }

    @Test
    void openingLeavesExtensionlessNameAlone() {
        assertEquals("сценарий 6",
            PathDialog.withExtIfSaving("сценарий 6", false, "kmap"),
            "открытие тоже не дописывает расширение — искать нечего, файла с таким именем нет");
    }

    @Test
    void savingAppendsTheExtensionWhenMissing() {
        assertEquals("раскладка.kmap",
            PathDialog.withExtIfSaving("раскладка", true, "kmap"));
    }

    @Test
    void savingDoesNotDoubleTheExtension() {
        assertEquals("раскладка.kmap",
            PathDialog.withExtIfSaving("раскладка.kmap", true, "kmap"));
    }
}
