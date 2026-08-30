package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Power;
import kelium.engine.Setup;
import kelium.support.Fix;

/**
 * ЖЁЛТАЯ ЯЧЕЙКА (правило дизайнера 16.08.2026).
 *
 * <p>На каждом гексе картонного блока напечатана одна НАЗЕМНАЯ ячейка жёлтым
 * цветом, отдельная от ячейки с печатным контейнером. Энергостанция выдаёт свой
 * номинал по уровню (1·2·2·3) ТОЛЬКО стоя на ней; на любой другой ячейке она
 * даёт ровно 1 кубик, каким бы ни был её уровень.
 *
 * <p>Тесты закрывают правило с трёх сторон: разметка поля, само число выработки
 * и то, что число не расходится между стройкой и Сменой энергии (там оно
 * считалось двумя разными выражениями, и это уже расходилось раньше).
 */
class EnergyCellRuleTest {

    /**
     * Разметка поля под набором 1.4.0: жёлтая ячейка есть НЕ на каждом гексе
     * (пустые гексы блока не несут ни её, ни контейнера), но там, где есть, —
     * всегда наземная и не та же, что контейнерная. Размеченных гексов при
     * этом большинство: 3 из 5 на малом блоке, 4 из 6 на большом.
     */
    @Test
    void markedHexesCarryAGroundEnergyCellApartFromTheContainer() {
        GameState s = Fix.game();
        int playable = 0;
        int marked = 0;
        for (Hex h : s.field.hexes.values()) {
            if (h.kind == HexKind.FORBIDDEN || h.hasSpawnTile()) {
                continue;   // на такие гексы жетоны не встают, картон под ними накрыт
            }
            playable++;
            if (h.energyCell < 0) {
                continue;   // пустой гекс блока — без жёлтой ячейки, это законно
            }
            assertTrue(h.energyCell < 6,
                "гекс " + h.id + ": жёлтая ячейка обязана быть НАЗЕМНОЙ (0..5), "
                + "а не " + h.energyCell);
            assertNotEquals(h.containerCell, h.energyCell,
                "гекс " + h.id + ": жёлтая ячейка не может совпадать с контейнерной");
            marked++;
        }
        assertTrue(marked > 0, "поле не размечено ни одной жёлтой ячейкой");
        assertTrue(marked >= playable / 2,
            "жёлтых ячеек должно быть большинство: " + marked + " из " + playable);
    }

    /** Станция на жёлтой ячейке даёт номинал своего уровня. */
    @Test
    void plantOnTheYellowCellGivesItsNominal() {
        GameState s = Fix.game();
        String hex = openHex(s);
        Hex h = s.field.get(hex);
        BuildingToken plant = plantAt(s, hex, h.energyCell, 4);

        assertTrue(Power.onEnergyCell(s, plant), "станция поставлена ровно на жёлтую ячейку");
        assertEquals(s.tokenStats.plantEnergyGives(4), Power.plantOutput(s, plant),
            "на жёлтой ячейке станция №4 обязана давать свой полный номинал");
    }

    /** Станция мимо жёлтой ячейки даёт 1 кубик — независимо от уровня. */
    @Test
    void plantOffTheYellowCellGivesOneCubeWhateverItsLevel() {
        GameState s = Fix.game();
        for (int level = 1; level <= 4; level++) {
            String hex = openHex(s);
            Hex h = s.field.get(hex);
            int wrong = (h.energyCell + 1) % 6;
            BuildingToken plant = plantAt(s, hex, wrong, level);

            assertFalse(Power.onEnergyCell(s, plant),
                "станция №" + level + " стоит НЕ на жёлтой ячейке");
            assertEquals(1, Power.plantOutput(s, plant),
                "станция №" + level + " вне жёлтой ячейки обязана давать 1 кубик, "
                + "а номинал уровня (" + s.tokenStats.plantEnergyGives(level) + ") — только на ней");
        }
    }

    /**
     * Станция №1 и так даёт 1 кубик — правило не может дать БОЛЬШЕ номинала.
     * Проверка отдельная: «не больше номинала» легко потерять, если написать
     * правило как «вне ячейки ровно значение из свода».
     */
    @Test
    void theRuleNeverRaisesOutputAboveTheNominal() {
        GameState s = Fix.game();
        String hex = openHex(s);
        Hex h = s.field.get(hex);
        BuildingToken weak = plantAt(s, hex, (h.energyCell + 1) % 6, 1);
        assertEquals(s.tokenStats.plantEnergyGives(1), Power.plantOutput(s, weak),
            "станция №1 даёт свой номинал и вне жёлтой ячейки — правило только режет");
    }

    /**
     * Неразмеченный гекс (ручная сцена, поле без набора блоков) правилу не
     * подчиняется: молча резать выработку там, где жёлтой ячейки не напечатано,
     * значило бы менять правило отсутствием данных.
     */
    @Test
    void anUnmarkedHexKeepsTheNominal() {
        GameState s = Fix.game();
        String hex = openHex(s);
        Hex h = s.field.get(hex);
        h.energyCell = -1;
        BuildingToken plant = plantAt(s, hex, 0, 4);
        assertEquals(s.tokenStats.plantEnergyGives(4), Power.plantOutput(s, plant),
            "гекс без жёлтой ячейки — правило не применяется");
    }

    /** ЦУ правилу не подчиняется: оно сам себе источник и потребитель. */
    @Test
    void theCommandCentreIsNotSubjectToTheRule() {
        GameState s = Fix.game();
        String hex = openHex(s);
        Hex h = s.field.get(hex);
        BuildingToken cu = s.tokenStats.makeBuilding(BuildingType.COMMAND_CENTER, 0,
            9500, null);
        cu.hexId = hex;
        h.occupySides(cu.uid, List.of((h.energyCell + 1) % 6));
        s.player(0).buildings.add(cu);
        assertEquals(s.tokenStats.buildingEnergyGives(BuildingType.COMMAND_CENTER),
            Power.plantOutput(s, cu), "ЦУ отдаёт свои кубики с любой ячейки");
    }

    /**
     * ИНВАРИАНТ ЖИВОЙ ПАРТИИ: у станции в обороте ровно столько кубиков, сколько
     * она выдаёт по правилу — ни одним больше.
     *
     * <p>Зачем целая партия, когда есть модульные проверки выше. Выработка
     * считается в ДВУХ местах: стройка кладёт кубики на станцию, а Смена энергии
     * снимает их и раскладывает пул заново. Расходятся такие пары молча — это
     * ровно тот способ, которым в движке уже разъезжались правила. Инвариант
     * ловит расхождение независимо от того, в каком из мест его допустили.
     */
    @Test
    void acrossWholeGamesNoPlantEverCirculatesMoreCubesThanItGives() {
        int plantsSeen = 0;
        for (long seed : new long[] {11L, 12L, 13L}) {
            GameState s = Setup.buildGame(
                GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 3, seed, null, null));
            List<kelium.core.Agent> agents = new java.util.ArrayList<>();
            for (int seat = 0; seat < s.numPlayers(); seat++) {
                agents.add(new Fix.FirstChoiceAgent(seat));
            }
            new GameEngine(s, agents, ev -> { }).run();

            for (int seat = 0; seat < s.numPlayers(); seat++) {
                kelium.core.PlayerState p = s.player(seat);
                int bonus = kelium.engine.Passives.plantEnergyBonus(s, seat);
                for (BuildingToken plant : p.buildingsOnField()) {
                    if (plant.type != BuildingType.POWER_PLANT) {
                        continue;
                    }
                    plantsSeen++;
                    int inPlay = plant.energyIdle;
                    for (BuildingToken c : p.buildingsOnField()) {
                        inPlay += c.energyBySource.getOrDefault(plant.uid, 0);
                    }
                    int allowed = Power.plantOutput(s, plant) + bonus;
                    assertTrue(inPlay <= allowed,
                        "сид " + seed + ", место " + seat + ": станция №" + plant.level
                        + " на гексе " + plant.hexId + " держит в обороте " + inPlay
                        + " кубиков, а по правилу даёт " + allowed
                        + (Power.onEnergyCell(s, plant) ? " (на жёлтой ячейке)"
                            : " (ВНЕ жёлтой ячейки)"));
                }
            }
        }
        assertTrue(plantsSeen > 0,
            "ни в одной партии не построено ни одной энергостанции — инвариант "
            + "ничего не сторожит, сцену надо чинить, а не оставлять зелёной");
    }

    /** Поставить игроку 0 станцию уровня level ровно на ячейку cell гекса hex. */
    private static BuildingToken plantAt(GameState s, String hex, int cell, int level) {
        Hex h = s.field.get(hex);
        BuildingToken b = s.tokenStats.makeBuilding(BuildingType.POWER_PLANT, 0,
            9600 + cell + level * 10, level);
        b.hexId = hex;
        assertTrue(h.occupySides(b.uid, List.of(cell)),
            "ячейка " + cell + " гекса " + hex + " должна быть свободна");
        s.player(0).buildings.add(b);
        return b;
    }

    /** Пустой размеченный гекс: все шесть наземных ячеек свободны. */
    private static String openHex(GameState s) {
        for (Hex h : s.field.hexes.values()) {
            if (h.kind == HexKind.NORMAL && !h.hasSpawnTile() && !h.hasNeutral()
                    && h.energyCell >= 0 && h.freeSectors() == 6) {
                return h.id;
            }
        }
        throw new IllegalStateException("на поле нет пустого размеченного гекса");
    }
}
