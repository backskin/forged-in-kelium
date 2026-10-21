package kelium.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import kelium.report.ReplayRecord;

/**
 * ЛЕНТА СОБЫТИЙ НА ЭКРАНЕ НЕ ВЫДАЁТ ЧУЖИХ ЗАКРЫТЫХ КАРТ.
 *
 * <p>Журнал партии по заказу знает всё — он пишется для разбора после партии.
 * Экран — другое дело: рядом сидит соперник (за общим столом) или смотрит в
 * свой экран на другом конце провода, и его карты закрыты правилами.
 *
 * <p>Две строки нарушали это прямо: отложенный приказ лежит рубашкой вверх, а
 * добранная карта задания уходит в закрытую руку, — и обе называли карту по
 * имени на весь стол.
 */
class ЛентаНеВыдаётЧужогоTest {

    private static ReplayRecord.Frame кадр(String тип, Integer место, String текст) {
        ReplayRecord.Frame f = new ReplayRecord.Frame();
        f.type = тип;
        f.seat = место;
        f.log = текст;
        return f;
    }

    @Test
    void отложенныеПриказыНаЭкранеБезЛиц() {
        ReplayRecord.Frame f = кадр("blind_discard", null,
            "Отложенные приказы: Игрок 1 отложил НАСТУПЛЕНИЕ; Игрок 2 отложил ДОБЫЧУ");
        String экран = HotSeatWindow.наЭкран(f, true, 0, "Игрок 1");
        assertFalse(экран.contains("НАСТУПЛЕНИЕ"), "чужой отложенный приказ виден: " + экран);
        assertFalse(экран.contains("ДОБЫЧУ"), "свой отложенный приказ тоже называть нельзя: "
            + "лента общая, её видят через плечо");
    }

    @Test
    void чужаяДобраннаяКартаНеНазывается() {
        ReplayRecord.Frame f = кадр("objective_drawn", 1,
            "Игрок 2 получил задание «Засада» (в руке 3)");
        String экран = HotSeatWindow.наЭкран(f, true, 0, "Игрок 2");
        assertFalse(экран.contains("Засада"), "чужая карта названа на экране: " + экран);
        assertEquals("Игрок 2 получил карту задания", экран);
    }

    @Test
    void своюКартуЛентаНазывает() {
        ReplayRecord.Frame f = кадр("objective_drawn", 0,
            "Игрок 1 получил задание «Засада» (в руке 3)");
        assertEquals(f.log, HotSeatWindow.наЭкран(f, true, 0, "Игрок 1"),
            "свою карту игрок видит — прятать её от него незачем");
    }

    @Test
    void заСтоломСБотамиЛентаПолная() {
        ReplayRecord.Frame f = кадр("objective_drawn", 1,
            "Игрок 2 получил задание «Засада» (в руке 3)");
        assertEquals(f.log, HotSeatWindow.наЭкран(f, false, 0, "Игрок 2"),
            "один живой против ботов — прятать не от кого, а подробная лента "
                + "и есть половина удовольствия от разбора");
    }
}
