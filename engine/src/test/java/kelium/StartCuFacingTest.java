package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Field;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;

/**
 * ПОВОРОТ ЦУ НА СТАРТЕ — ОДНО ПРАВИЛО И НИКАКИХ ЗАПРЕТОВ.
 *
 * <p>Решение дизайнера 13.08.2026: «авто» разворачивает ЦУ <b>стенками к центру
 * поля</b> — туда, куда игрок будет развиваться, — и на этом всё. Прежние запреты
 * («не закрывать окно к стартовой грядке», «не накрывать печатную ячейку
 * контейнера») УБРАНЫ по прямому указанию: «не надо ограничивать игроков в
 * размещении ЦУ на старте». Они же и портили картину: часть мест разворачивалась
 * иначе, чем остальные, и расстановка выглядела непоследовательно.
 *
 * <p>Проверка ФИЗИЧЕСКАЯ и независимая от кода: расстояния до центра считаются по
 * соседям, а не той же тригонометрией, которой выбирает {@code Setup}. Иначе тест
 * повторил бы ошибку кода — так уже было 12.08.2026 с перевёрнутым {@code angleGap}.
 */
class StartCuFacingTest {

    @Test
    void cuWallsFaceTheFieldCentre() {
        List<String> bad = new ArrayList<>();
        for (int players = 2; players <= 4; players++) {
            for (long seed = 1; seed <= 40; seed++) {
                GameState s = Setup.buildGame(GameConfig.buildCached(
                    GameConfig.DEFAULT_RULESET, players, seed, null, null));
                double[] centre = fieldCentre(s.field);
                for (PlayerState p : s.players) {
                    Hex sh = s.field.get(p.startHex);
                    BuildingToken cu = commandCentre(p);
                    if (sh == null || cu == null) {
                        continue;
                    }
                    List<Integer> chosen = sidesOf(sh, cu.uid);
                    if (chosen.size() != 2) {
                        continue;               // ЦУ встало не парой — другой случай
                    }
                    double best = Double.NEGATIVE_INFINITY;
                    for (int f = 0; f < 6; f++) {
                        int g = (f + 1) % 6;
                        if (occupiedByOther(sh, f, cu.uid) || occupiedByOther(sh, g, cu.uid)) {
                            continue;           // такая пара была недоступна физически
                        }
                        best = Math.max(best, score(s.field, sh, centre, List.of(f, g)));
                    }
                    double got = score(s.field, sh, centre, chosen);
                    if (got < best - 1e-6) {
                        bad.add(players + "и/сид" + seed + "/место" + (p.seat + 1)
                            + ": ЦУ на " + chosen + " (оценка " + round(got)
                            + "), а лучшая доступная пара давала " + round(best));
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "ЦУ повёрнуто не к центру поля:\n"
            + String.join("\n", bad.subList(0, Math.min(8, bad.size()))));
    }

    /**
     * ЗАДАННЫЙ ВРУЧНУЮ ПОВОРОТ УВАЖАЕТСЯ ВСЕГДА. Игрока не ограничиваем: даже если
     * ЦУ этой гранью закроет окно к грядке или накроет ячейку контейнера, оно
     * встанет именно так, как попросили.
     */
    @Test
    void requestedFacingIsAlwaysHonoured() {
        for (int side = 0; side < 6; side++) {
            List<Integer> want = List.of(side, side, side, side);
            GameState s = Setup.buildGame(GameConfig.buildCached(
                GameConfig.DEFAULT_RULESET, 4, 777L, null, null, null, want));
            for (PlayerState p : s.players) {
                Hex sh = s.field.get(p.startHex);
                BuildingToken cu = commandCentre(p);
                if (sh == null || cu == null) {
                    continue;
                }
                List<Integer> got = sidesOf(sh, cu.uid);
                assertEquals(List.of(side, (side + 1) % 6).stream().sorted().toList(),
                    got.stream().sorted().toList(),
                    "просили грань " + side + ", а место " + (p.seat + 1)
                        + " получило " + got);
            }
        }
    }

    /**
     * Оценка пары сторон: насколько её стенки обращены К ЦЕНТРУ. За сторону, за
     * которой сосед ближе к центру, — плюс; дальше — минус; край поля (соседа нет)
     * — заметный минус, потому что «наружу» это противоположность центру.
     */
    private static double score(Field field, Hex hex, double[] centre, List<Integer> sides) {
        double sum = 0;
        double dMe = dist(axial(hex.id), centre);
        for (int side : sides) {
            String nbId = hex.neighborBySide[side];
            double[] nb = nbId == null ? null : axial(nbId);
            sum += nb == null ? -1.0 : dMe - dist(nb, centre);
        }
        return sum;
    }

    private static boolean occupiedByOther(Hex hex, int side, int uid) {
        Integer owner = hex.sideOwner[side];
        return owner != null && owner != uid;
    }

    private static List<Integer> sidesOf(Hex hex, int uid) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            if (hex.sideOwner[i] != null && hex.sideOwner[i] == uid) {
                out.add(i);
            }
        }
        return out;
    }

    private static BuildingToken commandCentre(PlayerState p) {
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.type == BuildingType.COMMAND_CENTER) {
                return b;
            }
        }
        return null;
    }

    private static double[] fieldCentre(Field field) {
        double sx = 0;
        double sy = 0;
        int n = 0;
        for (Hex h : field.hexes.values()) {
            double[] c = axial(h.id);
            if (c != null) {
                sx += c[0];
                sy += c[1];
                n++;
            }
        }
        return n == 0 ? new double[]{0, 0} : new double[]{sx / n, sy / n};
    }

    /** Пиксельные координаты гекса из его идентификатора вида {@code hQ_R}. */
    private static double[] axial(String id) {
        try {
            String s = id.substring(1);
            int us = s.indexOf('_');
            int q = Integer.parseInt(s.substring(0, us));
            int r = Integer.parseInt(s.substring(us + 1));
            return new double[]{Math.sqrt(3) * (q + r / 2.0), 1.5 * r};
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static double dist(double[] p, double[] c) {
        return p == null ? Double.MAX_VALUE : Math.hypot(p[0] - c[0], p[1] - c[1]);
    }

    private static String round(double v) {
        return String.valueOf(Math.round(v * 100) / 100.0);
    }
}
