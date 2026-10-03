package kelium.gui.cardshop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * НОМЕРА ИКОНОК ВЫГРУЗКИ 03.10.2026. Дизайнер сдвинул нумерацию «все
 * иконки-N»; карты, сохранённые раньше ({@code .kcard} без поля «иконки»),
 * при открытии переводятся на новые номера — один раз, по таблице ниже.
 * Каталог номеров — {@code design-docs/Иконки — номера экспорта (03.10.2026).md}.
 */
public final class IconNumbers {

    /** Отметка в файле карты: номера уже по этой выгрузке. */
    public static final String VERSION = "03.10.2026";

    private IconNumbers() {
    }

    /** Номер выгрузки 27.09.2026 → номер выгрузки 03.10.2026. */
    public static int renum(int old) {
        if (old <= 13) {
            return old;
        }
        if (old <= 28) {
            return old + 1;
        }
        if (old >= 46 && old <= 61) {
            return old + 5;
        }
        return switch (old) {
            case 29 -> 30; case 30 -> 31; case 31 -> 33; case 32 -> 34; case 33 -> 35;
            case 34 -> 36; case 35 -> 38; case 36 -> 39; case 37 -> 41; case 38 -> 42;
            case 39 -> 44; case 40 -> 45; case 41 -> 46; case 42 -> 47; case 43 -> 48;
            case 44 -> 49; case 45 -> 50; case 62 -> 68; case 63 -> 69; case 64 -> 70;
            case 65 -> 71; case 66 -> 72; case 67 -> 73; case 68 -> 74; case 69 -> 75;
            case 70 -> 76; case 71 -> 77; case 72 -> 78; case 73 -> 79; case 74 -> 80;
            case 75 -> 81; case 76 -> 82; case 77 -> 83; case 78 -> 84; case 79 -> 85;
            case 80 -> 32;
            default -> old;
        };
    }

    /** Прежние иконки-файлы с именами — теперь у них номера. */
    static String named(String key) {
        return switch (key) {
            case "действие — снабжение" -> "35";
            case "действие — командование" -> "37";
            case "действие — развитие" -> "40";
            case "военное здание" -> "30";
            default -> key;
        };
    }

    /** Ключ иконки старой карты → ключ новой. */
    public static String key(String old) {
        String k = old.trim();
        if (k.matches("\\d+")) {
            return String.valueOf(renum(Integer.parseInt(k)));
        }
        return named(k);
    }

    private static final Pattern BRACES = Pattern.compile("\\{([^}]+)\\}");

    /** Все {иконки} в тексте — на новые номера. */
    public static String text(String s) {
        Matcher m = BRACES.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement("{" + key(m.group(1)) + "}"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Перевести карту, сохранённую до выгрузки 03.10.2026; уже переведённую не трогать. */
    @SuppressWarnings("unchecked")
    public static void migrate(CardSpec c) {
        if (VERSION.equals(c.text("иконки"))) {
            return;
        }
        for (Map.Entry<String, Object> e : new ArrayList<>(c.fields.entrySet())) {
            Object v = e.getValue();
            if (v instanceof String s && s.contains("{")) {
                e.setValue(text(s));
            } else if (v instanceof Map<?, ?> m && m.get("позиции") instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Map<?, ?> im && im.get("иконка") != null) {
                        ((Map<String, Object>) im).put("иконка",
                            key(String.valueOf(im.get("иконка")).replaceAll("[{}]", "")));
                    }
                }
            }
        }
        c.fields.put("иконки", VERSION);
    }
}
