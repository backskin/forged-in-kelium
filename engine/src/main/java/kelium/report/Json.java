package kelium.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Json — крошечный читатель и писатель JSON для записей партий.
 *
 * <p>Своя реализация вместо библиотеки по двум причинам: в проекте намеренно нет
 * зависимостей, кроме SnakeYAML и ONNX Runtime (см. заказ §2), а SnakeYAML читает
 * JSON по правилам YAML 1.1, где строки вроде {@code on}/{@code no} внезапно
 * становятся булевыми — для идентификаторов карт это ловушка.
 *
 * <p>Поддерживается полный синтаксис JSON, кроме экзотики: числа читаются как
 * {@link Integer}/{@link Long}, если целые, иначе как {@link Double}.
 */
public final class Json {

    private Json() {
    }

    /** Ошибка разбора JSON с указанием места. */
    public static final class JsonError extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public JsonError(String message) {
            super(message);
        }
    }

    // ==================== запись ====================
    /** Сериализовать значение в компактный JSON. */
    public static String write(Object v) {
        StringBuilder sb = new StringBuilder();
        writeTo(sb, v);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeTo(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            quote(sb, s);
        } else if (v instanceof Boolean b) {
            sb.append(b.booleanValue());
        } else if (v instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) {
                sb.append("null");
            } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                sb.append((long) (double) d);
            } else {
                sb.append(d.toString());
            }
        } else if (v instanceof Number n) {
            sb.append(n);
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<Object, Object> e : ((Map<Object, Object>) m).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                quote(sb, String.valueOf(e.getKey()));
                sb.append(':');
                writeTo(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeTo(sb, o);
            }
            sb.append(']');
        } else if (v instanceof int[] arr) {
            sb.append('[');
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(arr[i]);
            }
            sb.append(']');
        } else if (v instanceof Object[] arr) {
            sb.append('[');
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                writeTo(sb, arr[i]);
            }
            sb.append(']');
        } else {
            quote(sb, v.toString());
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ==================== чтение ====================
    /** Разобрать JSON-текст. Объекты становятся {@link LinkedHashMap}, массивы — {@link List}. */
    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.pos < p.src.length()) {
            throw new JsonError("лишние символы после значения на позиции " + p.pos);
        }
        return v;
    }

    private static final class Parser {
        /** Предел вложенности: без него битый файл валит разбор в StackOverflowError. */
        private static final int MAX_DEPTH = 200;

        private final String src;
        private int pos;
        private int depth;

        Parser(String src) {
            this.src = src;
        }

        void ws() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        Object value() {
            if (pos >= src.length()) {
                throw new JsonError("неожиданный конец JSON");
            }
            if (++depth > MAX_DEPTH) {
                throw new JsonError("слишком глубокая вложенность JSON — файл повреждён");
            }
            try {
                return valueInner();
            } finally {
                depth--;
            }
        }

        private Object valueInner() {
            char c = src.charAt(pos);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        Object literal(String word, Object val) {
            if (!src.startsWith(word, pos)) {
                throw new JsonError("ожидалось " + word + " на позиции " + pos);
            }
            pos += word.length();
            return val;
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            pos++;               // {
            ws();
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                return m;
            }
            while (true) {
                ws();
                String key = string();
                ws();
                expect(':');
                ws();
                m.put(key, value());
                ws();
                char c = next();
                if (c == '}') {
                    return m;
                }
                if (c != ',') {
                    throw new JsonError("ожидалась ',' или '}' на позиции " + (pos - 1));
                }
            }
        }

        List<Object> array() {
            List<Object> out = new ArrayList<>();
            pos++;               // [
            ws();
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                return out;
            }
            while (true) {
                ws();
                out.add(value());
                ws();
                char c = next();
                if (c == ']') {
                    return out;
                }
                if (c != ',') {
                    throw new JsonError("ожидалась ',' или ']' на позиции " + (pos - 1));
                }
            }
        }

        String string() {
            try {
                return stringInner();
            } catch (JsonError e) {
                throw e;
            } catch (RuntimeException e) {
                throw new JsonError("оборванная строка в JSON около позиции " + pos);
            }
        }

        private String stringInner() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= src.length()) {
                    throw new JsonError("незакрытая строка в JSON");
                }
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = src.charAt(pos++);
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw new JsonError("непонятная экранировка \\" + e);
                }
            }
        }

        Object number() {
            int start = pos;
            if (pos < src.length() && (src.charAt(pos) == '-' || src.charAt(pos) == '+')) {
                pos++;
            }
            boolean real = false;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (Character.isDigit(c)) {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    real = real || c == '.' || c == 'e' || c == 'E';
                    pos++;
                } else {
                    break;
                }
            }
            String s = src.substring(start, pos);
            if (s.isEmpty()) {
                throw new JsonError("ожидалось число на позиции " + start);
            }
            try {
                if (real) {
                    return Double.valueOf(s);
                }
                return small(Long.parseLong(s));
            } catch (NumberFormatException e) {
                throw new JsonError("непонятное число «" + s + "» на позиции " + start);
            }
        }

        private static Object small(long v) {
            return v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE
                ? (Object) Integer.valueOf((int) v) : (Object) Long.valueOf(v);
        }

        void expect(char c) {
            if (pos >= src.length() || src.charAt(pos) != c) {
                throw new JsonError("ожидался '" + c + "' на позиции " + pos);
            }
            pos++;
        }

        char next() {
            if (pos >= src.length()) {
                throw new JsonError("неожиданный конец JSON");
            }
            return src.charAt(pos++);
        }
    }

    // ==================== удобные извлекатели ====================
    /** Целое по ключу (0, если нет или не число). */
    public static int i(Map<String, Object> m, String key) {
        Object o = m == null ? null : m.get(key);
        return o instanceof Number n ? n.intValue() : 0;
    }

    /** Целое по ключу или null. */
    public static Integer io(Map<String, Object> m, String key) {
        Object o = m == null ? null : m.get(key);
        return o instanceof Number n ? n.intValue() : null;
    }

    /** Строка по ключу (null, если нет). */
    public static String s(Map<String, Object> m, String key) {
        Object o = m == null ? null : m.get(key);
        return o == null ? null : String.valueOf(o);
    }

    /** Булево по ключу. */
    public static boolean b(Map<String, Object> m, String key) {
        return Boolean.TRUE.equals(m == null ? null : m.get(key));
    }

    /** Список по ключу (пустой, если нет). */
    @SuppressWarnings("unchecked")
    public static List<Object> list(Map<String, Object> m, String key) {
        Object o = m == null ? null : m.get(key);
        return o instanceof List<?> l ? (List<Object>) l : List.of();
    }

    /** Вложенный объект по ключу (null, если нет). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<String, Object> m, String key) {
        Object o = m == null ? null : m.get(key);
        return o instanceof Map<?, ?> mm ? (Map<String, Object>) mm : null;
    }

    /** Список строк по ключу. */
    public static List<String> strings(Map<String, Object> m, String key) {
        List<String> out = new ArrayList<>();
        for (Object o : list(m, key)) {
            out.add(String.valueOf(o));
        }
        return out;
    }

    /** Карта строка→целое по ключу. */
    public static Map<String, Integer> ints(Map<String, Object> m, String key) {
        Map<String, Integer> out = new LinkedHashMap<>();
        Map<String, Object> src = map(m, key);
        if (src != null) {
            for (Map.Entry<String, Object> e : src.entrySet()) {
                if (e.getValue() instanceof Number n) {
                    out.put(e.getKey(), n.intValue());
                }
            }
        }
        return out;
    }

    /** Карта строка→строка по ключу. */
    public static Map<String, String> strMap(Map<String, Object> m, String key) {
        Map<String, String> out = new LinkedHashMap<>();
        Map<String, Object> src = map(m, key);
        if (src != null) {
            for (Map.Entry<String, Object> e : src.entrySet()) {
                if (e.getValue() != null) {
                    out.put(e.getKey(), String.valueOf(e.getValue()));
                }
            }
        }
        return out;
    }
}
