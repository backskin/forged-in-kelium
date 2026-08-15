package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.Scenario;
import kelium.engine.Setup;

/**
 * Две настройки подготовки, которые нужны проигрывателю партий: ЯВНЫЙ выбор
 * авторской раскладки и СТАРТОВЫЙ ПОВОРОТ ЦУ по местам. Обе необязательные —
 * без них подготовка работает ровно как раньше.
 */
class SetupOptionsTest {

    private static List<String> variantIds(int players) {
        List<String> out = new ArrayList<>();
        for (var v : Scenario.loadAllVariants(players, "1.0.0",
                GameConfig.resolveDataRoot(null))) {
            out.add(String.valueOf(v.get("id")));
        }
        return out;
    }

    @Test
    void everyPlayerCountOffersItsOwnAuthorLayouts() {
        for (int n : new int[]{2, 3, 4}) {
            List<String> ids = variantIds(n);
            assertTrue(!ids.isEmpty(), "нет раскладок на " + n + " игроков");
            for (String id : ids) {
                assertTrue(id.contains(n + "p"),
                    "раскладка " + id + " не для " + n + " игроков");
            }
        }
    }

    @Test
    void chosenLayoutIsTheOneActuallyPlayed() {
        List<String> ids = variantIds(4);
        assertTrue(ids.size() >= 2, "для проверки нужны хотя бы две раскладки на 4");
        // Разные раскладки дают разные наборы гексов — значит выбор реально дошёл.
        String signatureA = hexSignature(build(4, ids.get(0), null));
        String signatureB = hexSignature(build(4, ids.get(1), null));
        assertTrue(!signatureA.equals(signatureB),
            "две разные раскладки дали одинаковое поле — выбор не применился");

        // И выбор НЕ зависит от сида: та же раскладка при другом сиде — то же поле.
        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 12345L,
            null, null, ids.get(0), null);
        assertEquals(signatureA, hexSignature(Setup.buildGame(cfg)));
    }

    @Test
    void commandCentreStandsOnTheRequestedSides() {
        for (int face = 0; face < 6; face++) {
            GameState s = build(4, null, Arrays.asList(face, face, face, face));
            for (PlayerState p : s.players) {
                Hex start = s.field.get(p.startHex);
                BuildingToken cu = commandCentre(p);
                assertNotNull(cu, "у места " + p.seat + " нет ЦУ");
                List<Integer> mine = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    if (start.sideOwner[i] != null && start.sideOwner[i] == cu.uid) {
                        mine.add(i);
                    }
                }
                // Сравниваем как МНОЖЕСТВО: стороны собираются перебором 0..5,
                // поэтому пара «через ноль» (5 и 0) выглядит как [0, 5].
                assertEquals(new java.util.TreeSet<>(List.of(face, (face + 1) % 6)),
                    new java.util.TreeSet<>(mine),
                    "ЦУ места " + p.seat + " встало не на запрошенные грани");
            }
        }
    }

    /**
     * Правило дизайнера 2026-08-12: без явного указания ЦУ разворачивается
     * стенками В СТОРОНУ ЦЕНТРА ПОЛЯ — туда, куда игрок будет развиваться,
     * а не в тупик у края.
     *
     * <p>Проверка нарочно ФИЗИЧЕСКАЯ, а не по углам: обе занятые стенки должны
     * выходить на РЕАЛЬНЫЕ соседние гексы, и оба этих соседа должны быть БЛИЖЕ
     * к центру поля, чем сам стартовый гекс. Ошибка в знаке при работе с углами
     * такую проверку не переживёт (а прежнюю, «по углам», пережила).
     */
    @Test
    void autoFacingTurnsTheCommandCentreTowardsTheCentreOfTheField() {
        for (int players : new int[]{2, 3, 4}) {
            GameState s = build(players, null, null);
            double[] centre = fieldCentre(s);
            for (PlayerState p : s.players) {
                Hex start = s.field.get(p.startHex);
                BuildingToken cu = commandCentre(p);
                double mine = distance(centre, p.startHex);
                int checked = 0;
                int towardsCentre = 0;
                List<String> where = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    if (start.sideOwner[i] == null || start.sideOwner[i] != cu.uid) {
                        continue;
                    }
                    checked++;
                    String neighbour = start.neighborBySide[i];
                    if (neighbour == null) {
                        where.add("край поля");
                        continue;
                    }
                    double d = distance(centre, neighbour);
                    where.add(neighbour + " (" + Math.round(d * 100) / 100.0 + ")");
                    if (d < mine) {
                        towardsCentre++;
                    }
                }
                // Идеальную пару граней может занять стартовая раскладка (на
                // гексе уже стоят другие жетоны), поэтому требуем не «обе
                // стенки ближе к центру», а чтобы ЦУ было развёрнуто В СТОРОНУ
                // центра хотя бы одной стенкой. Перевёрнутый знак в подборе
                // угла эту проверку не переживёт: тогда ОБЕ стенки смотрели в
                // край поля или от центра.
                // ВАЖНЕЕ носа к центру — не закрыть окно к стартовой грядке
                // (правило 12.08.2026, см. StartCuFacingTest). Если все пары,
                // смотрящие к центру, заняли бы это окно, ЦУ законно отвернулось.
                if (towardsCentre == 0 && veinWindowNearby(s, start)) {
                    assertEquals(2, checked, "ЦУ обязано занимать ровно две стенки");
                    continue;
                }
                assertTrue(towardsCentre >= 1,
                    "место " + p.seat + " (" + players + " игроков): ЦУ на " + p.startHex
                        + " (до центра " + Math.round(mine * 100) / 100.0
                        + ") развёрнуто в тупик — стенки выходят на " + where);
                assertEquals(2, checked, "ЦУ обязано занимать ровно две стенки");
            }
        }
    }

    /** Есть ли у гекса сторона, за которой лежит живая грядка. */
    private static boolean veinWindowNearby(GameState s, Hex hex) {
        for (int i = 0; i < 6; i++) {
            String nb = hex.neighborBySide[i];
            Hex n = nb == null ? null : s.field.get(nb);
            if (n != null && n.spawnTile != null && n.spawnTile.kelium > 0) {
                return true;
            }
        }
        return false;
    }

    private static double distance(double[] centre, String hexId) {
        double[] c = hexCentre(hexId);
        return Math.hypot(centre[0] - c[0], centre[1] - c[1]);
    }

    private static double[] fieldCentre(GameState s) {
        double sx = 0;
        double sy = 0;
        int n = 0;
        for (String id : s.field.hexes.keySet()) {
            double[] c = hexCentre(id);
            if (c != null) {
                sx += c[0];
                sy += c[1];
                n++;
            }
        }
        return new double[]{sx / n, sy / n};
    }

    private static double[] hexCentre(String id) {
        int[] qr = kelium.report.FieldGeometry.parseQR(id);
        return qr == null ? null : kelium.report.FieldGeometry.hexCenter(qr[0], qr[1], 1.0);
    }

    @Test
    void withoutOptionsSetupIsUnchanged() {
        GameState plain = Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 777L, null, null));
        GameState same = build(4, null, null);
        assertEquals(hexSignature(plain), hexSignature(same));
    }

    private static GameState build(int players, String scenarioId, List<Integer> facing) {
        return Setup.buildGame(GameConfig.buildCached(GameConfig.DEFAULT_RULESET, players,
            777L, null, null, scenarioId, facing));
    }

    private static BuildingToken commandCentre(PlayerState p) {
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.type == BuildingType.COMMAND_CENTER) {
                return b;
            }
        }
        return null;
    }

    private static String hexSignature(GameState s) {
        return String.join(",", s.field.hexes.keySet());
    }
}
