package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import kelium.cards.objectives.СемействаЗаданий;
import kelium.dataio.ContentSet;
import kelium.dataio.GameConfig;

/**
 * СЕМЬ СЕМЕЙСТВ КОЛОДЫ ЗАДАНИЙ — разложены ли по ним ВСЕ сорок карт.
 *
 * <p>Семейство — рабочий признак: по нему дизайнер раздаёт утиль, и по нему же
 * читается таблица карт. Список семейств лежит отдельно от колоды, значит
 * разойтись с ней может молча: карту заменили — номер в списке остался старый,
 * и карта тихо выпала из всех разбивок «по семействам», показав ноль там, где
 * на самом деле единица.
 */
class ObjectiveFamiliesTest {

    private static ContentSet objectives() {
        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 7L, null, null);
        return cfg.content.get("objectives");
    }

    private static List<Map<String, Object>> обычные() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> c : objectives().entries) {
            if (!"starting".equals(String.valueOf(c.get("kind")))) {
                out.add(c);
            }
        }
        return out;
    }

    @Test
    void всеСорокКартРазложеныПоСемействам() {
        List<String> без = new ArrayList<>();
        for (Map<String, Object> c : обычные()) {
            String id = String.valueOf(c.get("id"));
            if ("—".equals(СемействаЗаданий.семейство(id))) {
                без.add(id + " «" + c.get("name") + "»");
            }
        }
        assertTrue(без.isEmpty(), "карты вне семейств: " + без);
        assertEquals(обычные().size(), СемействаЗаданий.карт(),
            "в списке семейств столько же карт, сколько в колоде");
    }

    @Test
    void вСпискеСемействНетКартВнеКолоды() {
        Set<String> вКолоде = new HashSet<>();
        for (Map<String, Object> c : objectives().entries) {
            вКолоде.add(String.valueOf(c.get("id")));
        }
        List<String> лишние = new ArrayList<>();
        for (String семья : СемействаЗаданий.ИМЕНА) {
            for (String id : СемействаЗаданий.ПО_СЕМЕЙСТВАМ.get(семья)) {
                if (!вКолоде.contains(id)) {
                    лишние.add(семья + ": " + id);
                }
            }
        }
        assertTrue(лишние.isEmpty(), "в списке семейств карты, которых нет в колоде: " + лишние);
    }

    @Test
    void ниОднаКартаНеЛежитВДвухСемействах() {
        Set<String> видели = new HashSet<>();
        for (String семья : СемействаЗаданий.ИМЕНА) {
            for (String id : СемействаЗаданий.ПО_СЕМЕЙСТВАМ.get(семья)) {
                assertTrue(видели.add(id), "карта " + id + " лежит сразу в двух семействах");
            }
        }
    }

    @Test
    void начальныеЗаданияСемействНеИмеют() {
        for (Map<String, Object> c : objectives().entries) {
            if ("starting".equals(String.valueOf(c.get("kind")))) {
                assertEquals("—", СемействаЗаданий.семейство(String.valueOf(c.get("id"))),
                    "начальное задание " + c.get("id") + " не входит в таблицу семейств");
            }
        }
    }
}
