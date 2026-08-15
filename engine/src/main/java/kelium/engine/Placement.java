package kelium.engine;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.ability.Hook;
import kelium.engine.ability.RuleQuery;

/**
 * Placement — ОБЩИЕ ПРАВИЛА РАЗМЕЩЕНИЯ жетонов на поле.
 *
 * <p>Это не внутренности одного действия: следом здания, списком гексов,
 * доступных для стройки, и загрузкой гекса пользуются и подготовка партии, и
 * движок, и боты. Пока всё это лежало внутри {@code Actions}, стартовая
 * расстановка считала след здания своим зашитым числом и разъезжалась с
 * движком при первой же правке данных.
 */
public final class Placement {

    private Placement() {
    }

    /** След здания = сколько СМЕЖНЫХ сторон гекса (из 6) оно занимает. */
    private static final Map<BuildingType, Integer> FOOTPRINT =
        new EnumMap<>(BuildingType.class);

    static {
        FOOTPRINT.put(BuildingType.BARRACKS, 2);
        FOOTPRINT.put(BuildingType.FACTORY, 2);
        FOOTPRINT.put(BuildingType.AIRBASE, 3);
        FOOTPRINT.put(BuildingType.COMMAND_CENTER, 2);
        FOOTPRINT.put(BuildingType.MINER, 1);
        FOOTPRINT.put(BuildingType.POWER_PLANT, 1);
    }

    /** Сколько смежных сторон гекса занимает здание данного типа. */
    public static int footprint(BuildingType btype) {
        return FOOTPRINT.getOrDefault(btype, 1);
    }

    /**
     * Гексы, где игроку доступна Стройка: свои гексы (где есть его здание) и
     * гексы, ПРИМЫКАЮЩИЕ СТЕНКОЙ к его зданию. Исключаются запрещённые, тайлы
     * зарождения, гексы с чужой постройкой.
     */
    /**
     * Гексы, где игроку доступна Стройка.
     *
     * <p><b>Правило (уточнено дизайнером 12.08.2026).</b> Строить можно:
     * <ul>
     *   <li>на СВОИХ гексах — там, где уже стоит твоё здание;</li>
     *   <li>на гексе, к которому твоё здание <b>примыкает СТЕНКОЙ</b> — то есть
     *       занимает именно ту ячейку, что смотрит на этот гекс.</li>
     * </ul>
     *
     * <p>Раньше сюда попадали ВСЕ соседи своих гексов, независимо от того, какой
     * стороной стоит здание. Из-за этого зона стройки расползалась кольцом, а
     * поворот здания ничего не решал — хотя именно он и должен решать: куда
     * повернул, туда и растёшь.
     *
     * <p>Проход закрыт, если ту же общую стенку с ДРУГОЙ стороны занимает чужое
     * или нейтральное здание: оно работает стенкой и не даёт ни расширять зону
     * стройки, ни ходить наземкой. <b>Но нейтрал, который просто лежит на
     * гексе, не занимая нужную сторону, — не преграда</b> (баг найден
     * дизайнером 14.08.2026): у него, как и у любого здания, есть СВОИ
     * стороны, а не весь гекс целиком.
     */
    public static List<String> buildableHexes(GameState state, int seat) {
        PlayerState p = state.player(seat);
        Set<String> ownHexes = new java.util.LinkedHashSet<>();
        for (BuildingToken b : p.buildingsOnField()) {
            ownHexes.add(b.hexId);
        }
        Set<String> result = new java.util.LinkedHashSet<>(ownHexes);

        // Расширение зоны — ТОЛЬКО через стенки, которые занимают свои здания.
        for (BuildingToken b : p.buildingsOnField()) {
            Hex from = state.field.get(b.hexId);
            if (from == null) {
                continue;
            }
            for (int side = 0; side < 6; side++) {
                if (from.sideOwner[side] == null || from.sideOwner[side] != b.uid) {
                    continue;   // этой стенкой здание не стоит
                }
                String nbId = from.neighborBySide[side];
                if (nbId == null) {
                    continue;
                }
                Hex nb = state.field.get(nbId);
                if (nb == null) {
                    continue;
                }
                // общую стенку с той стороны может закрывать чужое здание или
                // нейтрал — тогда прохода нет
                Integer far = nb.sideOwner[(side + 3) % 6];
                if (far != null && !ownsToken(p, far)) {
                    continue;
                }
                result.add(nbId);
            }
        }

        // ТОЧКА ПРАВИЛ: карта арсенала «Вольная застройка» снимает требование
        // ПРИМЫКАНИЯ СВОЕЙ СТЕНКОЙ — строить можно на любом гексе, соседнем со
        // своим. Чужая или нейтральная стенка с той стороны по-прежнему держит
        // проход: карта отменяет требование к СВОЕЙ форме, а не физическую
        // преграду.
        boolean adjacentWithoutWall = RuleQuery
            .of(state, seat, Hook.BUILD_ZONE).base(0).ask() >= 1;
        if (adjacentWithoutWall) {
            for (String own : ownHexes) {
                Hex from = state.field.get(own);
                if (from == null) {
                    continue;
                }
                for (int side = 0; side < 6; side++) {
                    String nbId = from.neighborBySide[side];
                    if (nbId == null) {
                        continue;
                    }
                    Hex nb = state.field.get(nbId);
                    if (nb == null) {
                        continue;
                    }
                    Integer far = nb.sideOwner[(side + 3) % 6];
                    if (far != null && !ownsToken(p, far)) {
                        continue;
                    }
                    result.add(nbId);
                }
            }
        }

        List<String> ok = new ArrayList<>();
        for (String hid : result) {
            Hex h = state.field.get(hid);
            if (h.kind == HexKind.FORBIDDEN || h.hasSpawnTile()) {
                continue;
            }
            // НЕЙТРАЛЬНОЕ ЗДАНИЕ САМО ПО СЕБЕ НЕ ЗАКРЫВАЕТ ГЕКС (баг найден
            // дизайнером 14.08.2026): оно занимает СВОИ стороны гекса, и если
            // общее ребро с ЦУ (или другим своим зданием) не среди них, проход
            // уже проверен выше — по стороне, а не по гексу целиком (см. far в
            // цикле выше). Гекс с нейтралом, который просто ЛЕЖИТ на нём, не
            // трогая нужную стенку, — обычный кандидат для стройки; будет ли
            // на нём в итоге место для ВСЕГО следа здания — решает chooseFootprint
            // в момент самой стройки, не эта функция.
            boolean blocked = false;
            for (PlayerState pl : state.players) {
                if (pl.seat == seat) {
                    continue;
                }
                for (BuildingToken b : pl.buildingsOnField()) {
                    if (hid.equals(b.hexId)) {
                        blocked = true;
                        break;
                    }
                }
                if (blocked) {
                    break;
                }
            }
            if (!blocked) {
                ok.add(hid);
            }
        }
        return ok;
    }

    /** Принадлежит ли жетон с этим uid игроку {@code p} (стенки нейтралов — нет). */
    private static boolean ownsToken(PlayerState p, int uid) {
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.uid == uid) {
                return true;
            }
        }
        return false;
    }


    /** Монотонный uid для вновь создаваемых жетонов. */
    public static int nextUid(GameState state) {
        int m = 0;
        for (PlayerState p : state.players) {
            for (UnitToken t : p.units) {
                m = Math.max(m, t.uid);
            }
            for (BuildingToken t : p.buildings) {
                m = Math.max(m, t.uid);
            }
        }
        return m + 1;
    }

    /**
     * Наземная нагрузка гекса войсками (все игроки): [техника, одиночные].
     * excludeUid — жетон, который сейчас входит/выходит (не считать), -1 = никто.
     * Техника занимает 2 смежные ячейки, одиночные (пехота/вышка) — одну;
     * конкретные позиции не считаются — войска переупаковываются (нежёсткие).
     */
    public static int[] groundLoad(GameState state, String hexId, int excludeUid) {
        int veh = 0;
        int single = 0;
        for (PlayerState p : state.players) {
            for (UnitToken u : p.units) {
                // Войско ВНУТРИ здания ячейку гекса не занимает — в этом весь
                // смысл: его туда и вставляют, когда на гексе места нет.
                if (u.inside()) {
                    continue;
                }
                if (hexId.equals(u.hexId) && u.uid != excludeUid
                        && u.type != UnitType.AIRCRAFT) {
                    if (u.type == UnitType.VEHICLE) {
                        veh++;
                    } else {
                        single++;
                    }
                }
            }
        }
        return new int[]{veh, single};
    }
}
