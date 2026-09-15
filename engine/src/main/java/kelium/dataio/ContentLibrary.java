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
            // Набор картонных блоков — не колода, а ПЕЧАТЬ НА КАРТОНЕ: где на
            // каждом гексе стоит контейнер и где жёлтая ячейка энергии. Читает
            // kelium.engine.BlockStamp, ключ здесь только фиксирует версию.
            if ("blocks".equals(ctype)) {
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
        // "containers" ИСКЛЮЧЕНЫ 18.08.2026 (заказ дизайнера): контейнеры —
        // чистые данные (containers.*.yaml) без выбора стороны, только
        // напечатанная награда; поведения у карты нет, и класса ей не нужно.
        // Прежняя колода в коде (c01-c32 с названиями и двумя сторонами)
        // отменена дизайнером и снесена 13.09.2026 вместе с ContainerPack.
        for (String type : java.util.List.of("objectives", "arsenal",
                "market", "super_objectives", "super_arsenal")) {
            ContentSet cs = sets.get(type);
            if (cs != null) {
                kelium.engine.cards.CardRegistry.bindAll(type, cs.version,
                    безКлассов(cs.entries));
            }
        }
    }

    /**
     * ОТБРОСИТЬ КАРТЫ, У КОТОРЫХ КЛАССА НЕТ ПО УСТРОЙСТВУ.
     *
     * <p>СУПЕР-ЗАДАНИЯ 8.0 (поле {@code categories}) — это пара категорий
     * счёта и ничего больше: карту нельзя ни выполнить, ни сжечь, поэтому и
     * класса у неё нет. Прежние редакции («множитель верха» {@code multiplier},
     * «накопитель» {@code stockpile}) тоже играл сам движок. Просить реестр
     * связать такие карты значило бы записать их в «карты без кода» — и сторож,
     * который ищет НАСТОЯЩИЕ пропуски кода, начинал бы врать на каждой партии.
     */
    private static java.util.List<Map<String, Object>> безКлассов(
            java.util.List<Map<String, Object>> entries) {
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Map<String, Object> e : entries) {
            if (!e.containsKey("categories") && !e.containsKey("multiplier")
                    && !e.containsKey("stockpile")) {
                out.add(e);
            }
        }
        return out;
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
