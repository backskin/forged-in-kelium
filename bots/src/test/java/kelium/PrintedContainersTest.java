package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.agents.RandomAgent;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.core.PlayerState;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.BlockStamp;
import kelium.engine.GameEngine;
import kelium.engine.PrintedContainers;
import kelium.engine.Setup;

/**
 * КОНТЕЙНЕРЫ 2.0 (правило дизайнера 12.08.2026): контейнеры напечатаны на
 * картонных блоках поля, а не лежат жетонами. Жетон, вставший на такую ячейку,
 * немедленно берёт карту контейнера из запаса — каждый раз, когда встаёт.
 */
class PrintedContainersTest {

    private static GameState game(long seed) {
        return Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, seed, null, null));
    }

    @Test
    void mostPlayableHexesCarryAPrintedContainerButNotAll() {
        GameState s = game(21L);
        int playable = 0;
        int printed = 0;
        for (Hex h : s.field.hexes.values()) {
            boolean canStand = h.kind != HexKind.FORBIDDEN && h.spawnTile == null;
            if (canStand) {
                playable++;
                if (h.containerCell >= 0) {
                    printed++;
                }
            } else {
                assertEquals(-1, h.containerCell,
                    "на тайле зарождения и запретном гексе контейнеров не бывает: "
                        + "жетон закрывает все ячейки (" + h.id + ")");
            }
        }
        assertTrue(playable > 0, "поле не пустое");
        // плотность набора 1.4.0: 3 контейнера на 5 гексов малого блока,
        // 4 на 6 гексов большого — то есть примерно три пятых гексов
        assertTrue(printed < playable, "часть гексов должна остаться без контейнера");
        assertTrue(printed >= playable / 2,
            "контейнеров всё же большинство: " + printed + " из " + playable);
    }

    @Test
    void airContainersAppearAboutOncePerBlock() {
        GameState s = game(22L);
        int printed = 0;
        int air = 0;
        for (Hex h : s.field.hexes.values()) {
            if (h.containerCell >= 0) {
                printed++;
                if (h.containerCell == BlockStamp.AIR) {
                    air++;
                }
            }
        }
        // на каждой стороне блока ровно один воздушный контейнер из его 3–4
        // (набор 1.4.0) — значит воздушный примерно каждый третий-четвёртый
        assertTrue(air > 0, "воздушные контейнеры обязаны быть");
        assertTrue(air <= printed / 3 + 1,
            "воздушных контейнеров не должно быть больше одного на блок: "
                + air + " из " + printed);
    }

    @Test
    void groundUnitTakesGroundContainerAndAircraftTakesAirOne() {
        GameState s = game(23L);
        PlayerState p = s.player(0);
        Hex ground = null;
        Hex sky = null;
        for (Hex h : s.field.hexes.values()) {
            if (h.containerCell >= 0 && h.containerCell != BlockStamp.AIR && ground == null) {
                ground = h;
            }
            if (h.containerCell == BlockStamp.AIR && sky == null) {
                sky = h;
            }
        }
        assertTrue(ground != null && sky != null, "нашлись оба вида ячеек");

        int before = p.containers;
        assertEquals(1, PrintedContainers.onUnitPlaced(s, p, ground.id, UnitType.INFANTRY),
            "пехота встала на наземный печатный контейнер — берёт карту");
        assertEquals(before + 1, p.containers);

        assertEquals(0, PrintedContainers.onUnitPlaced(s, p, sky.id, UnitType.INFANTRY),
            "наземному до воздушной ячейки не дотянуться");
        assertEquals(0, PrintedContainers.onUnitPlaced(s, p, ground.id, UnitType.AIRCRAFT),
            "авиация садится только в воздушную ячейку");
        assertEquals(1, PrintedContainers.onUnitPlaced(s, p, sky.id, UnitType.AIRCRAFT),
            "авиация берёт воздушный контейнер");
    }

    @Test
    void printedCellDoesNotBurnOut() {
        GameState s = game(24L);
        PlayerState p = s.player(0);
        Hex ground = null;
        for (Hex h : s.field.hexes.values()) {
            if (h.containerCell >= 0 && h.containerCell != BlockStamp.AIR) {
                ground = h;
                break;
            }
        }
        // «Срабатывает каждый раз при заходе» — решение дизайнера
        assertEquals(1, PrintedContainers.onUnitPlaced(s, p, ground.id, UnitType.INFANTRY));
        assertEquals(1, PrintedContainers.onUnitPlaced(s, p, ground.id, UnitType.INFANTRY));
        assertTrue(ground.containerCell >= 0, "ячейка не выгорает");
    }

    @Test
    void everyPlayerAlwaysStartsWithExactlyOneContainer() {
        // Стартовая карта выдаётся ИЗ ЗАПАСА и не зависит от того, что легло на
        // стартовый гекс: иначе старт был бы неравным из-за случайной раскладки.
        for (long seed : new long[]{25L, 26L, 27L}) {
            GameState s = game(seed);
            for (int seat = 0; seat < 4; seat++) {
                assertEquals(1, s.player(seat).containers,
                    "сид " + seed + ", место " + (seat + 1)
                        + ": стартовый контейнер получают все и ровно один");
            }
        }
    }

    @Test
    void shuttlingBetweenTwoContainerCellsGivesNothing() {
        // Правило дизайнера: вышел С контейнерной ячейки и пришёл НА
        // контейнерную — карту не получаешь. Маятник «туда-сюда» бесполезен.
        GameState s = game(28L);
        PlayerState p = s.player(0);
        List<Hex> ground = new ArrayList<>();
        for (Hex h : s.field.hexes.values()) {
            if (PrintedContainers.groundContainerFree(s, h)) {
                ground.add(h);
            }
            if (ground.size() == 2) {
                break;
            }
        }
        assertEquals(2, ground.size(), "нашлись две контейнерные ячейки");
        Hex a = ground.get(0);
        Hex b = ground.get(1);

        Hex plain = null;
        for (Hex h : s.field.hexes.values()) {
            if (h.containerCell < 0 && h.kind != HexKind.FORBIDDEN && h.spawnTile == null) {
                plain = h;
                break;
            }
        }
        assertTrue(plain != null, "нашёлся гекс без печатного контейнера");

        assertEquals(1, PrintedContainers.onUnitMoved(s, p, plain.id, a.id, UnitType.INFANTRY, false),
            "пришёл с обычной ячейки на контейнерную — карта есть");
        assertEquals(0, PrintedContainers.onUnitMoved(s, p, a.id, b.id, UnitType.INFANTRY, false),
            "с контейнера на контейнер — карты нет");
        assertEquals(0, PrintedContainers.onUnitMoved(s, p, b.id, a.id, UnitType.INFANTRY, false),
            "и обратно тоже нет: маятник не работает");
        assertEquals(1, PrintedContainers.onUnitMoved(s, p, plain.id, b.id, UnitType.INFANTRY, false),
            "а вот заход с обычной ячейки снова даёт карту");
    }

    @Test
    void minerSeesContainersOnItsOwnHexAndOnNeighbours() {
        // Правило дизайнера: добытчик берёт контейнер, только если тот нарисован
        // и ОТКРЫТ на его гексе либо на примыкающем стенкой.
        GameState s = game(29L);
        Hex withContainer = null;
        for (Hex h : s.field.hexes.values()) {
            if (PrintedContainers.visibleContainer(s, h)) {
                withContainer = h;
                break;
            }
        }
        assertTrue(withContainer != null, "на поле есть открытые контейнеры");

        // стоя на самом гексе — видит его
        kelium.core.BuildingToken here = s.tokenStats.makeBuilding(
            kelium.core.BuildingType.MINER, 0, 9200, 2);
        here.hexId = withContainer.id;
        assertEquals(withContainer.id, PrintedContainers.minableContainerHex(s, here));

        // стоя на соседнем — видит ТОЛЬКО через свою стенку (правило дизайнера)
        Hex nbHex = null;
        int facing = -1;
        for (String nb : s.field.neighbors(withContainer.id)) {
            Hex cand = s.field.get(nb);
            for (int i = 0; i < 6; i++) {
                if (withContainer.id.equals(cand.neighborBySide[i])
                        && cand.sideOwner[i] == null) {
                    nbHex = cand;
                    facing = i;
                    break;
                }
            }
            if (nbHex != null) {
                break;
            }
        }
        assertTrue(nbHex != null, "нашёлся сосед со свободной стенкой к контейнеру");
        kelium.core.BuildingToken next = s.tokenStats.makeBuilding(
            kelium.core.BuildingType.MINER, 0, 9201, 2);
        next.hexId = nbHex.id;
        nbHex.occupySides(next.uid, java.util.List.of(facing));
        assertEquals(withContainer.id, PrintedContainers.minableContainerHex(s, next),
            "с соседнего гекса контейнер добывается, если добытчик к нему повёрнут");

        // накрыли ячейку зданием — контейнер больше не виден
        if (withContainer.containerCell != BlockStamp.AIR) {
            withContainer.sideOwner[withContainer.containerCell] = 4242;
            assertFalse(PrintedContainers.visibleContainer(s, withContainer),
                "накрытый жетоном контейнер не виден и не добывается");
        }
    }

    @Test
    void noContainerTokensAreEverPlacedOnTheFieldDuringAGame() {
        GameState s = game(26L);
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            agents.add(new RandomAgent(seat, new Random(seat + 5)));
        }
        GameEngine.playGame(s, agents, ev -> { });
        for (Hex h : s.field.hexes.values()) {
            assertFalse(h.containerCell == -2, "поле не знает про жетоны контейнеров");
        }
    }
}
