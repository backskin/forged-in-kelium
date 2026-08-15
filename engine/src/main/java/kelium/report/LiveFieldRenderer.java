package kelium.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.core.UnitType;

/**
 * LiveFieldRenderer — ASCII-снимок ТЕКУЩЕГО состояния поля (не файла сценария).
 * Показывает где чьи здания/войска, грядки, контейнеры — с id гексов, чтобы
 * рассказ можно было читать «по карте».
 *
 * <p>Координаты берём из id гекса вида {@code h<q>_<r>} (так их кодирует
 * {@code Scenario.hexId}). Метки компактные: тип объекта + номер места (0-3).
 */
public final class LiveFieldRenderer {

    private LiveFieldRenderer() {
    }

    /** Отрисовать текущее поле GameState в многострочный ASCII с легендой. */
    public static String render(GameState s) {
        // индекс гексов по (q,r)
        TreeMap<Long, Hex> byQR = new TreeMap<>();
        int minq = Integer.MAX_VALUE;
        int maxq = Integer.MIN_VALUE;
        int minr = Integer.MAX_VALUE;
        int maxr = Integer.MIN_VALUE;
        boolean anyQR = false;
        for (Hex h : s.field.hexes.values()) {
            int[] qr = parseQR(h.id);
            if (qr == null) {
                continue;
            }
            anyQR = true;
            byQR.put(key(qr[0], qr[1]), h);
            minq = Math.min(minq, qr[0]);
            maxq = Math.max(maxq, qr[0]);
            minr = Math.min(minr, qr[1]);
            maxr = Math.max(maxr, qr[1]);
        }
        if (!anyQR) {
            return "(поле без осевых координат — рендер недоступен)";
        }

        List<String> lines = new ArrayList<>();
        for (int r = minr; r <= maxr; r++) {
            StringBuilder row = new StringBuilder("  ".repeat(r - minr));
            for (int q = minq; q <= maxq; q++) {
                Hex h = byQR.get(key(q, r));
                row.append(String.format("%-6s", h != null ? cell(s, h) : ""));
                row.append(' ');
            }
            lines.add(row.toString().stripTrailing());
        }
        lines.add("");
        lines.add("  легенда: ЦУ# ком.центр · Зв# завод · Ав# авиабаза · Кз# казарма · "
            + "Д# добытчик · Э# энергостанция  (#=место)");
        lines.add("  войска: п пехота · т техника · а авиация · в вышка (с местом) · "
            + "K грядка · C контейнер · # запрет");
        return String.join("\n", lines);
    }

    /** Содержимое одного гекса: приоритет зданиям, затем войска/грядка/контейнер. */
    private static String cell(GameState s, Hex h) {
        // здания на гексе
        for (PlayerState p : s.players) {
            for (BuildingToken b : p.buildingsOnField()) {
                if (h.id.equals(b.hexId)) {
                    return bldCode(b.type) + p.seat + tail(s, h);
                }
            }
        }
        // войска на гексе
        for (PlayerState p : s.players) {
            for (UnitToken u : p.unitsOnField()) {
                if (h.id.equals(u.hexId)) {
                    return unitCode(u.type) + p.seat + tail(s, h);
                }
            }
        }
        // грядка / нейтрал / контейнер / запрет
        if (h.kind == HexKind.FORBIDDEN) {
            return "#";
        }
        if (h.hasNeutral()) {
            return (h.anyNeutralBig() ? "NEU+" : "NEU")
                + (h.neutrals.size() > 1 ? "x" + h.neutrals.size() : "");
        }
        if (h.spawnTile != null) {
            return "K" + Math.max(0, h.spawnTile.kelium);
        }
        if (h.containerCell >= 0) {
            return "C" + h.containerCell;
        }
        return "·";
    }

    /** Хвостовая пометка гекса (контейнер поверх занятого). */
    private static String tail(GameState s, Hex h) {
        return h.containerCell >= 0 ? "+C" : "";
    }

    private static String bldCode(BuildingType t) {
        return switch (t) {
            case COMMAND_CENTER -> "ЦУ";
            case FACTORY -> "Зв";
            case AIRBASE -> "Ав";
            case BARRACKS -> "Кз";
            case MINER -> "Д";
            case POWER_PLANT -> "Э";
        };
    }

    private static String unitCode(UnitType t) {
        return switch (t) {
            case INFANTRY -> "п";
            case VEHICLE -> "т";
            case AIRCRAFT -> "а";
            case TOWER -> "в";
        };
    }

    /** Разобрать id вида h<q>_<r> в [q,r]; null, если формат иной. */
    private static int[] parseQR(String id) {
        if (id == null || !id.startsWith("h") || !id.contains("_")) {
            return null;
        }
        try {
            String body = id.substring(1);
            int us = body.indexOf('_');
            int q = Integer.parseInt(body.substring(0, us));
            int r = Integer.parseInt(body.substring(us + 1));
            return new int[]{q, r};
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static long key(int q, int r) {
        return ((long) (q + 1000) << 20) | (r + 1000);
    }
}
