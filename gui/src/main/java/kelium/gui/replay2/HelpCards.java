package kelium.gui.replay2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kelium.dataio.ContentLibrary;
import kelium.dataio.ContentSet;

/**
 * HelpCards — КАТАЛОГ ВСЕХ КАРТ внутри справочника.
 *
 * <p>Дизайнер читает карту так, как читал бы настоящую бумажную: сперва
 * человеческое описание, потом мелким — техническая начинка. Поэтому каталог
 * НЕ РИСУЕТ свой разворот, а показывает тот же самый, что читалка карт в личной
 * зоне игрока ({@link CardReader}). Один разворот на всё приложение — и они не
 * могут разойтись.
 *
 * <p>Описание карты живёт <b>в том же файле карточного набора</b>, что и её
 * кодовые параметры, ключом {@code описание}. Никаких отдельных файлов с
 * текстами: один модуль — вся правда о карте. Текстов пока нет ни у одной карты;
 * сочинять их здесь нельзя — это игровой контент, его пишет дизайнер. Пока
 * ключа нет, разворот честно говорит, что описание не написано, а
 * {@link #missing(ContentLibrary)} собирает список «каких текстов не хватает».
 */
public final class HelpCards {

    private HelpCards() {
    }

    /** Раздел каталога со всеми подразделами. */
    public static HelpBook.Section catalog(ContentLibrary content, Path dataRoot) {
        HelpBook.Section root = new HelpBook.Section("cards", "Каталог всех карт",
            () -> intro(content, dataRoot));
        group(root, content, dataRoot, "orders", "Приказы", HelpCards::orderGroup);
        group(root, content, dataRoot, "objectives", "Задания", HelpCards::objectiveGroup);
        group(root, content, dataRoot, "arsenal", "Арсенал", HelpCards::kindGroup);
        group(root, content, dataRoot, "super_arsenal", "Супер-арсенал",
            HelpCards::superArsenalGroup);
        group(root, content, dataRoot, "super_objectives", "Супер-задания", c -> "");
        group(root, content, dataRoot, "containers", "Контейнеры", HelpCards::tierGroup);
        group(root, content, dataRoot, "market", "Рынок", c -> "");
        return root;
    }

    /** Что за карта — по чему её раскладывают на подразделы. */
    private interface Grouper {
        String of(Map<String, Object> card);
    }

    /**
     * Раздел одного вида карт: сперва МОДУЛИ (версии набора), внутри модуля — виды
     * карт, внутри вида — сами карты.
     *
     * <p>Модуль — это отдельный файл набора: {@code arsenal.1.2.0},
     * {@code arsenal.2.0.0}. Они сосуществуют нарочно, версия правил подключает
     * один из них, и «арсенал» без указания модуля — это разные колоды под одним
     * словом (замечание дизайнера 13.08.2026). Поэтому каталог показывает все
     * модули и помечает тот, по которому идёт открытая партия.
     */
    private static void group(HelpBook.Section root, ContentLibrary content, Path dataRoot,
                              String type, String title, Grouper grouper) {
        ContentSet active = set(content, type);
        String activeVersion = active == null ? null : active.version;
        List<String> versions = modules(dataRoot, type);
        List<String[]> moduleRows = new ArrayList<>();
        HelpBook.Section node = new HelpBook.Section("cards-" + type, title,
            () -> typeIntro(type, active, moduleRows));
        root.children.add(node);
        for (String version : versions) {
            ContentSet set = version.equals(activeVersion) ? active : load(type, version,
                dataRoot);
            if (set == null) {
                continue;
            }
            boolean inPlay = version.equals(activeVersion);
            moduleRows.add(new String[]{"набор " + HelpBook.esc(version),
                set.size() + " карт" + (inPlay ? " · сейчас в игре" : "")});
            HelpBook.Section module = new HelpBook.Section(
                "cards-" + type + "-" + version,
                "набор " + version + (inPlay ? " · сейчас в игре" : ""),
                () -> moduleIntro(type, set, inPlay));
            node.children.add(module);
            fill(module, set, type, grouper);
        }
        if (node.children.isEmpty() && active != null) {
            fill(node, active, type, grouper);
        }
    }

    /** Разложить карты набора по видам и подвесить их к разделу модуля. */
    private static void fill(HelpBook.Section module, ContentSet set, String type,
                             Grouper grouper) {
        Map<String, String> names = names(set, type);
        // Виды идут в том порядке, в каком карты лежат в наборе: он осмысленный —
        // так дизайнер их и писал.
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> card : set.entries) {
            String g = grouper.of(card);
            groups.computeIfAbsent(g == null ? "" : g, k -> new ArrayList<>()).add(card);
        }
        int groupNumber = 0;
        for (Map.Entry<String, List<Map<String, Object>>> e : groups.entrySet()) {
            HelpBook.Section into = module;
            if (!e.getKey().isEmpty()) {
                into = new HelpBook.Section(module.id + "-" + (++groupNumber), e.getKey(),
                    () -> "<p>Карт в этом виде: " + e.getValue().size()
                    + ". Выбери карту в дереве слева.</p>");
                module.children.add(into);
            }
            for (Map<String, Object> card : e.getValue()) {
                String id = String.valueOf(card.get("id"));
                into.children.add(new HelpBook.Section(
                    "card-" + type + "-" + set.version + "-" + id,
                    names.getOrDefault(id, "не описано"),
                    () -> CardReader.describe(card, id,
                        key -> names.getOrDefault(key, "не описано"))));
            }
        }
    }

    /**
     * Какие модули этого вида карт лежат в данных: имена файлов
     * {@code <вид>.<версия>.yaml}. От новых к старым — свежий модуль нужен чаще.
     */
    public static List<String> modules(Path dataRoot, String type) {
        List<String> out = new ArrayList<>();
        Path dir = dataRoot == null ? null : dataRoot.resolve("cards");
        if (dir == null || !Files.isDirectory(dir)) {
            return out;
        }
        try (var files = Files.list(dir)) {
            for (Path p : files.toList()) {
                String name = p.getFileName().toString();
                if (!name.startsWith(type + ".") || !name.endsWith(".yaml")) {
                    continue;
                }
                String version = name.substring(type.length() + 1,
                    name.length() - ".yaml".length());
                // КОПИИ, КОТОРЫЕ ДЕЛАЕТ ОБЛАЧНАЯ ПАПКА при одновременной правке
                // («arsenal.1.2.0 (копия с компьютера …).yaml»), — не модуль набора.
                // Номер версии это только цифры и точки; всё прочее пропускаем, иначе
                // в дереве появится модуль-призрак.
                if (!version.matches("[0-9][0-9.]*([-][A-Za-z0-9.]+)?")) {
                    continue;
                }
                out.add(version);
            }
        } catch (java.io.IOException e) {
            return out;
        }
        out.sort((a, b) -> RulesetDiff.compareVersions(b, a));
        return out;
    }

    private static ContentSet load(String type, String version, Path dataRoot) {
        try {
            return ContentSet.load(type, version, dataRoot);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ==================== по каким видам делим ====================

    private static String orderGroup(Map<String, Object> card) {
        Object deck = card.get("deck");
        return "колода " + Names.orderDeck(deck == null ? "" : String.valueOf(deck));
    }

    private static String objectiveGroup(Map<String, Object> card) {
        if ("starting".equals(String.valueOf(card.get("kind")))) {
            return "начальные";
        }
        return objectiveType(String.valueOf(card.get("type")));
    }

    /** Вид задания — как он размечен в наборе. */
    private static String objectiveType(String type) {
        return switch (type == null ? "" : type) {
            case "incident" -> "происшествия (проверяются в момент)";
            case "state" -> "состояния (проверяются по положению на поле)";
            case "sacrifice" -> "жертвы (платятся ресурсами)";
            default -> "не описано";
        };
    }

    /**
     * Начальная карта или обычная. В ранних модулях у обычных карт пометки нет
     * вовсе — «нет пометки» и значит «обычная», а не «не описано».
     */
    private static String kindGroup(Map<String, Object> card) {
        Object kind = card.get("kind");
        if (kind == null || "regular".equals(String.valueOf(kind))) {
            return "обычные";
        }
        return "starting".equals(String.valueOf(kind)) ? "начальные" : "не описано";
    }

    private static String superArsenalGroup(Map<String, Object> card) {
        return switch (String.valueOf(card.get("kind"))) {
            case "troop" -> "супер-войска";
            case "power" -> "способности";
            default -> "не описано";
        };
    }

    private static String tierGroup(Map<String, Object> card) {
        return switch (String.valueOf(card.get("tier"))) {
            case "common" -> "простые";
            case "good" -> "хорошие";
            case "rare" -> "редкие";
            default -> "не описано";
        };
    }

    // ==================== названия карт ====================

    /**
     * Названия карт набора. У большинства название написано в самом наборе; у
     * приказов его нет — карта опознаётся колодой и двумя приказами, поэтому имя
     * собирается из них словами, а не показывается кодом.
     */
    public static Map<String, String> names(ContentSet set, String type) {
        Map<String, String> out = new LinkedHashMap<>();
        int joker = 0;
        for (Map<String, Object> card : set.entries) {
            String id = String.valueOf(card.get("id"));
            Object name = card.get("name");
            if (name != null && !String.valueOf(name).isBlank()) {
                out.put(id, String.valueOf(name));
                continue;
            }
            if ("orders".equals(type)) {
                out.put(id, orderName(card, Boolean.TRUE.equals(card.get("joker"))
                    ? ++joker : 0));
                continue;
            }
            out.put(id, "не описано");
        }
        return out;
    }

    /** Имя карты приказов: колода и два приказа — верхний и нижний. */
    private static String orderName(Map<String, Object> card, int jokerNumber) {
        String deck = Names.orderDeck(String.valueOf(card.get("deck")));
        if (jokerNumber > 0) {
            return deck + " · джокер " + jokerNumber;
        }
        Object top = card.get("top");
        Object bottom = card.get("bottom");
        return deck + " · " + Names.orderPair(top == null ? null : String.valueOf(top),
            bottom == null ? null : String.valueOf(bottom));
    }

    // ==================== статьи разделов ====================

    private static String moduleIntro(String type, ContentSet set, boolean inPlay) {
        HelpBook.Html h = new HelpBook.Html();
        h.p("Модуль <b>" + HelpBook.esc(set.version) + "</b> вида «"
            + RuleWords.contentType(type) + "», карт в нём " + set.size() + ".");
        h.p(inPlay ? "Это тот набор, по которому идёт открытая партия: версия правил "
            + "подключает его." : "В открытой партии этот набор не участвует — версия "
            + "правил подключила другой. Он лежит рядом, чтобы наборы можно было "
            + "сравнивать, не ломая прежние замеры.");
        int missing = 0;
        for (Map<String, Object> card : set.entries) {
            Object text = card.get("описание");
            if (text == null || String.valueOf(text).isBlank()) {
                missing++;
            }
        }
        if (missing > 0) {
            h.note("Описаний в этом модуле не хватает: " + missing + " из " + set.size()
                + ".");
        }
        return h.done();
    }

    private static String intro(ContentLibrary content, Path dataRoot) {
        HelpBook.Html h = new HelpBook.Html();
        h.p("Здесь все карты игры — так, как они лежат в карточных наборах. Выбери карту "
            + "в дереве слева: справа откроется её разворот.");
        h.p("Внутри каждого вида карты разложены по <b>модулям</b> — версиям набора. "
            + "Модуль это отдельная колода: <i>арсенал 1.2.0</i> и <i>арсенал 2.0.0</i> — "
            + "разные наборы карт под одним словом «арсенал». Версия правил подключает "
            + "один модуль; он помечен «сейчас в игре», остальные лежат рядом, чтобы "
            + "наборы можно было сравнивать.");
        h.p("Разворот устроен как настоящая карта: сверху крупно <b>человеческое "
            + "описание</b> — что карта делает и зачем она нужна; ниже, мельче и "
            + "приглушённее, — <b>техническое</b>: эффекты, параметры, условия, цена, как "
            + "они записаны в наборе.");
        if (content == null) {
            h.note("Карточные наборы не загрузились — каталог пуст.");
            return h.done();
        }
        List<String[]> rows = new ArrayList<>();
        for (String type : List.of("orders", "objectives", "arsenal", "super_arsenal",
                "super_objectives", "containers", "market")) {
            ContentSet s = set(content, type);
            if (s != null) {
                int modules = modules(dataRoot, type).size();
                rows.add(new String[]{HelpBook.esc(RuleWords.contentType(type)),
                    "в игре набор " + HelpBook.esc(s.version) + ", карт в нём " + s.size()
                    + (modules > 1 ? " · всего модулей " + modules : "")});
            }
        }
        h.table2("Что в каталоге", rows);
        List<String[]> miss = missing(content);
        h.h("Карты без описания");
        if (miss.isEmpty()) {
            h.p("Описание написано у всех карт.");
        } else {
            h.p("Человеческое описание хранится в самом наборе карты, ключом "
                + "<b>описание</b>. Пока его нет, разворот честно об этом пишет. "
                + "Не хватает текстов: <b>" + miss.size() + "</b>.");
            List<String[]> byType = new ArrayList<>();
            Map<String, Integer> count = new LinkedHashMap<>();
            for (String[] m : miss) {
                count.merge(m[0], 1, Integer::sum);
            }
            for (Map.Entry<String, Integer> e : count.entrySet()) {
                byType.add(new String[]{HelpBook.esc(e.getKey()),
                    e.getValue() + " карт"});
            }
            h.table2("Сколько и где", byType);
            h.note("Полный список кладёт в отчёты генератор картинок справочника.");
        }
        return h.done();
    }

    /**
     * Статья вида карт: перечень его модулей. Строки перечня складывает
     * {@link #group}, пока грузит наборы, — второй раз читать файлы незачем.
     */
    private static String typeIntro(String type, ContentSet active, List<String[]> rows) {
        HelpBook.Html h = new HelpBook.Html();
        if (active == null && rows.isEmpty()) {
            h.note("Наборы вида «" + RuleWords.contentType(type) + "» не нашлись.");
            return h.done();
        }
        if (active != null) {
            h.p("В открытой партии играет модуль <b>" + HelpBook.esc(active.version)
                + "</b>, карт в нём " + active.size() + ".");
        }
        if (!rows.isEmpty()) {
            h.table2("Модули этого вида карт", rows);
        }
        h.p("Выбери модуль в дереве слева: внутри модуля карты разложены по видам.");
        return h.done();
    }

    // ==================== список карт без описания ====================

    /**
     * Какие карты ещё без человеческого описания: пары «вид набора → название».
     * Это список ДЛЯ ДИЗАЙНЕРА: что осталось написать.
     */
    public static List<String[]> missing(ContentLibrary content) {
        List<String[]> out = new ArrayList<>();
        if (content == null) {
            return out;
        }
        for (String type : List.of("orders", "objectives", "arsenal", "super_arsenal",
                "super_objectives", "containers", "market")) {
            ContentSet set = set(content, type);
            if (set == null) {
                continue;
            }
            Map<String, String> names = names(set, type);
            for (Map<String, Object> card : set.entries) {
                Object text = card.get("описание");
                if (text == null || String.valueOf(text).isBlank()) {
                    out.add(new String[]{RuleWords.contentType(type),
                        names.getOrDefault(String.valueOf(card.get("id")), "не описано")});
                }
            }
        }
        return out;
    }

    /**
     * То же, но по ВСЕМ модулям, какие лежат в данных: пары «вид · набор» →
     * название карты. Каталог показывает все модули, значит и список «чего не
     * хватает» должен быть про все, иначе дизайнер увидит в справочнике «описание
     * не написано» там, где список уверял, что всё готово.
     */
    public static List<String[]> missingByModule(Path dataRoot) {
        List<String[]> out = new ArrayList<>();
        for (String type : TYPES) {
            for (String version : modules(dataRoot, type)) {
                ContentSet set = load(type, version, dataRoot);
                if (set == null) {
                    continue;
                }
                Map<String, String> names = names(set, type);
                for (Map<String, Object> card : set.entries) {
                    Object text = card.get("описание");
                    if (text == null || String.valueOf(text).isBlank()) {
                        out.add(new String[]{RuleWords.contentType(type) + " · набор "
                            + version,
                            names.getOrDefault(String.valueOf(card.get("id")),
                                "не описано")});
                    }
                }
            }
        }
        return out;
    }

    /** Виды карт, которые показывает каталог. */
    private static final List<String> TYPES = List.of("orders", "objectives", "arsenal",
        "super_arsenal", "super_objectives", "containers", "market");

    private static ContentSet set(ContentLibrary content, String type) {
        if (content == null) {
            return null;
        }
        try {
            return content.get(type);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
