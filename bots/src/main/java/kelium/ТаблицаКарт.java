package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kelium.dataio.ContentLibrary;
import kelium.dataio.ContentSet;
import kelium.dataio.GameConfig;
import kelium.rules.Ruleset;

/**
 * ВСЕ КАРТЫ ИГРЫ В ОДНОЙ КНИГЕ EXCEL — по листу на тип карт.
 *
 * <p>Порт {@code tools/таблицы-карт.py} на Java. Питоновский сборщик остаётся
 * в дереве как есть, но запустить его на рабочей машине нечем: Python туда не
 * ставился, в PATH лежит заглушка. Эта версия ничего, кроме самой игры, не
 * требует.
 *
 * <p>ОТКУДА БЕРУТСЯ КАРТЫ. Не из YAML напрямую, а через {@link ContentLibrary}
 * ДЕЙСТВУЮЩЕГО СВОДА — то есть ровно так, как их читает движок и оба
 * справочника. Карты, переехавшие в код, приходят уже накрытыми своими
 * классами, поэтому таблица не может показать устаревший текст из каталога.
 *
 * <p>ЧТО НА ЛИСТАХ. Задания обычные и начальные, супер-задания, арсенал
 * обычный, начальный и супер, контейнеры, рынок, приказы. Утиль (верх карты)
 * вынесен отдельными листами — своим для заданий и своим для арсенала: утиль
 * правится сам по себе и сравнивать его надо между собой, а в строке карты он
 * теряется среди условий и наград. Первым листом идёт сводка по числу карт.
 *
 * <p>Запуск: {@code kelium.ТаблицаКарт ["docs/КАРТЫ — все таблицы.xlsx"]}
 */
public final class ТаблицаКарт {

    private ТаблицаКарт() {
    }

    private static final Map<String, String> ПРИРОДА = Map.of(
        "state", "состояние",
        "incident", "происшествие",
        "sacrifice", "жертва");

    private static final Map<String, String> РОДА = Map.of(
        "infantry", "пехота", "vehicle", "техника",
        "tower", "вышка", "aircraft", "авиация");

    private static final Map<String, String> РЕДКОСТЬ = Map.of(
        "common", "обычный", "good", "хороший", "rare", "редкий");

    private static final Map<String, String> КОЛОДЫ = Map.of(
        "blue", "Голубая", "scarlet", "Алая", "green", "Зелёная",
        "yellow", "Жёлтая", "security", "БЕЗОПАСНОСТЬ");

    private static final Map<String, String> ПРИКАЗЫ = Map.of(
        "development", "Разработка", "infrastructure", "Инфраструктура",
        "operation", "Наступление", "acquisitions", "Приобретения");

    private static final Map<String, String> ПЛАШКИ = Map.of(
        "movement", "ДВИЖЕНИЕ", "coin", "МОНЕТА", "objective", "ЗАДАНИЕ");

    private static final Map<String, String> НАГРАДЫ = new LinkedHashMap<>();

    static {
        НАГРАДЫ.put("coin", "монеты");
        НАГРАДЫ.put("ammo", "боеприпасы");
        НАГРАДЫ.put("trophy", "трофеи");
        НАГРАДЫ.put("kelium", "келемий");
        НАГРАДЫ.put("objective_card", "карты задания");
        НАГРАДЫ.put("arsenal", "карта арсенала");
        НАГРАДЫ.put("arsenal_from_display", "карта арсенала НА ВЫБОР из открытых");
        НАГРАДЫ.put("module", "жетон модуля");
        НАГРАДЫ.put("storage_token", "жетон хранилища");
        НАГРАДЫ.put("vp", "победные очки");
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        Path цель = Path.of(args.length > 0 ? args[0] : "docs/КАРТЫ — все таблицы.xlsx");

        String свод = GameConfig.DEFAULT_RULESET;
        Path корень = Path.of("data");
        Ruleset правила = Ruleset.loadById(свод, корень.resolve("rulesets"));
        ContentLibrary библиотека = ContentLibrary.forRuleset(правила, корень);

        List<Map<String, Object>> задания = записи(библиотека, "objectives");
        List<Map<String, Object>> арсенал = записи(библиотека, "arsenal");
        List<Map<String, Object>> суперзадания = записи(библиотека, "super_objectives");
        List<Map<String, Object>> суперарсенал = записи(библиотека, "super_arsenal");
        List<Map<String, Object>> контейнеры = записи(библиотека, "containers");
        List<Map<String, Object>> рынок = записи(библиотека, "market");
        List<Map<String, Object>> приказы = записи(библиотека, "orders");

        List<Map<String, Object>> обычныеЗ = отбор(задания, false);
        List<Map<String, Object>> начальныеЗ = отбор(задания, true);
        List<Map<String, Object>> обычныйА = отбор(арсенал, false);
        List<Map<String, Object>> начальныйА = отбор(арсенал, true);

        // Категории супер-заданий печатаются на карте словами: карта — это
        // ПАРА категорий, и без расшифровки строка «buildings_3» никому
        // ничего не говорит.
        Map<String, Object> категории = категории(библиотека);

        XlsxКнига книга = new XlsxКнига();

        XlsxКнига.Лист сводка = книга.лист("Сводка", new int[]{28, 16, 44},
            "Тип карт", "Карт в наборе", "Версия набора");
        сводка.строка("Задания", обычныеЗ.size(), версия(библиотека, "objectives"));
        сводка.строка("Начальные задания", начальныеЗ.size(), версия(библиотека, "objectives"));
        сводка.строка("Супер-задания", суперзадания.size(), версия(библиотека, "super_objectives"));
        сводка.строка("Арсенал", обычныйА.size(), версия(библиотека, "arsenal"));
        сводка.строка("Начальный арсенал", начальныйА.size(), версия(библиотека, "arsenal"));
        сводка.строка("Супер-арсенал", суперарсенал.size(), версия(библиотека, "super_arsenal"));
        сводка.строка("Контейнеры", контейнеры.size(), версия(библиотека, "containers"));
        сводка.строка("Рынок", рынок.size(), версия(библиотека, "market"));
        сводка.строка("Приказы", приказы.size(), версия(библиотека, "orders"));
        сводка.строка("Свод", "", свод);

        // ------------------------------ ЗАДАНИЯ ------------------------------
        XlsxКнига.Лист лЗ = книга.лист("Задания", new int[]{7, 22, 15, 46, 34, 24, 30, 30, 70},
            "№", "Название", "Природа", "Условие", "Усиление",
            "Награда", "Усиленная награда", "Утиль", "Описание");
        for (Map<String, Object> c : обычныеЗ) {
            лЗ.строка(c.get("id"), текст(c.get("name")),
                ПРИРОДА.getOrDefault(текст(c.get("type")), текст(c.get("type"))),
                условие(c, "requirement"), условие(c, "enhanced"),
                награда(c.get("base_reward")), награда(c.get("special_reward")),
                верх(c), текст(c.get("описание")));
        }

        XlsxКнига.Лист лНЗ = книга.лист("Начальные задания", new int[]{7, 22, 15, 46, 24, 30, 70},
            "№", "Название", "Природа", "Условие", "Награда", "Утиль", "Описание");
        for (Map<String, Object> c : начальныеЗ) {
            лНЗ.строка(c.get("id"), текст(c.get("name")),
                ПРИРОДА.getOrDefault(текст(c.get("type")), текст(c.get("type"))),
                условие(c, "requirement"), награда(c.get("base_reward")),
                верх(c), текст(c.get("описание")));
        }

        // СУПЕР-ЗАДАНИЯ 8.0: у карты нет ни низа, ни требования — только ПАРА
        // категорий счёта. Поэтому колонки другие, чем были у редакции 7.0.
        XlsxКнига.Лист лСЗ = книга.лист("Супер-задания", new int[]{9, 24, 20, 20, 44, 44, 50},
            "№", "Название", "Категория 1", "Категория 2",
            "Что считает 1", "Что считает 2", "Описание");
        for (Map<String, Object> c : суперзадания) {
            List<?> пара = c.get("categories") instanceof List<?> l ? l : List.of();
            String к1 = пара.size() > 0 ? String.valueOf(пара.get(0)) : "";
            String к2 = пара.size() > 1 ? String.valueOf(пара.get(1)) : "";
            лСЗ.строка(c.get("id"), текст(c.get("name")), к1, к2,
                текст(категории.get(к1)), текст(категории.get(к2)),
                текст(c.get("описание")));
        }

        // ------------------------------ АРСЕНАЛ ------------------------------
        String[] шапкаА = {"№", "Название", "Вид низа", "Основной эффект (низ)",
            "Утиль (верх)", "Описание"};
        int[] ширинаА = {7, 24, 13, 60, 44, 70};

        XlsxКнига.Лист лА = книга.лист("Арсенал", ширинаА, шапкаА);
        for (Map<String, Object> c : обычныйА) {
            лА.строка(c.get("id"), текст(c.get("name")), видНиза(c), низ(c), верх(c),
                текст(c.get("описание")));
        }

        XlsxКнига.Лист лНА = книга.лист("Начальный арсенал", ширинаА, шапкаА);
        for (Map<String, Object> c : начальныйА) {
            лНА.строка(c.get("id"), текст(c.get("name")), видНиза(c), низ(c), верх(c),
                текст(c.get("описание")));
        }

        // ПО СЧИТАЮТСЯ В ДВА СЛАГАЕМЫХ: свод платит за саму удерживаемую карту
        // (economy.vp_per_installed_super_arsenal), и сверх того идёт то, что
        // напечатано на самой карте. Одной колонкой это читалось бы как «на
        // карте напечатан ноль», что неправда.
        int поЗаКарту = правила.getInt("economy.vp_per_installed_super_arsenal");
        XlsxКнига.Лист лСА = книга.лист("Супер-арсенал",
            new int[]{8, 24, 16, 13, 11, 13, 10, 56, 60, 60},
            "№", "Название", "Вид", "Род войск", "Прочность",
            "ПО напечатано", "ПО всего", "Что делает", "Печатный текст", "Описание");
        for (Map<String, Object> c : суперарсенал) {
            int по = число(c.get("vp_on_card")) + число(c.get("vp_flat"));
            лСА.строка(c.get("id"), текст(c.get("name")),
                "troop".equals(текст(c.get("kind"))) ? "супер-войско" : "способность",
                РОДА.getOrDefault(текст(c.get("unit")), текст(c.get("unit"))),
                число(c.get("hp_bonus")) > 0 ? "+" + число(c.get("hp_bonus")) : "",
                по, поЗаКарту + по,
                текст(c.get("label")), текст(c.get("текст_карты")),
                текст(c.get("описание")));
        }

        // ---------------------------- КОНТЕЙНЕРЫ -----------------------------
        // Одна запись на ЛИЦО карты, копий — в поле copies: колода собирает
        // копии сама (набор 6.0.0).
        XlsxКнига.Лист лК = книга.лист("Контейнеры", new int[]{7, 30, 9, 9, 40, 70},
            "№", "Название (печатное требование)", "Лицо", "Копий", "Что даёт", "Описание");
        for (Map<String, Object> c : контейнеры) {
            Object эффект = c.get("a") instanceof Map<?, ?> m ? m.get("label") : null;
            лК.строка(c.get("id"), текст(c.get("name")), текст(c.get("face")),
                c.containsKey("copies") ? число(c.get("copies")) : 1,
                эффект != null ? текст(эффект) : РЕДКОСТЬ.getOrDefault(
                    текст(c.get("tier")), текст(c.get("tier"))),
                текст(c.get("описание")));
        }

        // ------------------------------- РЫНОК -------------------------------
        XlsxКнига.Лист лР = книга.лист("Рынок", new int[]{10, 22, 20, 40, 20, 40, 70},
            "№", "Название", "Левое предложение", "Что даёт слева",
            "Правое предложение", "Что даёт справа", "Описание");
        for (Map<String, Object> c : рынок) {
            лР.строка(c.get("id"), текст(c.get("name")),
                вложенное(c, "left", "name"), вложенное(c, "left", "label"),
                вложенное(c, "right", "name"), вложенное(c, "right", "label"),
                текст(c.get("описание")));
        }

        // ------------------------------ ПРИКАЗЫ ------------------------------
        XlsxКнига.Лист лП = книга.лист("Приказы", new int[]{16, 16, 18, 18, 16, 70},
            "№", "Колода", "Верхний приказ", "Нижний приказ", "Спец-плашка", "Описание");
        for (Map<String, Object> c : приказы) {
            лП.строка(c.get("id"), КОЛОДЫ.getOrDefault(текст(c.get("deck")), текст(c.get("deck"))),
                ПРИКАЗЫ.getOrDefault(текст(c.get("top")), "—"),
                ПРИКАЗЫ.getOrDefault(текст(c.get("bottom")), "—"),
                ПЛАШКИ.getOrDefault(текст(c.get("spec")), "—"),
                текст(c.get("описание")));
        }

        // ------------------------------- УТИЛЬ -------------------------------
        XlsxКнига.Лист лУЗ = книга.лист("Утиль заданий", new int[]{7, 24, 13, 46, 22, 34},
            "№", "Карта", "Вид карты", "Утиль-эффект", "Код эффекта", "Параметры");
        for (Map<String, Object> c : задания) {
            лУЗ.строка(c.get("id"), текст(c.get("name")),
                "starting".equals(текст(c.get("kind"))) ? "начальная" : "обычная",
                верх(c), вложенное(c, "top", "effect"), параметры(c));
        }

        XlsxКнига.Лист лУА = книга.лист("Утиль арсенала", new int[]{7, 24, 13, 46, 22, 34},
            "№", "Карта", "Вид карты", "Утиль-эффект", "Код эффекта", "Параметры");
        for (Map<String, Object> c : арсенал) {
            лУА.строка(c.get("id"), текст(c.get("name")),
                "starting".equals(текст(c.get("kind"))) ? "начальная" : "обычная",
                верх(c), вложенное(c, "top", "effect"), параметры(c));
        }

        книга.записать(цель);
        System.out.println("Книга собрана: " + цель.toAbsolutePath());
        System.out.println("Свод " + свод + ", листов 12"
            + ", заданий " + обычныеЗ.size() + "+" + начальныеЗ.size()
            + ", супер-заданий " + суперзадания.size()
            + ", арсенала " + обычныйА.size() + "+" + начальныйА.size()
            + ", супер-арсенала " + суперарсенал.size());
    }

    // ======================================================================
    //  Чтение набора
    // ======================================================================

    private static List<Map<String, Object>> записи(ContentLibrary библиотека, String тип) {
        ContentSet cs = библиотека.sets.get(тип);
        return cs == null ? List.of() : cs.entries;
    }

    private static String версия(ContentLibrary библиотека, String тип) {
        ContentSet cs = библиотека.sets.get(тип);
        return cs == null ? "—" : cs.version;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> категории(ContentLibrary библиотека) {
        ContentSet cs = библиотека.sets.get("super_objectives");
        if (cs == null || !(cs.raw.get("categories") instanceof Map<?, ?> m)) {
            return Map.of();
        }
        return (Map<String, Object>) m;
    }

    private static List<Map<String, Object>> отбор(List<Map<String, Object>> все,
                                                   boolean начальные) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> c : все) {
            if ("starting".equals(текст(c.get("kind"))) == начальные) {
                out.add(c);
            }
        }
        return out;
    }

    // ======================================================================
    //  Ячейки
    // ======================================================================

    private static String текст(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static int число(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static String вложенное(Map<String, Object> карта, String узел, String ключ) {
        return карта.get(узел) instanceof Map<?, ?> m ? текст(m.get(ключ)) : "";
    }

    private static String параметры(Map<String, Object> карта) {
        return карта.get("top") instanceof Map<?, ?> m && m.get("params") != null
            ? текст(m.get("params")) : "";
    }

    private static String верх(Map<String, Object> карта) {
        return вложенное(карта, "top", "label");
    }

    private static String низ(Map<String, Object> карта) {
        return вложенное(карта, "bottom", "label");
    }

    private static String видНиза(Map<String, Object> карта) {
        return "SPEC".equals(вложенное(карта, "bottom", "kind")) ? "СПЕЦ" : "постоянный";
    }

    private static String условие(Map<String, Object> карта, String ключ) {
        if (!(карта.get(ключ) instanceof Map<?, ?> m)) {
            return "";
        }
        Object у = m.get("условие");
        return у != null ? текст(у) : текст(m);
    }

    /** Награда словами: {coin: 3} → «3 монеты». */
    private static String награда(Object узел) {
        if (!(узел instanceof Map<?, ?> m) || m.isEmpty()) {
            return "";
        }
        List<String> части = new ArrayList<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String ключ = текст(e.getKey());
            Object значение = e.getValue();
            String имя = НАГРАДЫ.getOrDefault(ключ, ключ);
            if ("module".equals(ключ)) {
                части.add("жетон модуля " + ("attack".equals(текст(значение)) ? "атаки" : "сборки"));
            } else if (значение instanceof Number n && n.intValue() == 1) {
                части.add(имя);
            } else {
                части.add(значение + " " + имя);
            }
        }
        return String.join(", ", части);
    }
}
