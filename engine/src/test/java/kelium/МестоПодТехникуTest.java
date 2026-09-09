package kelium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.Actions;
import kelium.engine.Setup;

/**
 * МЕСТО ПОД ТО, ЧТО ЗДАНИЕ ПРОИЗВОДИТ.
 *
 * <p>Замечание дизайнера 08.09.2026 по записи партии: «почему он ставит завод
 * таким образом, что невозможно поставить технику?» Технике нужны ДВЕ СМЕЖНЫЕ
 * свободные ячейки гекса, и завод, вставший не туда, за всю партию не выпускает
 * ни одного жетона: здание построено, деньги потрачены, толку ноль.
 *
 * <p>Правило живёт в движке ({@link Actions#roomForBuildingAndUnit} и
 * {@link Actions#roomAfterFootprint}), а бот им пользуется. Здесь проверяется
 * само правило: оно обязано отличать просторный гекс от тесного и — главное —
 * ХОРОШИЙ ПОВОРОТ от плохого на одном и том же гексе.
 */
class МестоПодТехникуTest {

    private GameState стол() {
        return Setup.buildGame(GameConfig.build(4, 7L));
    }

    /** Гекс, у которого свободны все стороны. */
    private String пустойГекс(GameState s) {
        for (Hex h : s.field.hexes.values()) {
            if (h.kind != kelium.core.HexKind.NORMAL || h.hasNeutral() || h.spawnTile != null) {
                continue;
            }
            boolean всеСвободны = true;
            for (Integer владелец : h.sideOwner) {
                всеСвободны &= владелец == null;
            }
            if (всеСвободны) {
                return h.id;
            }
        }
        throw new IllegalStateException("на поле не нашлось пустого гекса");
    }

    @Test
    void наПустомГексеЗаводИТехникаУживаются() {
        GameState s = стол();
        String hex = пустойГекс(s);
        int след = Actions.buildingFootprint(BuildingType.FACTORY);
        assertTrue(Actions.roomForBuildingAndUnit(s, hex, след, UnitType.VEHICLE),
            "на пустом гексе завод и техника обязаны уместиться");
    }

    @Test
    void поворотРешает() {
        GameState s = стол();
        String hex = пустойГекс(s);
        Hex h = s.field.get(hex);
        int сторон = h.sideOwner.length;
        // Занимаем стороны так, чтобы свободными остались ровно три подряд:
        // тогда завод из двух ячеек оставит технике либо одну (плохо), либо
        // две смежные (хорошо) — в зависимости от того, куда встанет.
        for (int i = 3; i < сторон; i++) {
            h.sideOwner[i] = 99;
        }
        // Здание встало посередине свободной дуги — техника уже не влезет.
        assertFalse(Actions.roomAfterFootprint(s, hex, List.of(1, 2), UnitType.VEHICLE),
            "след посреди свободной дуги режет место под технику");
        // Здание прижалось к краю дуги — две смежные ячейки остались... нет,
        // осталась одна: значит и этот поворот плох, и правило это видит.
        assertFalse(Actions.roomAfterFootprint(s, hex, List.of(0, 1), UnitType.VEHICLE),
            "после двухклеточного здания на трёх свободных ячейках техники не будет");
        // А пехоте одной ячейки хватает — и правило обязано это различать.
        assertTrue(Actions.roomAfterFootprint(s, hex, List.of(0, 1), UnitType.INFANTRY),
            "пехоте достаточно одной свободной ячейки");
    }

    @Test
    void наТесномГексеЗаводНеПройдёт() {
        GameState s = стол();
        String hex = пустойГекс(s);
        Hex h = s.field.get(hex);
        // Оставляем ровно две свободные стороны: сам завод в них влезет, а
        // технике места не останется вовсе.
        for (int i = 2; i < h.sideOwner.length; i++) {
            h.sideOwner[i] = 99;
        }
        int след = Actions.buildingFootprint(BuildingType.FACTORY);
        assertFalse(Actions.roomForBuildingAndUnit(s, hex, след, UnitType.VEHICLE),
            "гекс, где после завода не остаётся места технике, годным считаться не может");
    }
}
