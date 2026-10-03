package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.Figures;
import kelium.support.Fix;

/**
 * УЗОР ИЗ СЕКТОРОВ (дизайнер 03.10.2026): закрашенные на карте секторы гексов
 * должны быть заняты своими жетонами. Узор можно поворачивать, отражать нельзя.
 */
class УзорСекторовTest {

    /** Узор: опорный гекс — секторы 0 и 1, сосед по стороне 0 — сектор 2. */
    private static Map<String, Object> узор(int поворот, boolean зеркало) {
        int[][] пути = {{}, {0}};
        int[][] сек = {{0, 1}, {2}};
        List<Object> клетки = new ArrayList<>();
        for (int i = 0; i < пути.length; i++) {
            List<Integer> путь = new ArrayList<>();
            for (int x : пути[i]) {
                путь.add(преобразить(x, поворот, зеркало));
            }
            List<Integer> с = new ArrayList<>();
            for (int x : сек[i]) {
                с.add(преобразить(x, поворот, зеркало));
            }
            клетки.add(Map.of("путь", путь, "секторы", с));
        }
        return Map.of("узел", "узор", "клетки", клетки);
    }

    /** Поворот — прибавка; зеркало относительно оси стороны 0 — смена знака. */
    private static int преобразить(int side, int поворот, boolean зеркало) {
        int x = зеркало ? Math.floorMod(-side, 6) : side;
        return Math.floorMod(x + поворот, 6);
    }

    /**
     * Выложить узор жетонами игрока 0 на первом подходящем месте поля: каждый
     * сектор — отдельная пехота. false — места не нашлось.
     */
    private static boolean выложить(GameState s, Map<String, Object> у) {
        for (Hex опора : s.field.hexes.values()) {
            List<Hex> гексы = new ArrayList<>();
            List<List<Integer>> секторы = new ArrayList<>();
            boolean можно = true;
            for (Object o : (List<?>) у.get("клетки")) {
                Map<?, ?> к = (Map<?, ?>) o;
                Hex h = опора;
                for (Object st : (List<?>) к.get("путь")) {
                    String n = h.neighborBySide[((Number) st).intValue()];
                    h = n == null ? null : s.field.get(n);
                    if (h == null) {
                        break;
                    }
                }
                if (h == null) {
                    можно = false;
                    break;
                }
                List<Integer> с = new ArrayList<>();
                for (Object x : (List<?>) к.get("секторы")) {
                    int side = ((Number) x).intValue();
                    if (h.sideOwner[side] != null) {
                        можно = false;
                    }
                    с.add(side);
                }
                гексы.add(h);
                секторы.add(с);
            }
            if (!можно) {
                continue;
            }
            int uid = 9100;
            for (int i = 0; i < гексы.size(); i++) {
                for (int side : секторы.get(i)) {
                    UnitToken u = new UnitToken(UnitType.INFANTRY, 0, 1, uid);
                    u.setHexId(гексы.get(i).id);
                    s.player(0).units.add(u);
                    гексы.get(i).occupySides(uid, List.of(side));
                    uid++;
                }
            }
            return true;
        }
        return false;
    }

    @Test
    void узорНаходитсяПриЛюбомПовороте() {
        for (int поворот = 0; поворот < 6; поворот++) {
            GameState s = Fix.game(4, 31337L);
            assertTrue(выложить(s, узор(поворот, false)), "место для узора есть");
            assertTrue(Figures.sectorsSatisfied(s, 0, узор(0, false)),
                "узор, повёрнутый на " + поворот + "×60°, засчитан");
            assertEquals(3, Figures.sectorsBest(s, 0, узор(0, false)));
        }
    }

    @Test
    void зеркальныйУзорНеЗасчитан() {
        GameState s = Fix.game(4, 31337L);
        assertTrue(выложить(s, узор(0, true)), "место для зеркала есть");
        assertFalse(Figures.sectorsSatisfied(s, 0, узор(0, false)),
            "отражённый узор — другая фигура, карту лицом вниз не переворачивают");
    }

    @Test
    void чужиеЖетоныНеСчитаются() {
        GameState s = Fix.game(4, 31337L);
        Map<String, Object> у = узор(0, false);
        assertTrue(выложить(s, у));
        // те же жетоны — у другого игрока
        List<UnitToken> мои = new ArrayList<>(s.player(0).units);
        for (UnitToken u : мои) {
            if (u.uid >= 9100) {
                s.player(0).units.remove(u);
                s.player(1).units.add(u);
            }
        }
        assertFalse(Figures.sectorsSatisfied(s, 0, у), "узор из чужих жетонов не мой");
    }
}
