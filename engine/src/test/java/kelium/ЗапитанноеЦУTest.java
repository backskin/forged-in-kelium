package kelium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;

/**
 * СТОРОЖ ПРАВИЛА «ЦУ ЗАПИТАНО ЛЮБЫМ КУБИКОМ НА СВОЁМ ЖЕТОНЕ»
 * (дизайнер, 17.09.2026).
 *
 * <p>ЦУ — один жетон, и источник, и потребитель. Зон на нём две — ячейка
 * энергии и свободная энергия, — но кубик и там и там лежит НА ЖЕТОНЕ.
 * Перекладывать его с ЦУ на само ЦУ, чтобы «вставить в ячейку», за столом
 * никто не станет: это движение из руки в ту же руку.
 *
 * <p>Почему сторож нужен. До 17.09.2026 {@code powered()} считал только кубики
 * в ЯЧЕЙКАХ, и ЦУ с тремя кубиками в свободной зоне числилось незапитанным —
 * Снаряжение им не игралось, пока игрок не отдаст кубик самому себе. В партии
 * это выглядело как поломка, а не как правило.
 */
class ЗапитанноеЦУTest {

    private static GameState стол() {
        return Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 4242L, null, null));
    }

    private static BuildingToken цу(GameState s) {
        PlayerState p = s.player(0);
        for (BuildingToken b : p.buildings) {
            if (b.type == BuildingType.COMMAND_CENTER) {
                return b;
            }
        }
        throw new IllegalStateException("у игрока нет ЦУ");
    }

    @Test
    void кубикВСвободнойЗонеЗапитываетЦУ() {
        GameState s = стол();
        BuildingToken cu = цу(s);
        // снимаем всё, что лежит в ячейке, и оставляем кубик только «на жетоне»
        cu.energyBySource.clear();
        cu.energyPlaced = 0;
        cu.energyIdle = 1;
        assertTrue(cu.powered(),
            "кубик лежит на жетоне ЦУ — значит, ЦУ запитано");
    }

    @Test
    void ЦУБезКубиковНеЗапитано() {
        GameState s = стол();
        BuildingToken cu = цу(s);
        cu.energyBySource.clear();
        cu.energyPlaced = 0;
        cu.energyIdle = 0;
        assertFalse(cu.powered(),
            "на жетоне ЦУ нет ни одного кубика — питать его нечем");
    }

    @Test
    void обычномуЗданиюСвободнаяЭнергияНеПомогает() {
        GameState s = стол();
        PlayerState p = s.player(0);
        BuildingToken завод = s.tokenStats.makeBuilding(
            BuildingType.FACTORY, p.seat, 9100, null);
        завод.energyPlaced = 0;
        // у обычного потребителя своей «свободной энергии» не бывает вовсе;
        // даже если её туда записать, ячейки она не закрывает
        завод.energyIdle = завод.energySlots;
        assertFalse(завод.powered(),
            "завод запитан ячейками, а не кубиками, лежащими рядом");
    }

    @Test
    void стартовоеЦУЗапитано() {
        GameState s = стол();
        assertTrue(цу(s).powered(), "на подготовке ЦУ приходит со своим кубиком");
    }
}
