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

    /**
     * СУПЕР-ЗАДАНИЯ «ОДНА КАРТА ВТАЙНЕ» КЛАССОВ НЕ ИМЕЮТ: их половины
     * разыгрывает {@code kelium.engine.Super5}, разбирая номер карты. Отличаем
     * их по полям, которых нет ни у одной другой формы супер-задания.
     */
    private static boolean втайне(Map<String, Object> card) {
        return card.containsKey("multiplier") || card.containsKey("stockpile");
    }

    @Test
    void каждойКартеИзДанныхЕстьКод() {
        bindAll();
        assertTrue(CardRegistry.missing().isEmpty(),
            "в данных есть карты, для которых не написан код: "
                + String.join(", ", CardRegistry.missing()));
    }

    /**
     * ...А У СУПЕР-ЗАДАНИЙ «ВТАЙНЕ» — ВЕТКА В ДВИЖКЕ. Класса у них нет, но
     * молча ничего не делающая карта — беда та же, поэтому сторожим отдельно.
     */
    @Test
    void каждоеСуперЗаданиеВтайнеДвижокЗнает() {
        java.util.List<String> нет = new java.util.ArrayList<>();
        for (Map<String, Object> card : entries("super_objectives")) {
            if (втайне(card) && !kelium.engine.Super5.знает(String.valueOf(card.get("id")))) {
                нет.add(String.valueOf(card.get("id")));
            }
        }
        assertTrue(нет.isEmpty(),
            "супер-задания есть в данных, но движок их не разыгрывает: " + нет);
    }

    /** Связать ВСЕ переехавшие семейства: иначе часть карт останется без данных. */
    private static void bindAll() {
        CardRegistry.bindAll("objectives", entries("objectives"));
        CardRegistry.bindAll("arsenal", entries("arsenal"));
        CardRegistry.bindAll("containers", entries("containers"));
        CardRegistry.bindAll("market", entries("market"));
        // Карты «втайне» связывать не с чем — классов у них нет по устройству.
        CardRegistry.bindAll("super_objectives", entries("super_objectives").stream()
            .filter(c -> !втайне(c)).toList());
        CardRegistry.bindAll("super_arsenal", entries("super_arsenal"));
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

    /**
     * У КАЖДОЙ КАРТЫ ДАННЫХ КОД НАШЁЛСЯ ИМЕННО ПО ЕЁ НОМЕРУ.
     *
     * <p>Раньше здесь сравнивались ЧИСЛА: сколько карт в данных и сколько
     * классов в реестре. Такая проверка держалась только пока колоды не
     * версионировались: классы выбывших карт живут в дереве и дальше, потому что
     * старые версии наборов должны читаться как были, — и стоило колоде
     * похудеть, как «в коде 162, в данных 137» валило сборку, хотя не сломано
     * ничего. Сторожить надо не равенство чисел, а то, что каждая карта
     * ДЕЙСТВУЮЩЕЙ колоды нашла свой класс и подхватила из него имя.
     */
    @Test
    void каждаяКартаДанныхНашлаСвойКласс() {
        bindAll();
        java.util.List<String> плохие = new java.util.ArrayList<>();
        for (String family : java.util.List.of("objectives", "arsenal", "market",
                "super_arsenal")) {
            for (Map<String, Object> entry : entries(family)) {
                String id = String.valueOf(entry.get("id"));
                Card c = CardRegistry.find(id);
                if (c == null) {
                    плохие.add(family + "/" + id + ": класса нет");
                } else if (!String.valueOf(entry.get("name")).equals(c.name())) {
                    плохие.add(family + "/" + id + ": имя в данных «" + entry.get("name")
                        + "», а в коде «" + c.name() + "»");
                }
            }
        }
        assertTrue(плохие.isEmpty(), "карты данных и код разошлись: " + плохие);
    }
}
