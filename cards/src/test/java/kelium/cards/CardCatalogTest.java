package kelium.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.dataio.GameConfig;
import kelium.engine.cards.Card;
import kelium.engine.cards.CardRegistry;
import kelium.engine.cards.ObjectiveCard;

/**
 * СТОРОЖ КАТАЛОГА КАРТ.
 *
 * <p>Ради этого теста модуль и затевался. До него карта могла тихо исчезнуть из
 * игры тремя разными способами, и ни один не был виден:
 *
 * <ul>
 *   <li>эффект не реализован — движок ВЫБРАСЫВАЛ карту из колоды на подготовке,
 *       сообщая об этом одной строкой в системный лог (так из игры пропали шесть
 *       карт, и заметил это только целенаправленный замер);</li>
 *   <li>условие не зарегистрировано в реестре предикатов — карта оставалась в
 *       колоде, но не выполнялась НИКОГДА и выглядела при этом рабочей;</li>
 *   <li>карту добавили в данные и забыли написать ей код — она просто ничего не
 *       делала.</li>
 * </ul>
 *
 * <p>Теперь каждый из трёх случаев валит тест с внятным сообщением.
 */
class CardCatalogTest {

    private static List<Map<String, Object>> entries(String family) {
        GameConfig cfg = GameConfig.build(4, 1L);
        return cfg.content.get(family).entries;
    }

    @Test
    void каждойКартеИзДанныхЕстьКод() {
        bindAll();
        assertTrue(CardRegistry.missing().isEmpty(),
            "в данных есть карты, для которых не написан код: "
                + String.join(", ", CardRegistry.missing()));
    }

    /** Связать ВСЕ переехавшие семейства: иначе часть карт останется без данных. */
    private static void bindAll() {
        CardRegistry.bindAll("objectives", entries("objectives"));
        CardRegistry.bindAll("arsenal", entries("arsenal"));
        CardRegistry.bindAll("containers", entries("containers"));
        CardRegistry.bindAll("market", entries("market"));
    }

    @Test
    void каждаяКартаЗнаетСвоёИмяИОписание() {
        bindAll();
        for (Card c : CardRegistry.all()) {
            assertNotNull(c.name(), "карта без имени: " + c.id());
            assertFalse(c.name().isBlank(), "карта с пустым именем: " + c.id());
            assertFalse(c.name().equals(c.id()),
                "карта " + c.id() + " не подхватила имя из данных: либо номера "
                    + "в коде и в данных разошлись, либо действующий свод правил "
                    + "берёт СТАРУЮ версию каталога, где этой карты ещё нет");
            assertNotNull(c.describe(), "карта без описания: " + c.id());
        }
    }

    @Test
    void укаждойКартыЗаданияЕстьТребованиеИНаграда() {
        bindAll();
        for (Card c : CardRegistry.all()) {
            if (!(c instanceof ObjectiveCard)) {
                continue;
            }
            assertTrue(c.data().containsKey("requirement"),
                "задание " + c.id() + " (" + c.name() + ") без требования");
            assertTrue(c.data().containsKey("base_reward")
                    || c.data().containsKey("reward"),
                "задание " + c.id() + " (" + c.name() + ") без награды");
        }
    }

    @Test
    void номераКартВКодеИВДанныхСовпадают() {
        bindAll();
        // СЧИТАЕМ ПО СЕМЕЙСТВАМ. Реестр общий на все карты, поэтому сравнивать
        // его размер с одним набором данных нельзя: подключили арсенал — и
        // проверка заданий начала врать.
        int inData = entries("objectives").size() + entries("arsenal").size()
            + entries("containers").size() + entries("market").size();
        assertEquals(inData, CardRegistry.all().size(),
            "число карт в коде и в данных разошлось: в данных " + inData
                + ", в коде " + CardRegistry.all().size());
    }
}
