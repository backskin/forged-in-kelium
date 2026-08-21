package kelium.engine.cards;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * РЕЕСТР КАРТ — единственное место, где идентификатор из данных превращается в
 * объект с поведением.
 *
 * <p>Как карты сюда попадают. Движок НЕ ЗНАЕТ про модуль {@code cards} во время
 * сборки: реализации приходят через {@link ServiceLoader} по договору
 * {@link CardPack}. Есть модуль карт на класспасе — карты работают; нет —
 * движок собирается и тестируется без них. Так и задумано: движок ничего не
 * должен знать о конкретных картах.
 *
 * <p>ЧТО СЛУЧАЕТСЯ С КАРТОЙ БЕЗ КОДА. Раньше движок молча ВЫБРАСЫВАЛ такую
 * карту из колоды на подготовке — шесть карт исчезли из игры, и увидеть это
 * можно было только в системном логе. Теперь такая карта остаётся в колоде и
 * попадает в {@link #missing()}: список видно в отчётах и в справочнике, и он
 * же — прямой список работы. Тихого исчезновения больше нет.
 */
public final class CardRegistry {

    private CardRegistry() {
    }

    /** Поставщик готовых карт; реализуется модулем {@code cards}. */
    public interface CardPack {
        /** Все карты этого набора, ещё не связанные с данными. */
        List<Card> cards();
    }

    private static final Map<String, Card> BY_ID = new LinkedHashMap<>();
    private static final List<String> MISSING = new ArrayList<>();
    private static boolean loaded;

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        for (CardPack pack : ServiceLoader.load(CardPack.class)) {
            for (Card c : pack.cards()) {
                Card prev = BY_ID.put(c.id(), c);
                if (prev != null) {
                    throw new IllegalStateException("две карты с одним номером: "
                        + c.id() + " (" + prev.name() + " и " + c.name() + ")");
                }
            }
        }
    }

    /** Карта по идентификатору или {@code null}, если кода для неё ещё нет. */
    public static Card find(String id) {
        load();
        return BY_ID.get(id);
    }

    /** Карта задания по идентификатору или {@code null}. */
    public static ObjectiveCard objective(String id) {
        return find(id) instanceof ObjectiveCard o ? o : null;
    }

    /** Карта арсенала по идентификатору или {@code null}. */
    public static ArsenalCard arsenal(String id) {
        return find(id) instanceof ArsenalCard a ? a : null;
    }

    /** Все карты, для которых код есть. */
    public static List<Card> all() {
        load();
        return List.copyOf(BY_ID.values());
    }

    /**
     * Связать карты с их записями в загруженном наборе данных и запомнить, для
     * каких карт кода ещё нет.
     *
     * @param family  семейство набора: {@code objectives}, {@code arsenal}…
     * @param entries записи набора из YAML
     */
    public static synchronized void bindAll(String family,
                                            List<Map<String, Object>> entries) {
        load();
        for (Map<String, Object> entry : entries) {
            String id = String.valueOf(entry.get("id"));
            Card c = BY_ID.get(id);
            if (c == null) {
                String key = family + "/" + id + " ("
                    + entry.getOrDefault("name", "без имени") + ")";
                if (!MISSING.contains(key)) {
                    MISSING.add(key);
                }
            } else if (c.describesItself()) {
                // КАРТА НАКРЫВАЕТ ЗАПИСЬ СОБОЙ. Источник правды — класс, поэтому
                // всё, что лежало в файле, заменяется выгрузкой из кода: имя,
                // пороги, награда, печатный текст. Запись правится НА МЕСТЕ, а не
                // подменяется новой, потому что на неё уже смотрят указатель
                // набора и всё, что успело эту запись получить.
                //
                // ЗАПИСЬ СНАЧАЛА ПОКАЗЫВАЕТСЯ КАРТЕ (21.08.2026). Прежде bind() у
                // самоописанных карт не вызывался ВООБЩЕ, и то, что карта готова
                // была взять из набора, до неё не доходило: карты арсенала берут
                // оттуда свой УТИЛЬ, потому что утиль — это данные (эффект из
                // общего реестра плюс параметры), и в неизменяемом наборе только
                // так его и можно переиздать, не меняя старую версию. Порядок
                // важен: сперва показать, потом накрыть, иначе карта увидела бы
                // уже стёртую запись.
                c.bind(entry);
                entry.clear();
                entry.putAll(c.data());
            } else {
                c.bind(entry);
            }
        }
    }

    /**
     * Карты, которые есть в данных, но кода для них ещё нет. Это не ошибка и не
     * повод падать — это список работы, и он должен быть на виду.
     */
    public static List<String> missing() {
        return List.copyOf(MISSING);
    }

    /** Забыть загруженное — нужно тестам, которые подменяют наборы. */
    public static synchronized void reset() {
        BY_ID.clear();
        MISSING.clear();
        loaded = false;
    }
}
