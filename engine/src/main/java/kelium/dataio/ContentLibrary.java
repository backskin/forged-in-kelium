package kelium.dataio;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import kelium.rules.Ruleset;

/**
 * Все наборы контента, запрошенные правилами: загружены и адресуемы по типу.
 */
public final class ContentLibrary {

    public final Map<String, ContentSet> sets;

    public ContentLibrary(Map<String, ContentSet> sets) {
        this.sets = sets;
    }

    /** Получить набор контента по его типу; ошибка, если он не загружен. */
    public ContentSet get(String contentType) {
        ContentSet cs = sets.get(contentType);
        if (cs == null) {
            throw new ContentSet.ContentError("тип контента " + contentType + " не загружен");
        }
        return cs;
    }

    /** Загрузить все наборы контента, перечисленные в ruleset.content_versions. */
    @SuppressWarnings("unchecked")
    public static ContentLibrary forRuleset(Ruleset ruleset, Path dataRoot) {
        Map<String, Object> versions = (Map<String, Object>) ruleset.raw.get("content_versions");
        Map<String, ContentSet> sets = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : versions.entrySet()) {
            String ctype = e.getKey();
            // Сценарии — не карточный контент: загружаются отдельно
            // (Scenario.loadScenario), ключ здесь только фиксирует версию.
            if ("scenarios".equals(ctype)) {
                continue;
            }
            // Символы супер заданий — не колода карт, а РАЗМЕТКА: отображение
            // «форма → список карт». Общая проверка «список записей с id» к ней
            // не применима, поэтому файл читает kelium.engine.Symbols.
            if ("symbols".equals(ctype)) {
                continue;
            }
            // Наборы жетонов модулей — тоже не колода: это отображение
            // «наборы + мешки». Читает kelium.engine.ModuleSets.
            if ("modules".equals(ctype)) {
                continue;
            }
            String version = e.getValue().toString();
            sets.put(ctype, ContentSet.load(ctype, version, dataRoot));
        }
        bindCardsInCode(sets);
        return new ContentLibrary(sets);
    }

    /**
     * ЕДИНСТВЕННОЕ МЕСТО, ГДЕ КАТАЛОГ СВЯЗЫВАЕТСЯ С КОДОМ КАРТ (заказ дизайнера
     * 18.08.2026: «и внешний справочник, и справочник внутри реплэя должны брать
     * описания сразу с классов»).
     *
     * <p>НАЙДЕНО ПРИ ЭТОЙ ПРАВКЕ: {@code kelium.engine.cards.CardRegistry.bindAll}
     * вызывался только из {@code Setup.buildGame} — то есть только когда партия
     * реально поднимается. Внешний справочник ({@code HelpApp}) и внутренний
     * справочник без открытой партии строят {@link ContentLibrary} НАПРЯМУЮ, этот
     * путь минуя, — и потому читали каталог как ДО переезда карт в код: сырой
     * YAML, а не то, что выгружает класс. Для мигрировавших карт это пока не
     * расходилось (текст скопирован в код дословно), но было бы ровно тем же
     * классом ошибки, что чинился всю сессию, — стоило один раз поправить
     * класс-карту и забыть про YAML, и оба справочника показали бы устаревший
     * текст молча.
     *
     * <p>Теперь bindAll вызывается ЗДЕСЬ, в единственной точке, откуда контент
     * загружается для чего угодно — партии, внешнего и внутреннего справочника.
     * Прежние явные вызовы в {@code Setup.buildGame} стали избыточны и убраны.
     */
    private static void bindCardsInCode(Map<String, ContentSet> sets) {
        // "containers" ИСКЛЮЧЕНЫ 18.08.2026 (заказ дизайнера): контейнеры
        // возвращены к чистым данным (containers.4.0.0.yaml и новее) — без
        // выбора стороны, только один печатаемый ресурс/пара. Код-класс
        // остаётся в дереве (ContainerPack и т.д.), но больше не подключён
        // сюда и не перетирает YAML; если понадобится вернуть — просто
        // дописать "containers" обратно в этот список.
        for (String type : java.util.List.of("objectives", "arsenal",
                "market", "super_objectives", "super_arsenal")) {
            ContentSet cs = sets.get(type);
            if (cs != null) {
                kelium.engine.cards.CardRegistry.bindAll(type, cs.entries);
            }
        }
    }

    /** Краткая сводка по каждому типу контента: версия и число записей. */
    public Map<String, String> summary() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, ContentSet> e : sets.entrySet()) {
            out.put(e.getKey(), e.getValue().version + " (" + e.getValue().size() + " записей)");
        }
        return out;
    }
}
