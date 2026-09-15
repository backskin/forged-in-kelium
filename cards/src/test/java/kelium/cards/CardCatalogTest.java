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
     * СУПЕР-ЗАДАНИЯ КЛАССОВ НЕ ИМЕЮТ. В редакции 8.0 карта — это ПАРА
     * КАТЕГОРИЙ СЧЁТА и ничего больше: её нельзя выполнить или сжечь, значит и
     * кода у неё нет. Прежние редакции («множитель верха», «накопитель»)
     * узнаются по своим полям.
     */
    private static boolean безКода(Map<String, Object> card) {
        return card.containsKey("categories")
            || card.containsKey("multiplier") || card.containsKey("stockpile");
    }

    @Test
    void каждойКартеИзДанныхЕстьКод() {
        bindAll();
        assertTrue(CardRegistry.missing().isEmpty(),
            "в данных есть карты, для которых не написан код: "
                + String.join(", ", CardRegistry.missing()));
    }

    /**
     * ...А У СУПЕР-ЗАДАНИЙ — КАТЕГОРИИ СЧЁТА. Класса у карты нет, но
     * неизвестная движку категория молча платит ноль и выглядит рабочей —
     * сторожим её отдельно. Заодно проверяем, что категорий на карте ровно
     * две и они разные.
     */
    @Test
    void каждуюКатегориюСуперЗаданияДвижокСчитает() {
        java.util.List<String> беды = new java.util.ArrayList<>();
        for (Map<String, Object> card : entries("super_objectives")) {
            if (!(card.get("categories") instanceof java.util.List<?> cats)) {
                continue;
            }
            String id = String.valueOf(card.get("id"));
            if (cats.size() != 2 || cats.get(0).equals(cats.get(1))) {
                беды.add(id + ": на карте должна быть пара РАЗНЫХ категорий, а стоит " + cats);
            }
            for (Object c : cats) {
                if (!kelium.engine.СуперЗадания.знаетКатегорию(String.valueOf(c))) {
                    беды.add(id + ": движок не знает категорию " + c);
                }
            }
        }
        assertTrue(беды.isEmpty(), "супер-задания в данных не считаются: " + беды);
    }

    /** Связать ВСЕ переехавшие семейства: иначе часть карт останется без данных. */
    private static void bindAll() {
        CardRegistry.bindAll("objectives", entries("objectives"));
        CardRegistry.bindAll("arsenal", entries("arsenal"));
        // КОНТЕЙНЕРЫ КЛАССОВ НЕ ИМЕЮТ И НЕ СВЯЗЫВАЮТСЯ: у контейнера нет
        // поведения, только напечатанная награда, и движок читает её прямо из
        // данных (containers.*.yaml, см. ContentLibrary.bindCardsInCode).
        // Раньше здесь связывалась колода в коде c01-c32 с названиями и
        // выбором из двух сторон — она отменена дизайнером целиком.
        CardRegistry.bindAll("market", entries("market"));
        // Карты «втайне» связывать не с чем — классов у них нет по устройству.
        CardRegistry.bindAll("super_objectives", entries("super_objectives").stream()
            .filter(c -> !безКода(c)).toList());
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
