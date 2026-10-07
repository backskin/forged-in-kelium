package kelium.engine;

import java.util.List;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.Ctx;

/**
 * ЖЕТОНЫ МОДУЛЯ ХРАНИЛИЩА — ДВА ВИДА, У КАЖДОГО ОБЫЧНАЯ И УЛУЧШЕННАЯ СТОРОНА
 * (решение Влада 02.10.2026). Жетон игрок не тянет из мешка, а выбирает, какой
 * из двух видов взять:
 *
 * <ul>
 *   <li>ЯЧЕЙКА: а) +1 ячейка склада; б) +1 ячейка, звезда (1 ПО) и ещё одно
 *       спец-действие в каждом своём ходу — постоянная пассивка;</li>
 *   <li>ЭНЕРГИЯ: а) +1 кубик энергии ЦУ; б) +2 кубика энергии ЦУ и звезда.</li>
 * </ul>
 *
 * <p>Улучшенная сторона — та же позолота, что у модулей боя и сборки
 * ({@link Modules#gildOne}): жетон переворачивается навсегда, его звезда
 * считается в {@link PlayerState#goldModules}. Позолота жетонов хранилища
 * включается сводом ({@code storage.gild_tokens}); в прежних сводах золотых
 * жетонов хранилища не бывает, и всё считается по-старому.
 *
 * <p>Запись в {@link PlayerState#storageTokens} — прежние строки, чтобы старые
 * записи партий читались как есть; золотая сторона — та же строка с
 * {@link #ЗОЛОТО}.
 */
public final class ЖетоныХранилища {

    /** Вид «ячейка», обычная сторона. */
    public static final String ЯЧЕЙКА = "+1_universal_cell";
    /** Вид «энергия ЦУ», обычная сторона. */
    public static final String ЭНЕРГИЯ = "+1_energy";
    /** Приставка улучшенной (золотой) стороны. */
    public static final String ЗОЛОТО = ":gold";

    private ЖетоныХранилища() {
    }

    public static boolean ячейка(String жетон) {
        return жетон != null && жетон.startsWith(ЯЧЕЙКА);
    }

    public static boolean энергия(String жетон) {
        return жетон != null && жетон.startsWith(ЭНЕРГИЯ);
    }

    public static boolean золотой(String жетон) {
        return жетон != null && жетон.endsWith(ЗОЛОТО);
    }

    /** Сторона жетона после позолоты; уже золотой остаётся как есть. */
    public static String позолоченный(String жетон) {
        return золотой(жетон) ? жетон : жетон + ЗОЛОТО;
    }

    /** Сколько кубиков энергии ЦУ даёт жетон: 1 обычной стороной, 2 золотой, 0 — не энергия. */
    public static int кубиковЦу(String жетон) {
        if (!энергия(жетон)) {
            return 0;
        }
        return золотой(жетон) ? 2 : 1;
    }

    /** Ячеек склада от жетонов (обе стороны ячейки дают по одной). */
    public static int ячеек(PlayerState p) {
        int n = 0;
        for (String т : p.storageTokens) {
            if (ячейка(т)) {
                n++;
            }
        }
        return n;
    }

    /** Кубиков энергии ЦУ от жетонов игрока. */
    public static int энергииЦу(PlayerState p) {
        int n = 0;
        for (String т : p.storageTokens) {
            n += кубиковЦу(т);
        }
        return n;
    }

    /** Сколько жетонов лежит золотой стороной — по звезде каждый. */
    public static int золотых(PlayerState p) {
        int n = 0;
        for (String т : p.storageTokens) {
            if (золотой(т)) {
                n++;
            }
        }
        return n;
    }

    /** Лишних спец-действий за ход: по одному от каждой золотой ячейки. */
    public static int спецДействий(PlayerState p) {
        int n = 0;
        for (String т : p.storageTokens) {
            if (ячейка(т) && золотой(т)) {
                n++;
            }
        }
        return n;
    }

    /** Включена ли сводом позолота жетонов хранилища. */
    public static boolean золотитьМожно(GameState s) {
        return s != null && Ctx.rules(s).getBool("storage.gild_tokens", false);
    }

    /**
     * ЗВЕЗДА ОБЫЧНОЙ ЯЧЕЙКИ «ЕСЛИ ПУСТА» (правило 14.09.2026). С жетонами 2.0
     * (свод {@code storage.cell_star_when_empty: false}) обычная сторона ячейки
     * звезды не несёт — звезда есть только у золотой.
     */
    public static boolean звездаПустойЯчейки(GameState s) {
        return s == null || Ctx.rules(s).getBool("storage.cell_star_when_empty", true);
    }

    /** Номера жетонов, которые ещё можно позолотить. */
    public static List<Integer> кЗолочению(GameState s, PlayerState p) {
        if (!золотитьМожно(s)) {
            return List.of();
        }
        List<Integer> out = new java.util.ArrayList<>();
        for (int i = 0; i < p.storageTokens.size(); i++) {
            if (!золотой(p.storageTokens.get(i))) {
                out.add(i);
            }
        }
        return out;
    }

    /**
     * ПОЗОЛОТИТЬ ЖЕТОН {@code номер}. Энергия ЦУ растёт на кубик сразу: он
     * появляется на ЦУ, стоящем на поле, свободным — как у только что
     * поставленного источника.
     */
    public static void позолотить(GameState s, PlayerState p, int номер) {
        String было = p.storageTokens.get(номер);
        String стало = позолоченный(было);
        p.storageTokens.set(номер, стало);
        добавитьЭнергиюЦу(s, p, кубиковЦу(стало) - кубиковЦу(было));
    }

    /**
     * ПОЛОЖИТЬ НОВЫЙ ЖЕТОН. Жетон энергии сразу кладёт свои кубики на ЦУ на
     * поле: прежде кубик появлялся только у отстроенного заново ЦУ, и жетон,
     * полученный посреди партии, до тех пор не давал ничего.
     */
    public static void положить(GameState s, PlayerState p, String жетон) {
        p.storageTokens.add(жетон);
        добавитьЭнергиюЦу(s, p, кубиковЦу(жетон));
    }

    private static void добавитьЭнергиюЦу(GameState s, PlayerState p, int сколько) {
        if (s == null || сколько <= 0) {
            return;
        }
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.type == BuildingType.COMMAND_CENTER) {
                b.energyIdle += сколько;
                return;
            }
        }
    }
}
