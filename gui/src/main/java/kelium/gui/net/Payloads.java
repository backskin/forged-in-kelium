package kelium.gui.net;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ДАННЫЕ ВАРИАНТОВ ПО СЕТИ — С ТИПАМИ (29.09.2026). Окно партии у друга — то же
 * окно, что у хоста, и оно решает, где показать вариант, по его данным: здание
 * и уровень, ресурс ({@code Resource.AMMO}), трек и шаг ({@code Object[]}),
 * ряд обмена ({@code int[]}). JSON этих типов не знает, поэтому они
 * заворачиваются в метки:
 *
 * <ul>
 *   <li>перечисление — {@code {"@e": класс, "v": имя}} (только классы игры
 *       {@code kelium.*} — чужой класс по сети не создаётся);</li>
 *   <li>{@code Object[]} — {@code {"@a": [...]}}, {@code int[]} — {@code {"@i": [...]}};</li>
 *   <li>всё прочее, чего JSON не выразит, — {@code {"@s": строка}}: окно друга
 *       покажет такой вариант подписью в карточке вопроса.</li>
 * </ul>
 *
 * <p>Хост по-прежнему принимает от друга ТОЛЬКО номер варианта.
 */
public final class Payloads {

    private Payloads() {
    }

    /** Значение варианта или контекста — в JSON-совместимый вид. */
    public static Object enc(Object v) {
        if (v == null || v instanceof String || v instanceof Boolean || v instanceof Number) {
            return v;
        }
        if (v instanceof Enum<?> e) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("@e", e.getDeclaringClass().getName());
            m.put("v", e.name());
            return m;
        }
        if (v instanceof int[] ints) {
            List<Object> l = new ArrayList<>();
            for (int i : ints) {
                l.add(i);
            }
            return Map.of("@i", l);
        }
        if (v instanceof Object[] arr) {
            List<Object> l = new ArrayList<>();
            for (Object o : arr) {
                l.add(enc(o));
            }
            return Map.of("@a", l);
        }
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), enc(e.getValue()));
            }
            return out;
        }
        if (v instanceof Collection<?> c) {
            List<Object> l = new ArrayList<>();
            for (Object o : c) {
                l.add(enc(o));
            }
            return l;
        }
        return Map.of("@s", String.valueOf(v));
    }

    /** Обратно из JSON — те же типы, что были у хоста. */
    public static Object dec(Object v) {
        if (v instanceof Map<?, ?> m) {
            if (m.containsKey("@e") && m.get("@e") instanceof String cls
                    && m.get("v") instanceof String name) {
                return enumOf(cls, name);
            }
            if (m.size() == 1 && m.get("@i") instanceof List<?> l) {
                int[] out = new int[l.size()];
                for (int i = 0; i < out.length; i++) {
                    out[i] = l.get(i) instanceof Number n ? n.intValue() : 0;
                }
                return out;
            }
            if (m.size() == 1 && m.get("@a") instanceof List<?> l) {
                Object[] out = new Object[l.size()];
                for (int i = 0; i < out.length; i++) {
                    out[i] = dec(l.get(i));
                }
                return out;
            }
            if (m.size() == 1 && m.get("@s") instanceof String s) {
                return s;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), dec(e.getValue()));
            }
            return out;
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object o : l) {
                out.add(dec(o));
            }
            return out;
        }
        return v;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumOf(String cls, String name) {
        if (!cls.startsWith("kelium.")) {
            return name;
        }
        try {
            Class<?> c = Class.forName(cls);
            if (!c.isEnum()) {
                return name;
            }
            return Enum.valueOf((Class<? extends Enum>) c, name);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            return name;
        }
    }

    /** Контекст вопроса — в JSON-совместимый вид (ключи — строки). */
    public static Map<String, Object> encContext(Map<String, Object> ctx) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (ctx != null) {
            for (Map.Entry<String, Object> e : ctx.entrySet()) {
                out.put(e.getKey(), enc(e.getValue()));
            }
        }
        return out;
    }

    /** Контекст вопроса — обратно. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> decContext(Object raw) {
        Object d = dec(raw);
        return d instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
    }
}
