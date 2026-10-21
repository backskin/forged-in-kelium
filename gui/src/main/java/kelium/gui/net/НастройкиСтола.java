package kelium.gui.net;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kelium.gui.HotSeatWindow;
import kelium.report.Json;

/**
 * НАСТРОЙКИ СТОЛА НА ПРОВОДЕ — те же {@link HotSeatWindow.Options}, записанные
 * сообщением и прочитанные обратно.
 *
 * <p>Передаются они целиком и ровно один раз, на старте: раз у каждого
 * участника крутится своя копия движка, разойтись настройкам нельзя ни в одном
 * поле — иначе столы разъедутся на первом же решении.
 *
 * <p>ФАЙЛ РАСКЛАДКИ ПО СЕТИ НЕ ЕДЕТ. Своя раскладка (`scenarioFile`) — это путь
 * на диске хозяина, и у гостя такого файла нет. Стол с ней не поднимается
 * вовсе: лучше сказать это в лобби, чем дать партии развалиться на подготовке.
 */
public final class НастройкиСтола {

    private НастройкиСтола() {
    }

    /** Почему эти настройки нельзя отдать по сети, или {@code null}. */
    public static String почемуНельзя(HotSeatWindow.Options о) {
        if (о.scenarioFile() != null) {
            return "стол со своей раскладкой из файла по сети не поднимается: "
                + "у гостей этого файла нет. Выберите раскладку из списка.";
        }
        return null;
    }

    public static Map<String, Object> вСообщение(HotSeatWindow.Options о) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("свод", о.rulesetId());
        m.put("игроков", о.players());
        m.put("сид", о.seed());
        m.put("составы", о.seatSpecs());
        m.put("раскладка", о.scenarioId());
        m.put("поворотыЦУ", о.cuFacing());
        m.put("краски", о.seatColors());
        m.put("стартМонеты", о.startCoins());
        m.put("стартКелемий", о.startKelium());
        m.put("стартБоеприпасы", о.startAmmo());
        return m;
    }

    public static HotSeatWindow.Options изСообщения(Map<String, Object> m) {
        long сид = m.get("сид") instanceof Number n ? n.longValue() : 0L;
        return new HotSeatWindow.Options(
            Json.s(m, "свод"), Json.i(m, "игроков"), сид,
            Json.strings(m, "составы"),
            Json.s(m, "раскладка"), null,
            целые(m, "поворотыЦУ"), целые(m, "краски"),
            Json.io(m, "стартМонеты"), Json.io(m, "стартКелемий"),
            Json.io(m, "стартБоеприпасы"));
    }

    private static List<Integer> целые(Map<String, Object> m, String ключ) {
        if (!(m.get(ключ) instanceof List<?> список)) {
            return null;
        }
        List<Integer> out = new ArrayList<>();
        for (Object o : список) {
            out.add(o instanceof Number n ? n.intValue() : 0);
        }
        return out;
    }
}
