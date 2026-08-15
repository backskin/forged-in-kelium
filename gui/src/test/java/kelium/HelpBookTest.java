package kelium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import kelium.dataio.ContentLibrary;
import kelium.dataio.ContentSet;
import kelium.dataio.GameConfig;
import kelium.gui.replay2.HelpBook;
import kelium.gui.replay2.HelpCards;
import kelium.gui.replay2.HelpShots;
import kelium.gui.replay2.RuleWords;
import kelium.gui.replay2.RulesetDiff;
import kelium.report.ReplayRecord;
import kelium.rules.Ruleset;

/**
 * СПРАВОЧНИК ВНУТРИ ПРИЛОЖЕНИЯ (заказ дизайнера 13.08.2026).
 *
 * <p>Проверяется то, из-за чего справочник может начать врать или падать:
 * каждый раздел открывается (в том числе БЕЗ данных), у каждого правила есть
 * человеческая подпись, отличия версий считаются по файлам, каталог карт зовёт
 * ту же читалку и честно сообщает о ненаписанных описаниях, генератор картинок
 * отрабатывает на пустой записи.
 */
class HelpBookTest {

    /** Виды карт, которые показывает каталог справочника. */
    private static final List<String> TYPES = List.of("orders", "objectives", "arsenal",
        "super_arsenal", "super_objectives", "containers", "market");

    // ==================== разделы ====================

    @Test
    void everySectionOpens() {
        HelpBook book = HelpBook.of(null);
        List<HelpBook.Section> all = flatten(book.sections());
        assertTrue(all.size() > 100, "разделов должно быть много: " + all.size());
        for (HelpBook.Section s : all) {
            assertNotNull(s.html(), "раздел «" + s.title + "» не собрался");
            assertFalse(s.title.isBlank(), "раздел без названия");
            assertFalse(s.html().contains("Эту статью сейчас не собрать"),
                "раздел «" + s.title + "» не открылся с полными данными");
        }
    }

    @Test
    void sectionsOpenWithoutData() {
        String saved = System.getProperty("kelium.data", "");
        try {
            System.setProperty("kelium.data", "нет-такого-каталога");
            HelpBook book = HelpBook.of(null);
            List<HelpBook.Section> all = flatten(book.sections());
            assertFalse(all.isEmpty(), "без данных дерево разделов всё равно строится");
            for (HelpBook.Section s : all) {
                assertNotNull(s.html(), "раздел «" + s.title + "» уронил справочник");
            }
        } finally {
            System.setProperty("kelium.data", saved);
        }
    }

    @Test
    void noInternalKeysInArticles() {
        HelpBook book = HelpBook.of(null);
        for (HelpBook.Section s : flatten(book.sections())) {
            String html = s.html();
            // Два исключения, оба по договорённости заказа: разворот карты
            // показывает техническую часть «как записано в наборе», а статья про
            // версию правил приводит ДОСЛОВНУЮ записку её автора. Остальные статьи
            // внутренних ключей содержать не вправе.
            if (s.id.startsWith("card-") || s.id.startsWith("rules-")) {
                continue;
            }
            for (String key : List.of("kelium_to_ammo", "per_kelium_coin", "heal_per_refresh",
                    "circles_per_round", "step_vp_cumulative", "objective_hand_limit")) {
                assertFalse(html.contains(key),
                    "в статье «" + s.title + "» виден внутренний ключ " + key);
            }
        }
    }

    // ==================== подписи к правилам ====================

    @Test
    void everyRuleHasWords() {
        List<String> missing = new ArrayList<>();
        for (String path : allRulePaths()) {
            if (path.startsWith("meta.")) {
                continue;
            }
            if ("не описано".equals(RuleWords.rule(path))) {
                missing.add(path);
            }
        }
        assertTrue(missing.isEmpty(), "правила без человеческой подписи: " + missing);
    }

    @Test
    void groupsHaveWords() {
        Set<String> groups = new LinkedHashSet<>();
        for (String path : allRulePaths()) {
            groups.add(path.split("\\.")[0]);
        }
        for (String g : groups) {
            assertFalse("не описано".equals(RuleWords.group(g)),
                "раздел правил без подписи: " + g);
        }
    }

    // ==================== отличия версий ====================

    /**
     * Сравнение версий считается по файлам. С 15.08.2026 в живом каталоге лежит
     * ОДНА версия (остальные уведены в {@code data/_archive/rulesets}), поэтому
     * попарная часть проверяется только когда версий действительно несколько —
     * а инвариант «версия совпадает сама с собой» проверяется всегда.
     */
    @Test
    void diffIsComputedFromFiles() {
        Path root = GameConfig.resolveDataRoot(null);
        List<String> ids = RulesetDiff.sorted(GameConfig.availableRulesets(root));
        assertFalse(ids.isEmpty(), "хотя бы одна версия правил обязана лежать в каталоге");
        Ruleset b = Ruleset.loadById(ids.get(ids.size() - 1), root.resolve("rulesets"));

        if (ids.size() >= 2) {
            Ruleset a = Ruleset.loadById(ids.get(ids.size() - 2), root.resolve("rulesets"));
            for (RulesetDiff.Row r : RulesetDiff.compare(a.raw, b.raw)) {
                assertFalse(r.path().startsWith("meta."), "раздел «О версии» в сравнение не идёт");
            }
        }
        // Одна и та же версия сама с собой не отличается ничем.
        assertTrue(RulesetDiff.compare(b.raw, b.raw).isEmpty(),
            "версия обязана совпадать сама с собой");
    }

    @Test
    void versionsSortByNumbers() {
        List<String> sorted = RulesetDiff.sorted(List.of("1.10.0", "1.2.0", "1.6.0-c1",
            "1.6.0", "1.7.0"));
        assertTrue(sorted.indexOf("1.2.0") < sorted.indexOf("1.6.0"), "1.2.0 раньше 1.6.0");
        assertTrue(sorted.indexOf("1.6.0") < sorted.indexOf("1.6.0-c1"),
            "версия раньше своей ветки");
        assertTrue(sorted.indexOf("1.7.0") < sorted.indexOf("1.10.0"),
            "1.7.0 раньше 1.10.0 — числами, а не по алфавиту");
    }

    // ==================== каталог карт ====================

    @Test
    void catalogShowsEveryCardOfEveryModuleOnce() {
        Path root = GameConfig.resolveDataRoot(null);
        ContentLibrary lib = library();
        HelpBook.Section catalog = HelpCards.catalog(lib, root);
        Set<String> ids = new LinkedHashSet<>();
        for (HelpBook.Section s : flatten(List.of(catalog))) {
            if (s.id.startsWith("card-")) {
                assertTrue(ids.add(s.id), "карта в каталоге дважды: " + s.id);
                assertFalse(s.title.isBlank(), "карта без названия: " + s.id);
                assertTrue(s.html().length() > 40, "пустой разворот карты: " + s.title);
            }
        }
        // В каталоге лежат ВСЕ МОДУЛИ каждого вида карт, а не только тот, что
        // подключён версией правил.
        int inModules = 0;
        for (String type : TYPES) {
            List<String> modules = HelpCards.modules(root, type);
            assertFalse(modules.isEmpty(), "не нашлось модулей вида " + type);
            for (String version : modules) {
                inModules += ContentSet.load(type, version, root).size();
            }
        }
        assertTrue(ids.size() == inModules,
            "в каталоге " + ids.size() + " карт, а в модулях " + inModules);
    }

    @Test
    void activeModuleIsMarked() {
        Path root = GameConfig.resolveDataRoot(null);
        ContentLibrary lib = library();
        HelpBook.Section catalog = HelpCards.catalog(lib, root);
        for (String type : TYPES) {
            String want = "cards-" + type + "-" + lib.get(type).version;
            boolean found = false;
            for (HelpBook.Section s : flatten(List.of(catalog))) {
                if (s.id.equals(want)) {
                    found = true;
                    assertTrue(s.title.contains("сейчас в игре"),
                        "модуль партии не помечен: " + s.title);
                    assertFalse(s.title.contains("не описано"),
                        "вместо версии набора показано «не описано»: " + s.title);
                }
            }
            assertTrue(found, "в каталоге нет модуля, по которому идёт партия: " + want);
        }
    }

    @Test
    void noSectionSaysNotDescribedInsteadOfVersion() {
        HelpBook book = HelpBook.of(null);
        for (HelpBook.Section s : flatten(book.sections())) {
            if (s.id.startsWith("cards-")) {
                assertFalse(s.title.equals("не описано"),
                    "подраздел каталога назван «не описано»: " + s.id);
            }
        }
    }

    @Test
    void cardsWithoutTextAreListedHonestly() {
        ContentLibrary lib = library();
        List<String[]> missing = HelpCards.missing(lib);
        for (String[] m : missing) {
            assertFalse(m[1].isBlank(), "в списке карта без названия");
        }
        // Карта без ключа «описание» обязана сказать это словами, а не молчать.
        HelpBook.Section catalog = HelpCards.catalog(lib,
            GameConfig.resolveDataRoot(null));
        for (HelpBook.Section s : flatten(List.of(catalog))) {
            if (s.id.startsWith("card-") && !missing.isEmpty()) {
                String html = s.html();
                assertTrue(html.contains("описание") || html.contains("Описание"),
                    "разворот карты «" + s.title + "» молчит про описание");
                break;
            }
        }
    }

    @Test
    void orderCardsAreNamedWithWords() {
        ContentLibrary lib = library();
        ContentSet orders = lib.get("orders");
        Map<String, String> names = HelpCards.names(orders, "orders");
        for (Map.Entry<String, String> e : names.entrySet()) {
            assertFalse(e.getValue().contains(e.getKey()),
                "приказ назван своим внутренним кодом: " + e.getValue());
            assertFalse("не описано".equals(e.getValue()),
                "приказ без человеческого имени: " + e.getKey());
        }
    }

    // ==================== генератор картинок ====================

    @Test
    void shotsSurviveEmptyRecord() throws IOException {
        Path dir = Files.createTempDirectory("kelium-help-shots");
        List<String> made = HelpShots.generate(new ReplayRecord(), dir);
        assertNotNull(made, "генератор обязан вернуть список, а не упасть");
        for (String name : made) {
            assertTrue(Files.isRegularFile(dir.resolve(name)), "нет файла " + name);
        }
    }

    @Test
    void missingListIsWritten() throws IOException {
        Path dir = Files.createTempDirectory("kelium-help-reports");
        Path file = HelpShots.writeMissingList(new ReplayRecord(), dir);
        assertNotNull(file, "список карт без описания не записался");
        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(text.contains("описание"), "в списке нет речи об описаниях");
    }

    // ==================== подручное ====================

    private static ContentLibrary library() {
        Path root = GameConfig.resolveDataRoot(null);
        Ruleset rs = Ruleset.loadById(GameConfig.DEFAULT_RULESET, root.resolve("rulesets"));
        return ContentLibrary.forRuleset(rs, root);
    }

    private static List<HelpBook.Section> flatten(List<HelpBook.Section> roots) {
        List<HelpBook.Section> out = new ArrayList<>();
        for (HelpBook.Section s : roots) {
            out.addAll(s.flatten());
        }
        return out;
    }

    /** Все точечные пути всех наборов правил — по самим файлам. */
    @SuppressWarnings("unchecked")
    private static Set<String> allRulePaths() {
        Set<String> paths = new LinkedHashSet<>();
        Path dir = GameConfig.resolveDataRoot(null).resolve("rulesets");
        try (var files = Files.list(dir)) {
            for (Path p : files.toList()) {
                if (!p.getFileName().toString().endsWith(".yaml")) {
                    continue;
                }
                try (InputStream in = Files.newInputStream(p)) {
                    Object data = new Yaml().load(
                        new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    collect((Map<String, Object>) data, "", paths);
                }
            }
        } catch (IOException e) {
            throw new AssertionError("не прочитались файлы правил: " + e.getMessage());
        }
        assertFalse(paths.isEmpty(), "в наборах правил не нашлось ни одного правила");
        return paths;
    }

    @SuppressWarnings("unchecked")
    private static void collect(Map<String, Object> node, String prefix, Set<String> out) {
        for (Map.Entry<String, Object> e : node.entrySet()) {
            String path = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            if (e.getValue() instanceof Map<?, ?> m) {
                collect((Map<String, Object>) m, path, out);
            } else {
                out.add(path);
            }
        }
    }
}
