package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;
import kelium.engine.LayoutLibrary;

/**
 * Библиотека раскладок: поля, нарисованные конструктором и сохранённые своим
 * именем, должны находиться и РЕАЛЬНО играться — не только показываться в списке.
 */
class LayoutLibraryTest {

    @Test
    void findsAuthorLayoutsInTheDataFolder() {
        for (int players : new int[]{2, 3, 4}) {
            List<String> problems = new ArrayList<>();
            List<LayoutLibrary.Entry> found = LayoutLibrary.scan(players, problems);
            assertFalse(found.isEmpty(),
                "в папке данных не найдено ни одной раскладки на " + players);
            for (LayoutLibrary.Entry e : found) {
                assertEquals(players, e.players(),
                    "раскладка " + e.id() + " попала не в тот список игроков");
            }
        }
    }

    @Test
    void aLayoutFromTheLibraryCanActuallyBePlayed() {
        List<LayoutLibrary.Entry> found = LayoutLibrary.scan(4, new ArrayList<>());
        LayoutLibrary.Entry any = found.get(0);
        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 777L,
            null, null, any.id(), null, any.file());
        GameState s = Setup.buildGame(cfg);
        assertTrue(s.field.size() > 6,
            "поле из библиотеки не собралось: " + any.file());
        for (int seat = 0; seat < 4; seat++) {
            assertFalse(s.player(seat).startHex.isBlank(),
                "у места " + seat + " нет старта на раскладке " + any.id());
        }
    }

    @Test
    void unreadableFilesAreReportedButDoNotBreakTheScan() {
        List<String> problems = new ArrayList<>();
        LayoutLibrary.scan(4, problems);
        // В папке сценариев лежат и .bak, и служебные файлы — важно, что скан
        // их пережил и вернул хоть что-то; жалобы (если есть) собраны отдельно.
        assertFalse(LayoutLibrary.scan(4, null).isEmpty());
        for (String p : problems) {
            assertFalse(p.isBlank());
        }
    }
}
