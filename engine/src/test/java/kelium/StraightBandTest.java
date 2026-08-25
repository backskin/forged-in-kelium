package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;
import kelium.engine.Shapes;

/**
 * ПРЯМАЯ ПОЛОСА ИЗ ПОЛОВИН ГЕКСОВ — определение дизайнера 19.08.2026: полоса
 * идёт через половины гексов (три смежные ячейки) и продолжается на следующем
 * гексе через общую грань, не меняя направления вращения.
 *
 * <p>ЗАЧЕМ ЭТОТ ТЕСТ. Геометрия — ровно то место, где непроверенное правило
 * рождает НЕВЫПОЛНИМУЮ карту: замер 19.08.2026 показал, что две карты-фигуры из
 * трёх (o50, o54) не выполнялись НИ РАЗУ за 300 партий. Поэтому распознаватель
 * проверяется на руками собранных фигурах: правильная полоса должна находиться,
 * а изгиб и недобор — нет.
 */
class StraightBandTest {

    private static GameState game() {
        return Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 909L, null, null));
    }

    /** Очистить поле и войска игрока, чтобы фигуру не портила чужая обстановка. */
    private static PlayerState cleanSeat(GameState s, int seat) {
        for (Hex h : s.field.hexes.values()) {
            for (int i = 0; i < 6; i++) {
                h.sideOwner[i] = null;
            }
        }
        for (PlayerState p : s.players) {
            for (UnitToken u : p.units) {
                u.hexId = null;
            }
            for (var b : p.buildings) {
                b.hexId = null;
            }
        }
        return s.player(seat);
    }

    /**
     * СВОЙ ЖЕТОН ДЛЯ ФИГУРЫ — ЗДАНИЕ, А НЕ ВОЙСКО (правка 25.08.2026).
     *
     * <p>Прежде фигуру собирали пехотой, кладя ОДИН жетон сразу на три сектора.
     * По СВОДу так нельзя: пехота занимает один сектор, техника два смежных, и
     * многосекторный след бывает только у зданий. Пока войска не имели секторов
     * вовсе, разница была незаметна; теперь раскладка войск выводится из
     * свободных секторов ({@code СекторыВойск}), и жетон, вписанный в разметку
     * руками, сам себе занимает место. Фигура собирается зданиями — у них след
     * из смежных секторов законен и хранится именно в разметке гекса.
     */
    private static BuildingToken freshBuilding(PlayerState p, int uid) {
        BuildingToken b = new BuildingToken(BuildingType.AIRBASE, p.seat, 3, 3, null, uid);
        p.buildings.add(b);
        return b;
    }

    /** Поставить здание на гекс и занять им перечисленные секторы. */
    private static void occupy(GameState s, BuildingToken b, String hexId, int... cells) {
        b.hexId = hexId;
        Hex h = s.field.get(hexId);
        for (int c : cells) {
            h.sideOwner[Math.floorMod(c, 6)] = b.uid;
        }
    }

    /** Пара соседних гексов и стороны, которыми они смотрят друг на друга. */
    private static int[] facing(GameState s, Hex a, String bId) {
        Hex b = s.field.get(bId);
        int dA = -1;
        int dB = -1;
        for (int k = 0; k < 6; k++) {
            if (bId.equals(a.neighborBySide[k])) {
                dA = k;
            }
            if (a.id.equals(b.neighborBySide[k])) {
                dB = k;
            }
        }
        return new int[]{dA, dB};
    }

    private static Hex someHexWithNeighbour(GameState s) {
        for (Hex h : s.field.hexes.values()) {
            for (int k = 0; k < 6; k++) {
                if (h.neighborBySide[k] != null) {
                    return h;
                }
            }
        }
        throw new IllegalStateException("на поле нет ни одной пары соседей");
    }

    @Test
    void прямаяПолосаНаДвухГексахНаходится() {
        GameState s = game();
        PlayerState me = cleanSeat(s, 0);
        Hex a = someHexWithNeighbour(s);
        String bId = null;
        for (int k = 0; k < 6; k++) {
            if (a.neighborBySide[k] != null) {
                bId = a.neighborBySide[k];
                break;
            }
        }
        int[] d = facing(s, a, bId);
        int dir = 1;

        // ПОЛОВИНА A — три ячейки, ЗАКАНЧИВАЮЩИЕСЯ на стороне к B;
        // ПОЛОВИНА B — три ячейки, НАЧИНАЮЩИЕСЯ со стороны к A, то же вращение.
        occupy(s, freshBuilding(me, 9001), a.id,
            d[0] - 2 * dir, d[0] - dir, d[0]);
        occupy(s, freshBuilding(me, 9002), bId,
            d[1], d[1] + dir, d[1] + 2 * dir);

        assertEquals(2, Shapes.longestStraightBand(s, 0),
            "полоса из двух половин должна распознаться как длина 2");
        assertTrue(Shapes.straightSixCells(s, 0),
            "шесть ячеек на двух гексах вдоль прямой — это и есть требование карты");
    }

    @Test
    void изгибНеСчитаетсяПрямой() {
        GameState s = game();
        PlayerState me = cleanSeat(s, 0);

        Hex a = someHexWithNeighbour(s);
        String bId = null;
        for (int k = 0; k < 6; k++) {
            if (a.neighborBySide[k] != null) {
                bId = a.neighborBySide[k];
                break;
            }
        }
        int[] d = facing(s, a, bId);

        // ВРАЩЕНИЕ РАЗВЁРНУТО НА ВТОРОМ ГЕКСЕ: половины смыкаются через общее
        // ребро, но полоса заворачивает назад, а не идёт прямо.
        occupy(s, freshBuilding(me, 9011), a.id, d[0] - 2, d[0] - 1, d[0]);
        occupy(s, freshBuilding(me, 9012), bId, d[1], d[1] - 1, d[1] - 2);

        assertTrue(Shapes.longestStraightBand(s, 0) < 2,
            "изгиб не должен считаться прямой полосой из двух половин");
    }

    @Test
    void неполнаяПоловинаНеСчитается() {
        GameState s = game();
        PlayerState me = cleanSeat(s, 0);

        Hex a = someHexWithNeighbour(s);
        // ДВЕ ЯЧЕЙКИ ВМЕСТО ТРЁХ — половины нет, значит и полосы нет.
        occupy(s, freshBuilding(me, 9021), a.id, 0, 1);

        assertEquals(0, Shapes.longestStraightBand(s, 0),
            "две ячейки — это не половина гекса");
    }
}
