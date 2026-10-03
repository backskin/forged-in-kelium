package kelium.gui.cardshop;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * ОПИСАНИЕ КАРТЫ В МАСТЕРСКОЙ — тип и поля словами. Хранится файлом
 * {@code .kcard} (YAML), рядом с выпущенной картинкой.
 */
public final class CardSpec {

    /** Раскладка — как рисовать; тип — какой шаблон брать. */
    public enum Layout { OBJECTIVE, ARSENAL, MARKET, CONTAINER, HEX }

    /**
     * Типы карт мастерской. ШАБЛОНЫ СЛОЯМИ (выгрузка дизайнера 03.10.2026):
     * иллюстрация из «шаблоны карт/фоны», поверх — плашки из «шаблоны
     * карт/подложка». {@code bg} — имя фона с %d (номер рисунка) или без,
     * {@code from..to} — номера рисунков, {@code over} / {@code overAlt} —
     * подложка: у задания без/с «дополнительно», у арсенала ∞ / ▶.
     */
    public enum Type {
        OBJECTIVE("Задание", Layout.OBJECTIVE, "задания-шаблоны-%d.png", 4, 14,
            "задания-шаблон.png", "задания-шаблон-с-доп.png"),
        OBJECTIVE_START("Задание начальное", Layout.OBJECTIVE, "Задания начальные шаблоны.png", 1, 1,
            "Задания начальные шаблоны.png", "Задания начальные шаблоны.png"),
        ARSENAL("Арсенал", Layout.ARSENAL, "арсенал-шаблоны-%d.png", 4, 17,
            "арсенал-шаблоны-2.png", "арсенал-шаблоны-3.png"),
        ARSENAL_START("Арсенал начальный", Layout.ARSENAL, "арсенал-начальный.png", 1, 1,
            "арсенал-начальный-2.png", "арсенал-начальный-1.png"),
        ARSENAL_SUPER("Арсенал супер", Layout.ARSENAL, "арсенал-супер-шаблоны-%d.png", 3, 6,
            "арсенал-супер-шаблоны-1.png", "арсенал-супер-шаблоны-2.png"),
        MARKET("Рынок", Layout.MARKET, "рынок-шаблоны-%d.png", 2, 10,
            "рынок-шаблоны.png", "рынок-шаблоны.png"),
        CONTAINER("Контейнер", Layout.CONTAINER, null, 1, 1, null, null),
        SPAWN_HEX("Гекс зарождения", Layout.HEX, null, 1, 2, null, null);

        public final String ru;
        public final Layout layout;
        public final String bg;
        public final int from;
        public final int to;
        public final String over;
        public final String overAlt;

        Type(String ru, Layout layout, String bg, int from, int to, String over, String overAlt) {
            this.ru = ru;
            this.layout = layout;
            this.bg = bg;
            this.from = from;
            this.to = to;
            this.over = over;
            this.overAlt = overAlt;
        }

        /** Сколько рисунков у типа. */
        public int arts() {
            return to - from + 1;
        }

        /** Файл фона для рисунка art (1..arts()); null — у типа фон один с подложкой. */
        public String bgFile(int art) {
            if (bg == null) {
                return null;
            }
            int a = Math.max(1, Math.min(arts(), art));
            return "фоны/" + (bg.contains("%d") ? String.format(bg, from + a - 1) : bg);
        }

        /** Файл подложки: alt — у задания «с дополнительно», у арсенала ▶. */
        public String overFile(boolean alt) {
            String o = alt ? overAlt : over;
            return o == null ? null : "подложка/" + o;
        }

        /** Шаблон целиком — для типа без слоёв (контейнер). */
        public String wholeFile() {
            return this == CONTAINER ? "контейнер.png" : null;
        }

        /**
         * Жетон гекса зарождения: две стороны — зелёная рамка (шаблоны 1, 2) и
         * рыжая (3, 4); у каждой стороны два рисунка.
         */
        public String hexFile(boolean orange, int art) {
            int a = Math.max(1, Math.min(2, art));
            return "Жетон гекса зарождения-" + ((orange ? 2 : 0) + a) + ".png";
        }

        public static Type of(String ru) {
            for (Type t : values()) {
                if (t.ru.equalsIgnoreCase(ru) || t.name().equalsIgnoreCase(ru)) {
                    return t;
                }
            }
            return OBJECTIVE;
        }

        @Override
        public String toString() {
            return ru;
        }
    }

    public final Map<String, Object> fields = new LinkedHashMap<>();

    public CardSpec(Type t) {
        fields.put("тип", t.ru);
        fields.put("иконки", IconNumbers.VERSION);
    }

    public Type type() {
        return Type.of(text("тип"));
    }

    public String text(String k) {
        Object v = fields.get(k);
        return v == null ? "" : String.valueOf(v);
    }

    public List<String> lines(String k) {
        List<String> out = new ArrayList<>();
        for (String l : text(k).split("\n")) {
            if (!l.isBlank()) {
                out.add(l.trim());
            }
        }
        return out;
    }

    public int integer(String k, int def) {
        try {
            return Integer.parseInt(text(k).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean bool(String k) {
        Object v = fields.get(k);
        return v instanceof Boolean b ? b : "да".equalsIgnoreCase(text(k))
            || "true".equalsIgnoreCase(text(k));
    }

    // ==================== файл ====================

    public void save(File f) throws IOException {
        DumperOptions o = new DumperOptions();
        o.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        o.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        o.setAllowUnicode(true);
        o.setWidth(200);
        Files.writeString(f.toPath(), new Yaml(o).dump(fields), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    public static CardSpec load(File f) throws IOException {
        Object raw = new Yaml().load(Files.readString(f.toPath(), StandardCharsets.UTF_8));
        Map<String, Object> m = raw instanceof Map<?, ?> mm ? (Map<String, Object>) mm : Map.of();
        CardSpec c = new CardSpec(Type.of(String.valueOf(m.getOrDefault("тип", "Задание"))));
        c.fields.remove("иконки");
        for (var e : m.entrySet()) {
            c.fields.put(e.getKey(), e.getValue());
        }
        IconNumbers.migrate(c);     // карта до выгрузки 03.10.2026 — на новые номера
        return c;
    }

    /** Новая карта с заполненными для примера полями — сразу видно, что куда пишется. */
    public static CardSpec blank(Type t) {
        CardSpec c = new CardSpec(t);
        Map<String, Object> f = c.fields;
        if (t.layout == Layout.OBJECTIVE) {
            f.put("рисунок", 1);
            f.put("слот", "∞");
            f.put("верх_вид", "реакция");
            f.put("заголовок_верха", "Закрома");
            f.put("иконка_верха", "{57}");
            f.put("верх", "Получи 2 {3} боеприпаса, если\nкто-либо атакует твой жетон");
            f.put("имя", "Новое задание");
            f.put("условие", "Имей на поле 2 своих добытчика с полной энергией");
            Reward r = new Reward();
            r.kind = Reward.ACTION;
            r.choice = Reward.EITHER;
            r.items.add(new Reward.Item("36", 1));
            r.items.add(new Reward.Item("34", 1));
            f.put("награда", r.toMap());
            f.put("дополнительно", "на разных гексах.");
            Reward d = new Reward();
            d.kind = Reward.RESOURCES;
            d.choice = Reward.ALL;
            d.items.add(new Reward.Item("1", 2));
            d.items.add(new Reward.Item("26", 1));
            f.put("доп_награда", d.toMap());
            f.put("номер", 1);
        } else if (t.layout == Layout.ARSENAL) {
            f.put("спец", false);
            f.put("верх", "Замени карту приказа\nс руки на одну из сброса");
            f.put("верх_по_центру", false);
            f.put("верх_слева", "");
            f.put("верх_справа", "{44}");
            f.put("имя", "Новая карта");
            f.put("низ", "Если в этот ход у тебя **совпадение** приказов, получи ещё одно {26} "
                + "спец-действие.");
            f.put("ряд", "");
            f.put("звезда", false);
            f.put("цена", "1");
            f.put("цена_иконка", "{1}");
            f.put("спец_иконка", "{34}");
            f.put("спец_знак", "=1");
            f.put("спец_текст", "перемести\n1 кубик\nэнергии");
            f.put("контейнер", true);
            f.put("номер", 1);
        } else if (t.layout == Layout.MARKET) {
            f.put("рисунок", 1);
            f.put("слева", "Выполни\n«Манёвр»");
            f.put("иконка_слева", "{38}");
            f.put("справа", "Выполни\n«Питание»");
            f.put("иконка_справа", "{34}");
            f.put("номер", 1);
        } else if (t.layout == Layout.HEX) {
            f.put("сторона", "зелёная");
            f.put("рисунок", 2);
            f.put("старт", true);
            f.put("число", "3");
            f.put("ярлык", "0 {5} ?");
            f.put("ряд", "+ {9} → {52}");
        } else if (t.layout == Layout.CONTAINER) {
            f.put("буква", "А");
            f.put("число", "3");
            f.put("иконка", "{1}{1}");
            f.put("имя", "спрятанная\nналичность");
        }
        return c;
    }
}
