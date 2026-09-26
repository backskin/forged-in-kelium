package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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


    /**
     * НЕТ ЛИ НА ГЕКСЕ ЖЕТОНОВ ИГРОКОВ. Именно этого требует правило выдачи
     * («контейнер получает тот, кто пришёл на ЧИСТЫЙ гекс»), и именно этого не
     * проверяет visibleContainer: он смотрит, чем ячейка НАКРЫТА, а войска
     * ячеек не накрывают — они просто стоят на гексе.
     */
    private static boolean чистыйГекс(GameState s, Hex h) {
        for (int seat = 0; seat < s.numPlayers(); seat++) {
            PlayerState ps = s.player(seat);
            for (kelium.core.UnitToken u : ps.unitsOnField()) {
                if (h.id.equals(u.hexId)) {
                    return false;
                }
            }
            for (kelium.core.BuildingToken b : ps.buildingsOnField()) {
                if (h.id.equals(b.hexId)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Найти поле, где есть свободные гексы с открытым наземным и воздушным контейнером. */
    private static Object[] полеСКонтейнерами() {
        for (long seed = 23; seed < 90; seed++) {
            GameState s = game(seed);
            List<Hex> наземные = new ArrayList<>();
            Hex небо = null;
            for (Hex h : s.field.hexes.values()) {
                if (!PrintedContainers.visibleContainer(s, h) || !чистыйГекс(s, h)) {
                    continue;
                }
                if (h.containerCell != BlockStamp.AIR) {
                    наземные.add(h);
                } else if (небо == null) {
                    небо = h;
                }
            }
            if (наземные.size() >= 2 && небо != null) {
                return new Object[]{s, наземные.get(0), наземные.get(1), небо};
            }
        }
        throw new AssertionError("ни на одном поле нет нужных открытых ячеек");
    }

    private static kelium.core.UnitToken поставить(GameState s, PlayerState p, UnitType t,
                                                   Hex h, int uid) {
        kelium.core.UnitToken u = s.tokenStats.makeUnit(t, p.seat, uid);
        p.units.add(u);
        переставить(u, null, h);
        return u;
    }

    private static void переставить(kelium.core.UnitToken u, Hex из, Hex в) {
        if (из != null) {
            if (u.type == UnitType.AIRCRAFT) {
                из.airToken = null;
            } else {
                из.groundTokens.remove(Integer.valueOf(u.uid));
            }
        }
        u.hexId = в == null ? null : в.id;
        if (в != null) {
            if (u.type == UnitType.AIRCRAFT) {
                в.airToken = u.uid;
            } else {
                в.groundTokens.add(u.uid);
            }
        }
    }

    /**
     * НАКРЫТИЕ ЗА ДЕЙСТВИЕ (решение дизайнера 23.09.2026): открыт до действия и
     * закрыт жетоном игрока после — контейнер ему. Наземный жетон закрывает
     * наземную ячейку, авиация — воздушную.
     */
    @Test
    void накрытиеЗаДействиеНаземноеИВоздушное() {
        Object[] f = полеСКонтейнерами();
        GameState s = (GameState) f[0];
        Hex земля = (Hex) f[1];
        Hex небо = (Hex) f[3];
        PlayerState p = s.player(0);
        assertTrue(PrintedContainers.поДействию(s), "в своде действует накрытие за действие");

        java.util.Set<String> до = PrintedContainers.открытые(s);
        поставить(s, p, UnitType.INFANTRY, земля, 9301);
        поставить(s, p, UnitType.INFANTRY, небо, 9302);     // пехота воздушную не закрывает
        assertEquals(1, PrintedContainers.накрытия(s, p, до),
            "пехота закрыла наземный контейнер — одна карта, воздушный остался открыт");
        assertTrue(PrintedContainers.visibleContainer(s, небо), "воздушный контейнер открыт");

        до = PrintedContainers.открытые(s);
        поставить(s, p, UnitType.AIRCRAFT, небо, 9303);
        assertEquals(1, PrintedContainers.накрытия(s, p, до), "авиация закрыла воздушный");
    }

    /**
     * ДЁРГАНИЯ ВНУТРИ ДЕЙСТВИЯ НЕ СЧИТАЮТСЯ: сколько раз ни ставь и ни снимай —
     * одна карта за действие; а если ячейка до действия уже была закрыта —
     * ничего.
     */
    @Test
    void дёрганияВнутриДействияНеСчитаются() {
        Object[] f = полеСКонтейнерами();
        GameState s = (GameState) f[0];
        Hex земля = (Hex) f[1];
        PlayerState p = s.player(0);

        java.util.Set<String> до = PrintedContainers.открытые(s);
        kelium.core.UnitToken u = поставить(s, p, UnitType.INFANTRY, земля, 9311);
        переставить(u, земля, null);
        переставить(u, null, земля);
        assertEquals(1, PrintedContainers.накрытия(s, p, до), "одна карта за действие");

        до = PrintedContainers.открытые(s);
        переставить(u, земля, null);
        переставить(u, null, земля);
        assertEquals(0, PrintedContainers.накрытия(s, p, до),
            "до действия ячейка была закрыта — снять и вернуть ничего не даёт");
    }

    /**
     * С КОНТЕЙНЕРА НА КОНТЕЙНЕР — МОЖНО: сошёл с одного и накрыл другой на
     * другом гексе — получил (решение дизайнера 23.09.2026, прежний запрет
     * отменён).
     */
    @Test
    void сКонтейнераНаКонтейнерДаётКарту() {
        Object[] f = полеСКонтейнерами();
        GameState s = (GameState) f[0];
        Hex a = (Hex) f[1];
        Hex b = (Hex) f[2];
        PlayerState p = s.player(0);
        java.util.Set<String> до = PrintedContainers.открытые(s);
        kelium.core.UnitToken u = поставить(s, p, UnitType.INFANTRY, a, 9321);
        assertEquals(1, PrintedContainers.накрытия(s, p, до));
        до = PrintedContainers.открытые(s);
        переставить(u, a, b);
        assertEquals(1, PrintedContainers.накрытия(s, p, до),
            "перешёл с контейнера на другой открытый — карта");
    }

    /**
     * СЕКТОР ВОЙСКА РЕШАЕТ (выбор сектора, 26.09.2026): войско, поставленное
     * игроком НЕ на ячейку контейнера, её не накрывает — контейнер остаётся
     * открытым; поставленное НА ячейку — накрывает, карта его.
     */
    @Test
    void контейнерБерётТолькоВойскоНаЕгоСекторе() {
        Object[] f = полеСКонтейнерами();
        GameState s = (GameState) f[0];
        Hex земля = (Hex) f[1];
        PlayerState p = s.player(0);
        int ячейка = земля.containerCell;
        int другой = -1;
        for (int i = 0; i < 6; i++) {
            if (i != ячейка && земля.sideOwner[i] == null) {
                другой = i;
                break;
            }
        }
        assertTrue(другой >= 0, "на гексе нашёлся другой свободный сектор");

        java.util.Set<String> до = PrintedContainers.открытые(s);
        kelium.core.UnitToken u = s.tokenStats.makeUnit(UnitType.INFANTRY, p.seat, 9341);
        p.units.add(u);
        u.hexId = земля.id;
        u.chooseSides(List.of(другой));
        assertTrue(PrintedContainers.visibleContainer(s, земля),
            "пехота на соседнем секторе ячейку не накрыла");
        assertEquals(0, PrintedContainers.накрытия(s, p, до), "карты нет");

        до = PrintedContainers.открытые(s);
        u.chooseSides(List.of(ячейка));
        assertFalse(PrintedContainers.visibleContainer(s, земля), "ячейка накрыта пехотой");
        assertEquals(1, PrintedContainers.накрытия(s, p, до), "пехота встала на контейнер");
    }

    /** Бот ставит войско сам — на свободный контейнер, как поставил бы человек. */
    @Test
    void ботСтавитВойскоНаКонтейнер() {
        Object[] f = полеСКонтейнерами();
        GameState s = (GameState) f[0];
        Hex земля = (Hex) f[1];
        PlayerState p = s.player(0);
        kelium.core.UnitToken u = s.tokenStats.makeUnit(UnitType.INFANTRY, p.seat, 9351);
        p.units.add(u);
        u.hexId = земля.id;
        kelium.engine.СекторыВойск.поставить(s, null, u);
        assertEquals(List.of(земля.containerCell), u.chosenSides());
    }

    @Test
    void никакихСтартовыхКонтейнеров() {
        // ПРАВИЛО ДИЗАЙНЕРА 13.09.2026: «карты контейнера на старте нет».
        // Прежняя раздача (по одной каждому) была тестовой и расходилась с
        // книгой правил — ключ setup.start_containers теперь ноль.
        for (long seed : new long[]{25L, 26L, 27L}) {
            GameState s = game(seed);
            for (int seat = 0; seat < 4; seat++) {
                assertEquals(0, s.player(seat).containers,
                    "сид " + seed + ", место " + (seat + 1)
                        + ": на подготовке карт контейнера не выдают");
            }
        }
    }

    @Test
    void minerSeesContainersOnItsOwnHexAndOnNeighbours() {
        // ПРАВИЛО ДИЗАЙНЕРА (12.08.2026, возвращено 18.09.2026): добытчик берёт
        // ОТКРЫТЫЙ контейнер со своего гекса, а если своего нет — с гекса,
        // к которому стоит стенкой. Сектор контейнера не смотрят вовсе.
        //
        // 16.09.2026 правило сужали до «сектор контейнера соседний с сектором
        // добытчика», и этот тест охранял сужение. Онлайн-тест 18.09.2026 его
        // отменил, поэтому проверки перевёрнуты: теперь дальний сектор ОБЯЗАН
        // добываться. Что осталось неизменным — накрытый контейнер не виден.
        GameState s = null;
        Hex withContainer = null;
        for (long seed = 29; seed < 90 && withContainer == null; seed++) {
            s = game(seed);
            for (Hex h : s.field.hexes.values()) {
                if (h.containerCell >= 0 && h.containerCell != BlockStamp.AIR
                        && PrintedContainers.visibleContainer(s, h)) {
                    withContainer = h;
                    break;
                }
            }
        }
        assertTrue(withContainer != null, "на поле есть открытый наземный контейнер");
        int cell = withContainer.containerCell;

        // СВОЙ ГЕКС, ЛЮБОЙ СЕКТОР — добывается, и соседний, и дальний.
        kelium.core.BuildingToken here = s.tokenStats.makeBuilding(
            kelium.core.BuildingType.MINER, 0, 9200, 2);
        here.hexId = withContainer.id;
        for (int сдвиг : new int[] {1, 2, 3}) {
            withContainer.sideOwner[(cell + сдвиг) % 6] = here.uid;
            assertEquals(withContainer.id, PrintedContainers.minableContainerHex(s, here),
                "на своём гексе добытчик дотягивается до любого сектора (сдвиг "
                    + сдвиг + ")");
            withContainer.sideOwner[(cell + сдвиг) % 6] = null;
        }

        // СОСЕДНИЙ ГЕКС — тоже любой сектор, важна только общая стенка. Сосед
        // берётся такой, у которого своего контейнера нет: иначе добытчик
        // увидит контейнер на своём гексе, и это будет верный ответ, а не ошибка.
        Hex nbHex = null;
        int facing = -1;
        for (String nb : s.field.neighbors(withContainer.id)) {
            Hex cand = s.field.get(nb);
            if (cand == null || PrintedContainers.visibleContainer(s, cand)) {
                continue;
            }
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
        withContainer.containerCell = (facing + 3) % 6;      // смотрит на добытчика
        assertEquals(withContainer.id, PrintedContainers.minableContainerHex(s, next),
            "с соседнего гекса контейнер добывается");
        withContainer.containerCell = ((facing + 3) % 6 + 2) % 6;   // дальний сектор
        assertEquals(withContainer.id, PrintedContainers.minableContainerHex(s, next),
            "сектор на соседнем гексе не важен: добытчик стоит к нему стенкой");

        // накрыли ячейку зданием — контейнер больше не виден
        withContainer.containerCell = cell;
        withContainer.sideOwner[cell] = 4242;
        assertFalse(PrintedContainers.visibleContainer(s, withContainer),
            "накрытый жетоном контейнер не виден и не добывается");
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
