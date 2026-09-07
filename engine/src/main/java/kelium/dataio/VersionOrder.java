package kelium.dataio;

import java.util.Comparator;
import java.util.List;

/**
 * ПОРЯДОК ВЕРСИЙ И ИМЁН — один на всю программу.
 *
 * <p>Строковое сравнение ставит «1.31.0» раньше «1.7.0», потому что сравнивает
 * символы: «3» меньше «7». В выпадающих списках сводов, колод и полей это
 * выглядело как перемешанный набор, и найти в нём нужное было нельзя (жалоба
 * дизайнера 02.09.2026). Здесь цифровые куски сравниваются ЧИСЛАМИ, а буквенные
 * — как текст, поэтому «1.9.0» идёт раньше «1.10.0», а «Поле 2» — раньше
 * «Поле 10».
 *
 * <p>Ответвление идёт ПОСЛЕ чистой версии: «1.8.0», потом «1.8.0-fast». Это
 * выходит само собой — у чистой версии просто кончаются куски, а короткое
 * считается меньшим.
 *
 * <p>Раньше такое сравнение было написано трижды (ContentSet, RulesetDiff,
 * StartMenuWindow) и с разными правилами хвоста, а часть списков не сортировалась
 * вовсе. Теперь правило одно и лежит здесь.
 */
public final class VersionOrder {

    /** По возрастанию: 1.9.0, 1.10.0, 1.10.0-fast. */
    public static final Comparator<String> ASC = VersionOrder::compare;
    /** По убыванию — свежее сверху. */
    public static final Comparator<String> DESC = ASC.reversed();

    private VersionOrder() {
    }

    /** Сравнение двух версий или имён по «человеческому» порядку. */
    public static int compare(String a, String b) {
        String x = a == null ? "" : a;
        String y = b == null ? "" : b;
        int i = 0;
        int j = 0;
        while (i < x.length() && j < y.length()) {
            char cx = x.charAt(i);
            char cy = y.charAt(j);
            if (Character.isDigit(cx) && Character.isDigit(cy)) {
                int i2 = i;
                int j2 = j;
                while (i2 < x.length() && Character.isDigit(x.charAt(i2))) {
                    i2++;
                }
                while (j2 < y.length() && Character.isDigit(y.charAt(j2))) {
                    j2++;
                }
                // Сравниваем без ведущих нулей и без разбора в int: номер версии
                // теоретически может не влезть в int, а обрывать его нельзя.
                String nx = x.substring(i, i2).replaceFirst("^0+(?=.)", "");
                String ny = y.substring(j, j2).replaceFirst("^0+(?=.)", "");
                int c = nx.length() != ny.length()
                    ? Integer.compare(nx.length(), ny.length())
                    : nx.compareTo(ny);
                if (c != 0) {
                    return c;
                }
                i = i2;
                j = j2;
                continue;
            }
            if (cx != cy) {
                // Регистр не должен растаскивать соседние имена: «Поле» и «поле»
                // стоят рядом, а порядок между ними решается уже точным сравнением.
                int c = Character.compare(Character.toLowerCase(cx), Character.toLowerCase(cy));
                return c != 0 ? c : Character.compare(cx, cy);
            }
            i++;
            j++;
        }
        return Integer.compare(x.length() - i, y.length() - j);
    }

    /** Копия списка по возрастанию версии. */
    public static List<String> sorted(List<String> ids) {
        List<String> out = new java.util.ArrayList<>(ids);
        out.sort(ASC);
        return out;
    }

    /** Копия списка по убыванию версии — свежее сверху. */
    public static List<String> sortedDesc(List<String> ids) {
        List<String> out = new java.util.ArrayList<>(ids);
        out.sort(DESC);
        return out;
    }
}
