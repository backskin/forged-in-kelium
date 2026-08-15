package kelium.gui.replay2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RulesetDiff — ЧЕМ ВЕРСИИ ПРАВИЛ ОТЛИЧАЮТСЯ, ПО ДАННЫМ.
 *
 * <p>Дизайнеру нужен список отличий соседних версий правил. Держать такой список
 * руками нельзя: он отстанет от файлов на первой же правке. Поэтому отличия
 * СЧИТАЮТСЯ: два файла правил обходятся деревом, и наружу выдаются добавленные,
 * убранные и изменённые правила. Слова к путям даёт {@link RuleWords}, значения
 * берутся из самих файлов.
 *
 * <p>Раздел «О версии» из сравнения исключён: он меняется в каждой версии по
 * определению, и в статье показывается отдельно — как заголовок.
 */
public final class RulesetDiff {

    private RulesetDiff() {
    }

    /** Что случилось с правилом между версиями. */
    public enum Kind { ADDED, REMOVED, CHANGED }

    /** Одно отличие: путь правила и его значения до и после. */
    public record Row(Kind kind, String path, Object before, Object after) {
    }

    /**
     * Сравнить два разобранных файла правил. Путь — точечный, как в
     * {@link kelium.rules.Ruleset#get(String)}.
     */
    public static List<Row> compare(Map<String, Object> before, Map<String, Object> after) {
        List<Row> out = new ArrayList<>();
        walk(before, after, "", out);
        out.sort(Comparator.comparing(Row::path));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void walk(Map<String, Object> a, Map<String, Object> b, String prefix,
                             List<Row> out) {
        Set<String> keys = new LinkedHashSet<>();
        if (a != null) {
            keys.addAll(keysOf(a));
        }
        if (b != null) {
            keys.addAll(keysOf(b));
        }
        for (String k : keys) {
            String path = prefix.isEmpty() ? k : prefix + "." + k;
            if (path.equals("meta") || path.startsWith("meta.")) {
                continue;
            }
            Object va = a == null ? null : a.get(k);
            Object vb = b == null ? null : b.get(k);
            boolean hasA = a != null && a.containsKey(k);
            boolean hasB = b != null && b.containsKey(k);
            if (va instanceof Map<?, ?> && vb instanceof Map<?, ?>) {
                walk((Map<String, Object>) va, (Map<String, Object>) vb, path, out);
                continue;
            }
            if (!hasA && hasB) {
                out.add(new Row(Kind.ADDED, path, null, vb));
            } else if (hasA && !hasB) {
                out.add(new Row(Kind.REMOVED, path, va, null));
            } else if (!same(va, vb)) {
                out.add(new Row(Kind.CHANGED, path, va, vb));
            }
        }
    }

    private static List<String> keysOf(Map<String, Object> m) {
        List<String> out = new ArrayList<>();
        for (Object k : m.keySet()) {
            out.add(String.valueOf(k));
        }
        return out;
    }

    /**
     * Равны ли значения. Числа сравниваются по написанию: в YAML одно и то же
     * число приходит то целым, то дробным, и «2» против «2.0» — не отличие
     * правил, а особенность разбора файла.
     */
    private static boolean same(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a instanceof Number na && b instanceof Number nb) {
            return na.doubleValue() == nb.doubleValue();
        }
        return String.valueOf(a).equals(String.valueOf(b));
    }

    /**
     * Версии правил по возрастанию: сначала числа (1.6.0 раньше 1.7.0), при
     * равных числах — пометка ветки (1.6.0 раньше 1.6.0-c1).
     */
    public static List<String> sorted(List<String> ids) {
        List<String> out = new ArrayList<>(ids);
        out.sort(RulesetDiff::compareVersions);
        return out;
    }

    /** Сравнение версий: по числам, потом по пометке ветки. */
    public static int compareVersions(String x, String y) {
        String[] xs = x.split("-", 2);
        String[] ys = y.split("-", 2);
        String[] xn = xs[0].split("\\.");
        String[] yn = ys[0].split("\\.");
        for (int i = 0; i < Math.max(xn.length, yn.length); i++) {
            int a = i < xn.length ? number(xn[i]) : 0;
            int b = i < yn.length ? number(yn[i]) : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        String xt = xs.length > 1 ? xs[1] : "";
        String yt = ys.length > 1 ? ys[1] : "";
        return xt.compareTo(yt);
    }

    private static int number(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
