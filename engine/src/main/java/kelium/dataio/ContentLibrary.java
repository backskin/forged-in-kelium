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
        return new ContentLibrary(sets);
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
