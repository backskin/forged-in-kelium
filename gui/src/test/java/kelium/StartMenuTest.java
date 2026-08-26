package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.agents.BotCatalog;
import kelium.gui.HotSeatWindow;

/**
 * СТОРОЖ СВЯЗКИ «МЕНЮ ЗАПУСКА → ПАРТИЯ».
 *
 * <p>Меню отдаёт партии состав стола, и разъехаться этим двоим нельзя молча:
 * состав соперников берётся из каталога ботов, а окно партии обязано уметь
 * посадить каждого из них. Один раз оно этого НЕ умело — звало фабрику мимо
 * каталога, и любой соперник с уровнем умения («punisher:4») ронял поток партии
 * попыткой открыть файл с двоеточием в имени.
 */
class StartMenuTest {

    @Test
    void каждыйСоперникИзКаталогаРеальноСадитсяЗаСтол() {
        for (BotCatalog.Entry e : BotCatalog.players()) {
            var agent = BotCatalog.create(e.id(), 0, new Random(1), 2);
            assertNotNull(agent, "не посажен соперник " + e.id());
        }
    }

    @Test
    void унастроекПартииРазличаютсяОбычнаяИТренировочная() {
        HotSeatWindow.Options plain = HotSeatWindow.Options.simple(2, 7L,
            List.of("human", "builder:2"));
        assertFalse(plain.training(), "обычная партия не может быть тренировочной");

        HotSeatWindow.Options trained = new HotSeatWindow.Options(plain.rulesetId(), 2, 7L,
            plain.seatSpecs(), null, null, null, 9, null, null);
        assertTrue(trained.training(), "правка значений подготовки делает партию тренировочной");
        assertEquals(9, trained.startCoins());
    }

    @Test
    void стартовыеЗначенияПодготовкиЧитаютсяИзСвода() {
        var cfg = kelium.dataio.GameConfig.buildCached(
            kelium.dataio.GameConfig.DEFAULT_RULESET, 2, 5L, null, null);
        cfg.ruleset.override("setup.start_kelium", 6);
        cfg.ruleset.override("setup.start_ammo", 4);
        var state = kelium.engine.Setup.buildGame(cfg);
        assertEquals(6, state.player(0).resources.kelium(),
            "стартовый келемий должен браться из свода");
        assertEquals(4, state.player(0).resources.ammo(),
            "стартовые боеприпасы должны браться из свода");
    }
}
