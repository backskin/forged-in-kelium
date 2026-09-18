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
 * СТОРОЖ ПРАВИЛА ДОБЫЧИ КОНТЕЙНЕРА (дизайнер, 12.08.2026, возвращено
 * 18.09.2026): добытчик берёт ОТКРЫТЫЙ контейнер со своего гекса, а если своего
 * нет — с любого гекса, к которому стоит стенкой. Сектор контейнера не смотрят,
 * воздушная ячейка годится наравне с наземными.
 *
 * <p>Почему сторож нужен и почему он переписан. 16.09.2026 правило сузили до
 * «контейнер на СОСЕДНЕМ с добытчиком секторе, и не воздушном», и сторож
 * охранял именно это. Онлайн-тест 18.09.2026 сужение отменил — дизайнер
 * словами: «отмена была зря, старая версия правила будет лучше». Проверки ниже
 * теперь ловят обратное: если узкое правило вернут в движок молча, дальний
 * сектор и воздушная ячейка перестанут добываться и тесты упадут.
 *
 * <p>Что сторож охраняет по-прежнему: добытчик НЕ дотягивается до гекса, с
 * которым не граничит, и не берёт контейнер, НАКРЫТЫЙ жетоном.
 */
class ДобычаКонтейнераTest {

    /**
     * Стол с ПУСТЫМ ПОЛЕМ ПО КОНТЕЙНЕРАМ: все печатные ячейки сняты, и каждый
     * тест рисует ровно тот контейнер, который проверяет.
     *
     * <p>Иначе сторож меряет не правило, а раскладку: добытчик по возвращённому
     * правилу дотягивается до ЛЮБОГО соседнего гекса, а на настоящем поле
     * контейнеры напечатаны почти всюду — и «нечего брать» не наступает никогда.
     */
    private static GameState стол() {
        GameState s = Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 4242L, null, null));
        for (Hex h : s.field.hexes.values()) {
            h.containerCell = -1;
            h.containerTaken = false;
        }
        return s;
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
    void любойСекторСвоегоГексаДобывается() {
        GameState s = стол();
        Hex h = чистыйГекс(s, 0);
        BuildingToken b = добытчик(s, h, 0);
        h.containerTaken = false;
        for (int сектор : new int[] {1, 2, 3, 4, 5}) {
            h.containerCell = сектор;
            assertEquals(h.id, PrintedContainers.minableContainerHex(s, b),
                "добытчик берёт контейнер с ЛЮБОГО сектора своего гекса, "
                    + "в том числе дальнего (сектор " + сектор + ")");
        }
    }

    @Test
    void воздушнаяЯчейкаСвоегоГексаДобывается() {
        GameState s = стол();
        Hex h = чистыйГекс(s, 0);
        BuildingToken b = добытчик(s, h, 0);
        h.containerCell = BlockStamp.AIR;
        h.containerTaken = false;
        h.airToken = null;
        assertEquals(h.id, PrintedContainers.minableContainerHex(s, b),
            "воздушная ячейка добывается наравне с наземными: запрет "
                + "16.09.2026 отменён");
    }

    @Test
    void накрытыйКонтейнерНеДобывается() {
        GameState s = стол();
        Hex h = чистыйГекс(s, 0);
        BuildingToken b = добытчик(s, h, 0);
        h.containerCell = 0;                  // ровно под самим добытчиком
        h.containerTaken = false;
        assertNull(PrintedContainers.minableContainerHex(s, b),
            "накрытый жетоном контейнер для Добычи всё равно что отсутствует");
    }

    @Test
    void соседнийГексДобываетсяСЛюбогоСектора() {
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
        h.containerCell = -1;                 // на своём гексе брать нечего
        сосед.containerTaken = false;

        сосед.containerCell = (сторона + 3) % 6;   // смотрит на добытчика
        assertEquals(сосед.id, PrintedContainers.minableContainerHex(s, b),
            "контейнер на соседнем гексе добывается");
        сосед.containerCell = сторона;             // дальний сектор того же соседа
        assertEquals(сосед.id, PrintedContainers.minableContainerHex(s, b),
            "сектор соседнего гекса не важен: добытчик стоит к гексу стенкой");
    }

    @Test
    void чужойГексНеДобывается() {
        GameState s = стол();
        Hex свой = чистыйГекс(s, 0);
        BuildingToken b = добытчик(s, свой, 0);
        свой.containerCell = -1;
        // Гекс, с которым добытчик НЕ граничит: берём дальний чистый и убеждаемся,
        // что он не сосед. Дотянуться до него добытчик не должен ни при каких
        // секторах — иначе правило «плюс соседний гекс» ничего не ограничивает.
        for (int мимо = 1; мимо < 12; мимо++) {
            Hex далёкий = чистыйГекс(s, мимо);
            boolean сосед = false;
            for (int i = 0; i < 6; i++) {
                сосед |= далёкий.id.equals(свой.neighborBySide[i]);
            }
            if (сосед) {
                continue;
            }
            далёкий.containerCell = 2;
            далёкий.containerTaken = false;
            assertNull(PrintedContainers.minableContainerHex(s, b),
                "до гекса, с которым добытчик не граничит, он не дотягивается");
            далёкий.containerCell = -1;
            return;
        }
    }
}
