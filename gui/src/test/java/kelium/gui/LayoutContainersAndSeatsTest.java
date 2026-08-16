package kelium.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.engine.Scenario;

/**
 * Две правки конструктора от 14.08.2026 (п. 12 заказа 13.08.2026): контейнеры
 * снова рисуются на поле, а стартов игроков нельзя поставить больше четырёх.
 *
 * <p>Контейнер здесь — РАЗМЕТКА ПЕЧАТИ: он обязан пережить запись в файл и
 * чтение обратно, но правила его не читают, поэтому загрузчик сценария должен
 * принять такое поле молча и без изменений в игровом состоянии.
 */
class LayoutContainersAndSeatsTest {

    /** Поле из трёх гексов в ряд — минимум, который принимает загрузчик. */
    private static LayoutEditor.Model row(int n) {
        LayoutEditor.Model m = new LayoutEditor.Model();
        for (int q = 0; q < n; q++) {
            m.hexes.put(LayoutEditor.Model.key(q, 0), new LayoutEditor.LHex(q, 0));
        }
        return m;
    }

    @Test
    void containersSurviveSaveAndLoad() {
        LayoutEditor.Model m = row(3);
        m.get(0, 0).containers = 1;
        m.get(2, 0).containers = 2;

        Map<String, Object> scn = LayoutEditor.toScenarioMap(m, "t");
        LayoutEditor.loadScenarioIntoModel(scn);
        LayoutEditor.Model back = LayoutEditor.modelRef();

        // Запись ЦЕНТРИРУЕТ координаты (правило дизайнера 16.08.2026): ряд
        // q = 0..2 сохраняется как q = −1..+1, поэтому крайние гексы после
        // загрузки лежат на −1 и +1, а середина — на нуле.
        assertEquals(1, back.get(-1, 0).containers, "один контейнер вернулся");
        assertEquals(2, back.get(1, 0).containers, "два контейнера вернулись");
        assertEquals(0, back.get(0, 0).containers, "где не рисовали — пусто");
        assertEquals("normal", back.get(-1, 0).content,
            "контейнер не занимает содержимое гекса: на нём по-прежнему строят");
    }

    @Test
    void simulatorLoaderAcceptsDrawnContainers() {
        LayoutEditor.Model m = row(3);
        m.get(1, 0).containers = 2;
        var field = Scenario.buildFieldFromScenario(
            LayoutEditor.toScenarioMap(m, "t")).field();
        assertEquals(3, field.size(), "поле прочиталось целиком");
        // Разметка печати в игру не попадает: контейнеры движку даёт штамповка
        // блоков, а не файл поля.
        assertEquals(-1, field.get("h1_0").containerCell,
            "нарисованный контейнер не превращается в печатную ячейку движка");
    }

    @Test
    void oldFormatContentContainerBecomesMarkup() {
        List<Object> hexes = new ArrayList<>();
        for (int q = 0; q < 3; q++) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("q", q);
            e.put("r", 0);
            if (q == 1) {
                e.put("content", "container");   // формат до «Контейнеров 2.0»
            }
            hexes.add(e);
        }
        Map<String, Object> scn = new LinkedHashMap<>();
        scn.put("id", "old");
        scn.put("hexes", hexes);
        LayoutEditor.loadScenarioIntoModel(scn);

        LayoutEditor.LHex h = LayoutEditor.modelRef().get(1, 0);
        assertEquals("normal", h.content, "старое содержимое «container» снято");
        assertEquals(1, h.containers, "и перенесено в разметку как один контейнер");
    }

    @Test
    void fifthStartIsRefused() {
        LayoutEditor.Model m = row(8);
        for (int seat = 0; seat < 4; seat++) {
            LayoutEditor.LHex h = m.get(seat, 0);
            h.content = "player_start";
            h.seat = seat;
        }
        assertEquals(4, m.players(), "четыре старта расставлены");
        assertFalse(LayoutEditor.canAddStart(m), "пятый старт ставить нельзя");

        m.get(3, 0).content = "normal";
        assertTrue(LayoutEditor.canAddStart(m), "сняли лишний — снова можно");
    }

    @Test
    void journalComplainsAboutFifthStartFromFile() {
        LayoutEditor.Model m = row(12);
        for (int seat = 0; seat < 5; seat++) {
            LayoutEditor.LHex h = m.get(seat * 2, 0);
            h.content = "player_start";
            h.seat = seat;
        }
        boolean caught = LayoutEditor.validate(m).stream()
            .anyMatch(i -> i.level() == 2 && i.text().contains("мест в игре не бывает"));
        assertTrue(caught, "журнал ловит пятый старт, пришедший из файла");
    }
}
