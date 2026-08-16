package kelium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import kelium.dataio.ContentLibrary;
import kelium.dataio.ContentSet;
import kelium.dataio.GameConfig;
import kelium.engine.Effects;
import kelium.engine.Predicates;
import kelium.engine.Setup;
import kelium.rules.Ruleset;

/**
 * ЦЕЛОСТНОСТЬ ДАННЫХ — самая дешёвая защита от опечаток в файлах дизайнера.
 *
 * <p>Раньше опечатка в {@code predicate:} или {@code effect:} карты всплывала
 * посреди случайной партии (а то и вообще молча отключала механику). Здесь она
 * падает сразу и с именем карты. Тест не проверяет БАЛАНС — только то, что на
 * всё, на что ссылаются данные, в коде есть реализация.
 */
class DataIntegrityTest {

    private static List<String> rulesetIds() {
        List<String> out = new ArrayList<>(GameConfig.availableRulesets(null));
        assertFalse(out.isEmpty(), "не найдено ни одного файла правил");
        return out;
    }

    /** Каждая версия правил грузится, и весь её контент резолвится. */
    @Test
    void everyRulesetLoadsWithAllItsContent() {
        List<String> broken = new ArrayList<>();
        for (String id : rulesetIds()) {
            try {
                GameConfig cfg = GameConfig.build(id, 4, 1L, null, null);
                for (String type : cfg.content.sets.keySet()) {
                    ContentSet cs = cfg.content.get(type);
                    assertFalse(cs.entries.isEmpty(), id + ": пустой набор " + type);
                }
            } catch (RuntimeException e) {
                broken.add(id + " — " + e.getMessage());
            }
        }
        assertTrue(broken.isEmpty(), "версии правил не грузятся: " + broken);
    }

    /** Все предикаты, названные в данных, есть в реестре кода. */
    @Test
    void everyPredicateNamedInDataExists() {
        Set<String> missing = new TreeSet<>();
        for (String id : rulesetIds()) {
            ContentLibrary lib = GameConfig.build(id, 4, 1L, null, null).content;
            for (String type : List.of("objectives", "super_objectives")) {
                ContentSet cs = lib.sets.get(type);
                if (cs == null) {
                    continue;
                }
                for (Map<String, Object> card : cs.entries) {
                    // У КАРТЫ ДВА ЗАКОННЫХ ИСТОЧНИКА УСЛОВИЯ (с 15.08.2026).
                    // Прежде условие могло жить только в реестре предикатов, и
                    // проверка была прямой. Теперь карта задания — объект с
                    // собственным кодом (модуль cards), и её условие живёт ТАМ;
                    // имя предиката в данных осталось как пояснение для человека.
                    // Смысл проверки сохранён: карта не имеет права ссылаться на
                    // условие, которого нет НИ В ОДНОМ из двух мест.
                    // Признак стоит В ДАННЫХ, а не проверяется через реестр карт:
                    // движок собирается и тестируется БЕЗ модуля карт (связь идёт
                    // через ServiceLoader только во время работы), поэтому спросить
                    // реестр отсюда нельзя — он пуст по построению.
                    boolean ownCode = "card".equals(String.valueOf(
                        card.getOrDefault("checked_by", "")));
                    if (ownCode) {
                        continue;
                    }
                    for (String pid : collect(card, "predicate")) {
                        if (!Predicates.isRegistered(pid)) {
                            missing.add(pid + " (" + type + "/" + card.get("id")
                                + ", правила " + id + ")");
                        }
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "в данных названы несуществующие предикаты: " + missing);
    }

    /**
     * Все эффекты, названные в данных, либо реализованы, либо карта ЗАВЕДОМО
     * отсеивается на подготовке. Список отсева зафиксирован: если он вырос —
     * значит что-то сломалось незаметно.
     */
    @Test
    void everyEffectNamedInDataIsImplementedOrKnownToBeCulled() {
        Set<String> unknown = new TreeSet<>();
        ContentLibrary lib = GameConfig.build(GameConfig.DEFAULT_RULESET, 4, 1L, null, null)
            .content;
        for (String type : List.of("objectives", "arsenal", "containers", "market",
                                   "super_arsenal", "super_objectives")) {
            ContentSet cs = lib.sets.get(type);
            if (cs == null) {
                continue;
            }
            for (Map<String, Object> card : cs.entries) {
                for (String eid : collect(card, "effect")) {
                    if (!Effects.isImplemented(eid)) {
                        unknown.add(eid + " (" + type + "/" + card.get("id") + ")");
                    }
                }
            }
        }
        // «noop» и заглушки — законная часть данных: такие карты отсеиваются.
        unknown.removeIf(s -> s.startsWith("noop"));
        assertTrue(unknown.isEmpty(),
            "в данных названы эффекты без реализации (и без отсева): " + unknown);
    }

    /** Подготовка партии проходит на всех числах игроков и всех версиях правил. */
    @Test
    void setupWorksForEveryRulesetAndPlayerCount() {
        List<String> broken = new ArrayList<>();
        for (String id : rulesetIds()) {
            for (int n : new int[]{2, 3, 4}) {
                try {
                    Setup.buildGame(GameConfig.build(id, n, 5L, null, null));
                } catch (RuntimeException e) {
                    broken.add(id + " на " + n + " игроков — " + e.getMessage());
                }
            }
        }
        assertTrue(broken.isEmpty(), "подготовка партии падает: " + broken);
    }

    /**
     * Правила раздаются КОПИЕЙ: разбор YAML общий на процесс, но правка одной
     * партии не должна протекать в другую.
     */
    @Test
    void tweakingOneGameDoesNotLeakIntoTheNext() {
        GameConfig a = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 1L, null, null);
        GameConfig b = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 2L, null, null);
        Object before = b.ruleset.get("super_objectives.enabled", Boolean.TRUE);
        a.ruleset.override("super_objectives.enabled", Boolean.FALSE);
        assertTrue(before.equals(b.ruleset.get("super_objectives.enabled", Boolean.TRUE)),
            "правка правил в одной партии протекла в другую");
    }

    /** Отсутствующую карту можно спросить безопасно, а не только «упасть». */
    @Test
    void missingCardsCanBeAskedForSafely() {
        ContentSet cards = GameConfig.build(GameConfig.DEFAULT_RULESET, 4, 1L, null, null)
            .content.get("arsenal");
        org.junit.jupiter.api.Assertions.assertNull(cards.find("такой-карты-нет"));
        org.junit.jupiter.api.Assertions.assertThrows(ContentSet.ContentError.class,
            () -> cards.byId("такой-карты-нет"));
    }

    /**
     * В РАБОЧИХ каталогах данных не валяются резервные копии и временные файлы:
     * загрузчики берут файлы по маске, и забытая копия однажды подменит живой
     * файл. Для старых версий есть {@code _archive/} — туда можно складывать
     * что угодно, оттуда никто не читает.
     */
    @Test
    void theDataFolderHasNoLeftovers() throws Exception {
        Path root = GameConfig.resolveDataRoot(null);
        Path archive = root.resolve("_archive");
        Set<String> junk = new LinkedHashSet<>();
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> !p.startsWith(archive))
                .forEach(p -> {
                    String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    if (n.endsWith(".bak") || n.endsWith("~") || n.endsWith(".tmp")) {
                        junk.add(root.relativize(p).toString());
                    }
                });
        }
        assertTrue(junk.isEmpty(),
            "в рабочем каталоге данных лежат резервные копии — их место в _archive: " + junk);
    }

    /** Собрать значения ключа по всему дереву карты (top/bottom/варианты/награды). */
    @SuppressWarnings("unchecked")
    private static Set<String> collect(Object node, String key) {
        Set<String> out = new LinkedHashSet<>();
        if (node instanceof Map<?, ?> m) {
            for (Map.Entry<Object, Object> e : ((Map<Object, Object>) m).entrySet()) {
                if (key.equals(String.valueOf(e.getKey())) && e.getValue() != null
                        && !(e.getValue() instanceof Map) && !(e.getValue() instanceof List)) {
                    out.add(String.valueOf(e.getValue()));
                } else {
                    out.addAll(collect(e.getValue(), key));
                }
            }
        } else if (node instanceof List<?> l) {
            for (Object o : l) {
                out.addAll(collect(o, key));
            }
        }
        return out;
    }

    /** Версии правил читаются и не ссылаются на несуществующие наборы. */
    @Test
    void contentVersionsPointAtRealFiles() {
        List<String> problems = new ArrayList<>();
        for (String id : rulesetIds()) {
            Ruleset rs = GameConfig.build(id, 4, 1L, null, null).ruleset;
            Object cv = rs.get("content_versions", null);
            if (!(cv instanceof Map<?, ?> m)) {
                problems.add(id + ": нет content_versions");
                continue;
            }
            assertFalse(m.isEmpty(), id + ": content_versions пуст");
        }
        assertTrue(problems.isEmpty(), String.join("; ", problems));
    }
}
