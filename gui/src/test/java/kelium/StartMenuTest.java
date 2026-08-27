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
            plain.seatSpecs(), null, null, null, null, 9, null, null);
        assertTrue(trained.training(), "правка значений подготовки делает партию тренировочной");
        assertEquals(9, trained.startCoins());
    }

    @Test
    void цветаМестПереживаютЗаписьЖурнала(@org.junit.jupiter.api.io.TempDir
                                          java.nio.file.Path dir) throws Exception {
        // Настоящая запись, а не собранная руками: у читателя журнала строгая
        // проверка, и синтетика до неё не доживает.
        var rec = kelium.gui.dev.DevScenes.build("энергия").record();
        rec.seatColors.clear();
        rec.seatColors.addAll(List.of(2, 0));
        var out = dir.resolve("colors.kelium-replay.json");
        rec.save(out);

        var back = kelium.report.ReplayRecord.load(out);
        assertEquals(List.of(2, 0), back.seatColors,
            "журнал обязан помнить, каким цветом играли");
        assertEquals(2, back.seatColor(0));
        assertEquals(0, back.seatColor(1));
    }

    @Test
    void безВыбораЦветаКраскаИдётЗаНомеромМеста() {
        kelium.report.FieldGeometry.useSeatColors(null);
        assertEquals(0, kelium.report.FieldGeometry.seatColor(0));
        assertEquals(3, kelium.report.FieldGeometry.seatColor(3));

        kelium.report.FieldGeometry.useSeatColors(List.of(2, 0));
        assertEquals(2, kelium.report.FieldGeometry.seatColor(0),
            "выбранная краска должна перебивать номер места");
        assertEquals(0, kelium.report.FieldGeometry.seatColor(1));
        assertEquals(2, kelium.report.FieldGeometry.seatColor(2),
            "место вне таблицы красится по своему номеру");
        kelium.report.FieldGeometry.useSeatColors(null);
    }

    /**
     * ПОМЕТКА «ВЫ» ЕСТЬ, ТОЛЬКО КОГДА ЖИВОЙ ОДИН. За одним компьютером с
     * несколькими живыми ход просто передаётся каждому по очереди, и который
     * из них «я», не значит ничего. Дважды выходило иначе: сперва пометку
     * носило место, на которое СМОТРЯТ (и в ход бота «вы» переезжало на бота),
     * потом — первое живое место, кто бы за ним ни сидел.
     */
    @Test
    void пометкаВыДостаётсяТолькоОдинокомуЖивому() {
        assertEquals(0, HotSeatWindow.meSeat(List.of("human", "builder:2")),
            "живой один — пометка на его месте");
        assertEquals(2, HotSeatWindow.meSeat(List.of("builder:2", "punisher:2", "human")),
            "место живого ищется по составу, а не по номеру");
        assertEquals(-1, HotSeatWindow.meSeat(List.of("human", "human", "punisher:2")),
            "живых двое — отмечать некого");
        assertEquals(-1, HotSeatWindow.meSeat(List.of("human", "human", "human", "human")),
            "стол целиком живой — отмечать некого");
        assertEquals(-1, HotSeatWindow.meSeat(List.of("builder:2", "punisher:2")),
            "живых нет вовсе — отмечать некого");
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
