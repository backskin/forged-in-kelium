package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;
import kelium.engine.TokenContainers;

/**
 * СТАРЫЙ РЕЖИМ КОНТЕЙНЕРОВ — ЖЕТОНЫ НА ГЕКСАХ (правила 1.6.0-c1, временный
 * откат по просьбе дизайнера 12.08.2026, чтобы измерить, насколько «Контейнеры
 * 2.0» меняют игру).
 *
 * <p>Проверяем ровно диктовку: раскладка при подготовке по пустым гексам,
 * печатные ячейки погашены, сбор только войском, стройка жетон сжигает, в
 * Обновление жетоны падают на гексы без жетонов игроков и без тайлов.
 */
class TokenContainersTest {

    private static GameState game(String rules, long seed) {
        return Setup.buildGame(GameConfig.buildCached(rules, 4, seed, null, null));
    }

    @Test
    void setupLaysTokensOnEveryCompletelyEmptyHexAndKillsPrintedCells() {
        GameState s = game("1.6.0-c1", 31L);
        assertTrue(TokenContainers.enabled(s), "режим жетонов включён версией правил");
        int tokens = 0;
        for (Hex h : s.field.hexes.values()) {
            assertEquals(-1, h.containerCell,
                "печатные ячейки в этом режиме погашены: " + h.id);
            boolean empty = !h.hasSpawnTile() && !h.hasNeutral()
                && h.groundTokens.isEmpty() && h.airToken == null;
            assertEquals(empty ? 1 : 0, h.containerTokens,
                "гекс " + h.id + (empty ? " пуст — жетон обязателен" : " занят — жетона быть не должно"));
            tokens += h.containerTokens;
        }
        assertTrue(tokens > 0, "жетоны должны лечь хоть куда-то");
    }

    @Test
    void printedModeLaysNoTokens() {
        GameState s = game("1.6.0", 31L);
        assertFalse(TokenContainers.enabled(s), "в основном режиме жетонов нет");
        assertEquals(0, TokenContainers.onField(s), "поле без жетонов контейнеров");
    }

    @Test
    void onlyTroopsCollectAndBuildingsBurnTheToken() {
        GameState s = game("1.6.0-c1", 32L);
        Hex withToken = null;
        for (Hex h : s.field.hexes.values()) {
            if (h.containerTokens > 0) {
                withToken = h;
                break;
            }
        }
        assertTrue(withToken != null, "нужен гекс с жетоном");

        PlayerState p = s.player(0);
        int before = p.containers;

        // СТРОЙКА сжигает жетон: игрок ничего не получает
        BuildingToken b = s.tokenStats.makeBuilding(BuildingType.MINER, 0, 7001, 1);
        b.hexId = withToken.id;
        p.buildings.add(b);
        assertTrue(TokenContainers.onBuildingPlaced(s, b), "жетон сгорел под стройкой");
        assertEquals(0, withToken.containerTokens, "жетона на гексе больше нет");
        assertEquals(before, p.containers, "стройка контейнер НЕ даёт");

        // ВОЙСКО подбирает жетон
        Hex other = null;
        for (Hex h : s.field.hexes.values()) {
            if (h.containerTokens > 0) {
                other = h;
                break;
            }
        }
        assertTrue(other != null, "нужен второй гекс с жетоном");
        int got = TokenContainers.onUnitEntered(s, p, other.id);
        assertEquals(1, got, "войско забрало контейнер");
        assertEquals(0, other.containerTokens, "жетон ушёл с гекса");
        assertEquals(before + 1, p.containers, "контейнер лёг игроку");

        // на пустом гексе брать нечего
        assertEquals(0, TokenContainers.onUnitEntered(s, p, other.id),
            "второй раз с того же гекса ничего не берётся");
    }

    @Test
    void refreshDropsTokensWhereNoPlayerTokensAndNoSpawnTile() {
        GameState s = game("1.6.0-c1", 33L);
        // расчистим поле: снимем все жетоны контейнеров, чтобы Обновление их вернуло
        for (Hex h : s.field.hexes.values()) {
            h.containerTokens = 0;
        }
        int laid = TokenContainers.layoutOnRefresh(s);
        assertTrue(laid > 0, "в Обновление жетоны должны появиться");
        for (Hex h : s.field.hexes.values()) {
            boolean free = !h.hasSpawnTile() && h.groundTokens.isEmpty() && h.airToken == null;
            assertEquals(free ? 1 : 0, h.containerTokens,
                "гекс " + h.id + ": нейтралы жетону не мешают, а жетоны игроков и тайлы — мешают");
        }
    }

    /** В этом режиме контейнеров становится СУЩЕСТВЕННО меньше — это и мерили. */
    @Test
    void tokenModeGivesFarFewerContainersThanPrintedCells() {
        int printed = openedContainers("1.6.0", 6);
        int tokens = openedContainers("1.6.0-c1", 6);
        assertTrue(tokens * 2 < printed,
            "печатные ячейки дают в разы больше контейнеров: печатные " + printed
                + ", жетоны " + tokens);
    }

    private static int openedContainers(String rules, int games) {
        int total = 0;
        for (int g = 0; g < games; g++) {
            long seed = 5100 + g;
            GameState s = game(rules, seed);
            List<kelium.core.Agent> agents = new java.util.ArrayList<>();
            for (int seat = 0; seat < 4; seat++) {
                agents.add(kelium.agents.Bots.create("balanced", seat,
                    new java.util.Random(seed * 31 + seat), 4));
            }
            int[] opened = {0};
            kelium.engine.GameEngine.playGame(s, agents, ev -> {
                if ("container".equals(ev.get("type"))) {
                    opened[0]++;
                }
            });
            total += opened[0];
        }
        return total;
    }
}
