package kelium.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.cards.objectives.ЗаданиеИзЯзыка;
import kelium.cards.язык.Группа;
import kelium.cards.язык.Кто;
import kelium.cards.язык.Требование;
import kelium.core.GameState;
import kelium.core.TurnJournal;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;
import kelium.engine.cards.CardRegistry;
import kelium.engine.cards.EngineCardContext;
import kelium.engine.cards.ObjectiveCard;

/**
 * ЯЗЫК КАРТ (Карты 2.0, 30.09.2026): задание из данных строится без класса,
 * печатает себя словами игры и проверяется по столу и журналу хода.
 */
class ЯзыкКартTest {

    private static Map<String, Object> запись(String id, Map<String, Object> язык) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", id);
        e.put("язык", язык);
        return e;
    }

    @Test
    void фразыСклоняютсяПоЧислу() {
        Группа своиДобытчики = new Группа(Кто.ДОБЫТЧИК, true, Группа.Состояние.ЗАПИТАН);
        assertEquals("свой запитанный добытчик", своиДобытчики.фраза(1));
        assertEquals("2 своих запитанных добытчика", своиДобытчики.фраза(2));
        assertEquals("5 своих запитанных добытчиков", своиДобытчики.фраза(5));
        Группа станцияВрага = new Группа(Кто.ЭНЕРГОСТАНЦИЯ, false);
        assertEquals("энергостанцию врага", станцияВрага.фраза(1));
        assertEquals("3 энергостанции врага", станцияВрага.фраза(3));
        assertEquals("энергостанция врага", станцияВрага.гдеЕсть());
        assertEquals("своё военное здание", new Группа(Кто.ВОЕННОЕ, true).фраза(1));
        assertEquals("свою казарму", new Группа(Кто.КАЗАРМА, true).фраза(1));
    }

    @Test
    void заданиеИзДанныхСтроитсяПечатаетсяИПроверяется() {
        Map<String, Object> язык = new LinkedHashMap<>();
        язык.put("имя", "Клещи");
        язык.put("требование", Map.of("узел", "обе_ветки", "развилка", "command"));
        язык.put("усиление", Map.of("узел", "уничтожь",
            "цель", Map.of("кто", "ЗДАНИЕ", "чьё", "врага"), "сколько", 1));
        язык.put("награда", Map.of("действие", "mining|build_miner", "монеты", 1));
        язык.put("сверх", Map.of("спецДействий", 1));
        язык.put("верх", "ДВИЖЕНИЕ");
        CardRegistry.bindAll("objectives", List.of(запись("g_test1", язык)));
        ObjectiveCard карта = CardRegistry.objective("g_test1");
        assertTrue(карта instanceof ЗаданиеИзЯзыка, "фабрика собрала карту из данных");
        ЗаданиеИзЯзыка з = (ЗаданиеИзЯзыка) карта;
        assertEquals("Клещи", з.name());
        assertEquals("В ЭТОТ ХОД сыграй обе ветки Командования", з.лицо().условие());
        assertNull(з.лицо().жалоба(), "форма карты по правилам дизайнера");

        GameState s = Setup.buildGame(GameConfig.build(4, 3L));
        s.journal = new TurnJournal(4);
        s.journal.startTurn(0);
        EngineCardContext ctx = new EngineCardContext(s, 0);
        assertFalse(з.satisfied(ctx));
        assertEquals(0.0, з.progress(ctx), 1e-9);
        s.journal.of(0).веткиХода.add("movement");
        assertEquals(0.5, з.progress(ctx), 1e-9);
        s.journal.of(0).веткиХода.add("combat");
        assertTrue(з.satisfied(ctx));
        assertFalse(з.satisfiedEnhanced(ctx), "здание врага ещё не уничтожено");
    }

    @Test
    void текстСрабатывания() {
        assertEquals("Каждый раз, когда играешь ветку «Добыть», получи 1 монету. Не больше 1 раза за ход.",
            kelium.cards.язык.Срабатывание.текст(Map.of(
                "когда", Map.of("событие", "ветка", "ветка", "mining"),
                "эффект", Map.of("effect", "gain", "params", Map.of("coin", 1)),
                "предел", 1)));
        assertEquals("Каждый раз, когда уничтожаешь здание врага техникой, получи ещё одно "
                + "спец-действие. Не больше 2 раз за ход.",
            kelium.cards.язык.Срабатывание.текст(Map.of(
                "когда", Map.of("событие", "уничтожил", "жертва", "building", "кем", "vehicle"),
                "эффект", Map.of("effect", "спец", "params", Map.of("n", 1)),
                "предел", 2)));
    }

    @Test
    void текстыУзлов() {
        assertEquals("В ЭТОТ ХОД сыграй «Добыть» и Бой",
            Требование.из(Map.of("узел", "ветки", "ветки", List.of("mining", "combat"))).текст());
        assertEquals("В ЭТОТ ХОД выполни это задание вторым",
            Требование.из(Map.of("узел", "очередь_задания", "k", 2)).текст());
        assertEquals("Имей своё военное здание на гексе, соседнем с гексом, где есть войско врага",
            Требование.из(Map.of("узел", "рядом",
                "а", Map.of("кто", "ВОЕННОЕ", "чьё", "своё"),
                "б", Map.of("кто", "ВОЙСКО", "чьё", "врага"))).текст());
        assertEquals("В ЭТОТ ХОД уничтожь 2 здания врага техникой",
            Требование.из(Map.of("узел", "уничтожь", "цель", Map.of("кто", "ЗДАНИЕ", "чьё", "врага"),
                "кем", "ТЕХНИКА", "сколько", 2)).текст());
        assertEquals("Имей не меньше 6 монет",
            Требование.из(Map.of("узел", "ресурс", "ресурс", "COIN", "сколько", 6)).текст());
    }
}
