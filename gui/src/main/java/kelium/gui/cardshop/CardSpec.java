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
    public enum Layout { OBJECTIVE, ARSENAL, NONE }

    /** Типы карт мастерской и их пустые шаблоны в папке «шаблоны карт». */
    public enum Type {
        OBJECTIVE("Задание", Layout.OBJECTIVE, "задания-шаблоны-%d.png"),
        OBJECTIVE_START("Задание начальное", Layout.OBJECTIVE, "задания-начальные-шаблоны-%d.png"),
        OBJECTIVE_SUPER("Задание супер", Layout.OBJECTIVE, "задания-супер-шаблоны-%d.png"),
        ARSENAL("Арсенал", Layout.ARSENAL, "арсенал-%d.png"),
        ARSENAL_START("Арсенал начальный", Layout.ARSENAL, "арсенал-начальный-%d.png"),
        ARSENAL_SUPER("Арсенал супер", Layout.ARSENAL, "арсенал-супер-%d.png"),
        MARKET("Рынок", Layout.NONE, "карта-рынка-шаблон.png");

        public final String ru;
        public final Layout layout;
        public final String pattern;

        Type(String ru, Layout layout, String pattern) {
            this.ru = ru;
            this.layout = layout;
            this.pattern = pattern;
        }

        /**
         * Имя файла шаблона. У заданий рисунки идут парами: чётный номер — с
         * полосой «дополнительно», нечётный — без неё (рисунок 1 = файлы 2 и 3).
         * У арсенала: 1 — ∞, 2 — ▶.
         */
        public String templateFile(int art, boolean withDop) {
            if (layout == Layout.OBJECTIVE) {
                int n = art * 2 + (withDop ? 0 : 1);
                return String.format(pattern, n);
            }
            return pattern.contains("%d") ? String.format(pattern, art) : pattern;
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
        for (var e : m.entrySet()) {
            c.fields.put(e.getKey(), e.getValue());
        }
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
            f.put("иконка_верха", "{52}");
            f.put("верх", "Получи 2 {3} боеприпаса, если\nкто-либо атакует твой жетон");
            f.put("имя", "Новое задание");
            f.put("условие", "Имей на поле 2 своих добытчика с полной энергией");
            f.put("награда", "{34} {32}");
            f.put("дополнительно", "на разных гексах.");
            f.put("доп_награда", "{1}{1} {25}");
            f.put("номер", 1);
        } else if (t.layout == Layout.ARSENAL) {
            f.put("спец", false);
            f.put("верх", "Замени карту приказа\nс руки на одну из сброса");
            f.put("верх_по_центру", false);
            f.put("верх_слева", "");
            f.put("верх_справа", "{39}");
            f.put("имя", "Новая карта");
            f.put("низ", "Если в этот ход у тебя **совпадение** приказов, получи ещё одно {25} "
                + "спец-действие.");
            f.put("ряд", "");
            f.put("звезда", false);
            f.put("цена", "1");
            f.put("цена_иконка", "{1}");
            f.put("спец_иконка", "{32}");
            f.put("спец_знак", "=1");
            f.put("спец_текст", "перемести\n1 кубик\nэнергии");
            f.put("контейнер", true);
            f.put("номер", 1);
        }
        return c;
    }
}
