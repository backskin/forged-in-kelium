package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.BlockStamp;
import kelium.engine.PrintedContainers;
import kelium.engine.Setup;

/**
 * СТОРОЖ ПРАВИЛА ДОБЫЧИ КОНТЕЙНЕРА (дизайнер, 16.09.2026, дословно):
 *
 * <p>«Добытчик добывает контейнер, ЕСЛИ ТОТ НАПЕЧАТАН НА СОСЕДНЕМ С ДОБЫТЧИКОМ
 * СЕКТОРЕ. И НЕ ВОЗДУШНОМ. И НЕ ПРОСТО НА ГЕКСЕ. ПЛЮС ОН МОЖЕТ БЫТЬ НА
 * СОСЕДНЕМ ГЕКСЕ».
 *
 * <p>Почему сторож нужен. До 16.09.2026 движок отдавал контейнер с ЛЮБОГО
 * сектора своего гекса (добытчик дотягивался через весь гекс) и вдобавок
 * допускал воздушную ячейку. Ошибка не видна ни в партии, ни в тестах — она
 * просто раздувает поток контейнеров, и книга правил повторяла её словами.
 * Каждая из четырёх проверок ниже ломается, если правило откатят.
 */
class ДобычаКонтейнераTest {

    private static GameState стол() {
        return Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 4242L, null, null));
    }

    /** Гекс, на котором нет ни одного жетона и ни тайла, ни нейтрала. */
    private static Hex чистыйГекс(GameState s, int мимо) {
        for (Hex h : s.field.hexes.values()) {
            if (h.kind == kelium.core.HexKind.FORBIDDEN || h.hasSpawnTile()
                    || h.hasNeutral() || !h.groundTokens.isEmpty()) {
                continue;
            }
            boolean занят = false;
            for (int i = 0; i < 6; i++) {
                занят |= h.sideOwner[i] != null;
            }
            if (!занят && мимо-- <= 0) {
                return h;
            }
        }
        throw new IllegalStateException("на поле не нашлось чистого гекса");
    }

    /** Поставить добытчик игрока на сектор {@code сектор} этого гекса. */
    private static BuildingToken добытчик(GameState s, Hex h, int сектор) {
        // СВОД setup.start_miner = false: на подготовке добытчиков на поле
        // нет вовсе — все четыре лежат на планшете хранилища и жетонами
        // движка становятся только после Стройки. Значит жетон для
        // сторожа создаётся здесь, а не берётся у игрока.
        PlayerState p = s.player(0);
        BuildingToken b =
            s.tokenStats.makeBuilding(BuildingType.MINER, p.seat, 9000 + сектор, 1);
        p.buildings.add(b);
        b.hexId = h.id;
        h.sideOwner[сектор] = b.uid;
        return b;
    }

    @Test
    void соседнийСекторСвоегоГексаДобывается() {
        GameState s = стол();
        Hex h = чистыйГекс(s, 0);
        BuildingToken b = добытчик(s, h, 0);
        h.containerCell = 1;                  // сосед по кругу с сектором 0
        h.containerTaken = false;
        assertEquals(h.id, PrintedContainers.minableContainerHex(s, b),
            "контейнер на соседнем с добытчиком секторе обязан добываться");
    }

    @Test
    void дальнийСекторСвоегоГексаНеДобывается() {
        GameState s = стол();
        Hex h = чистыйГекс(s, 0);
        BuildingToken b = добытчик(s, h, 0);
        h.containerCell = 3;                  // напротив добытчика, не сосед
        h.containerTaken = false;
        assertNull(PrintedContainers.minableContainerHex(s, b),
            "через весь гекс добытчик не дотягивается: сектор 3 добытчику "
                + "на секторе 0 не соседний");
    }

    @Test
    void воздушнаяЯчейкаНеДобывается() {
        GameState s = стол();
        Hex h = чистыйГекс(s, 0);
        BuildingToken b = добытчик(s, h, 0);
        h.containerCell = BlockStamp.AIR;
        h.containerTaken = false;
        h.airToken = null;
        assertNull(PrintedContainers.minableContainerHex(s, b),
            "воздушный контейнер добытчику не достаётся");
    }

    @Test
    void соседнийГексДобываетсяТолькоЧерезСмотрящийСектор() {
        GameState s = стол();
        Hex h = чистыйГекс(s, 0);
        int сторона = -1;
        Hex сосед = null;
        for (int i = 0; i < 6; i++) {
            String nb = h.neighborBySide[i];
            if (nb == null) {
                continue;
            }
            Hex cand = s.field.get(nb);
            if (cand == null || cand.hasSpawnTile() || cand.hasNeutral()
                    || !cand.groundTokens.isEmpty()
                    || cand.kind == kelium.core.HexKind.FORBIDDEN) {
                continue;
            }
            boolean занят = false;
            for (int k = 0; k < 6; k++) {
                занят |= cand.sideOwner[k] != null;
            }
            if (!занят) {
                сторона = i;
                сосед = cand;
                break;
            }
        }
        if (сосед == null) {
            return;                           // такой пары на поле нет — нечего мерить
        }
        BuildingToken b = добытчик(s, h, сторона);
        h.containerCell = -1;

        // сектор соседа, смотрящий на добытчика, — добывается
        сосед.containerCell = (сторона + 3) % 6;
        сосед.containerTaken = false;
        assertEquals(сосед.id, PrintedContainers.minableContainerHex(s, b),
            "контейнер на секторе соседа, смотрящем на добытчика, добывается");

        // дальний сектор того же соседа — нет
        сосед.containerCell = сторона;
        assertNull(PrintedContainers.minableContainerHex(s, b),
            "дальний сектор соседнего гекса добытчику не достаётся");
    }
}
