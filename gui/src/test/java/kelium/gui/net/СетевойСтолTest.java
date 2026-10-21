package kelium.gui.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;
import kelium.gui.GameRecorder;
import kelium.gui.HotSeatWindow;
import kelium.report.ReplayRecord;

/**
 * СЕТЕВОЙ СТОЛ ИГРАЕТ ОДНУ И ТУ ЖЕ ПАРТИЮ НА ДВУХ КОПИЯХ ДВИЖКА.
 *
 * <p>Это главная проверка сетевой игры: у хозяина и у гостя свои копии, по
 * сети летят только номера выбранных вариантов, и если копии разойдутся хоть
 * на одном решении, дальше каждый будет играть свою партию с общим счётом.
 * Поэтому тест сверяет не «не упало», а ПОЛНОЕ совпадение партий — журнал и
 * очки у обоих обязаны быть одинаковыми до последней строки.
 *
 * <p>Партия играется ботами с обеих сторон: живой игрок в тесте не нужен,
 * подача решений от этого не меняется — меняется только, кто их принимает.
 */
class СетевойСтолTest {

    private static final int ИГРОКОВ = 2;
    private static final long СИД = 20261021L;

    @Test
    void хозяинИГостьИграютОдинаковуюПартию() throws Exception {
        HotSeatWindow.Options настройки = HotSeatWindow.Options.simple(
            ИГРОКОВ, СИД, List.of("balanced", "balanced"));

        try (ХозяинСтола хозяин = new ХозяинСтола(0, настройки,
                List.of(ХозяинСтола.Кто.БОТ, ХозяинСтола.Кто.СЕТЬ));
             ГостьСтола гость = new ГостьСтола("127.0.0.1", хозяин.порт(), 5)) {

            assertTrue(гость.дождатьсяСтола(5), "гость не получил настройки стола");
            assertEquals(1, гость.моёМесто(), "гостю досталось не то место");
            assertEquals(0, хозяин.свободныхМест(), "сетевое место осталось пустым");
            assertEquals(настройки.seed(), гость.настройки().seed(),
                "сид стола у гостя не тот");

            ЛентаРешений лентаГостя = гость.лента();
            ЛентаРешений лентаХозяина = хозяин.начать();
            assertTrue(гость.дождатьсяНачала(5), "гость не услышал начала партии");

            AtomicReference<ReplayRecord> уХозяина = new AtomicReference<>();
            AtomicReference<ReplayRecord> уГостя = new AtomicReference<>();
            AtomicReference<Throwable> беда = new AtomicReference<>();

            Thread т1 = партия(настройки, лентаХозяина, уХозяина, беда, "хозяин");
            Thread т2 = партия(настройки, лентаГостя, уГостя, беда, "гость");
            т1.start();
            т2.start();
            т1.join(180_000);
            т2.join(180_000);

            if (беда.get() != null) {
                throw new AssertionError("сетевая партия сорвалась", беда.get());
            }
            ReplayRecord а = уХозяина.get();
            ReplayRecord б = уГостя.get();
            assertNotNull(а, "у хозяина нет записи партии");
            assertNotNull(б, "у гостя нет записи партии");
            assertEquals(а.frames.size(), б.frames.size(),
                "у хозяина и гостя разное число шагов в партии");
            assertEquals(строки(а), строки(б),
                "журналы партии у хозяина и гостя разошлись");
        }
    }

    /**
     * НАСТРОЙКИ ПЕРЕЖИВАЮТ ПРОВОД ЦЕЛИКОМ. Разойдись у хозяина и гостя хоть
     * одно поле — сид, свод, состав, раскладка, тренировочные значения, — и
     * копии движка разъедутся на первом же решении, причём молча.
     */
    @Test
    void настройкиСтолаПереживаютПровод() {
        HotSeatWindow.Options было = new HotSeatWindow.Options(
            "1.43.0", 3, -7788L, List.of("human", "balanced:2", "human"),
            "карта-4", null, java.util.Arrays.asList(2, null, 5), List.of(1, 0, 3), 9, null, 4);

        HotSeatWindow.Options стало = НастройкиСтола.изСообщения(
            НастройкиСтола.вСообщение(было));

        assertEquals(было.rulesetId(), стало.rulesetId(), "свод");
        assertEquals(было.players(), стало.players(), "число игроков");
        assertEquals(было.seed(), стало.seed(), "сид");
        assertEquals(было.seatSpecs(), стало.seatSpecs(), "состав мест");
        assertEquals(было.scenarioId(), стало.scenarioId(), "раскладка");
        assertEquals(было.seatColors(), стало.seatColors(), "краски мест");
        assertEquals(было.cuFacing(), стало.cuFacing(), "повороты ЦУ, вместе с незаданными");
        assertEquals(было.startCoins(), стало.startCoins(), "стартовые монеты");
        assertEquals(было.startKelium(), стало.startKelium(), "стартовый келемий");
        assertEquals(было.startAmmo(), стало.startAmmo(), "стартовые боеприпасы");
        assertTrue(стало.training(), "тренировочная партия обязана остаться тренировочной");
    }

    /**
     * СВОЯ РАСКЛАДКА ИЗ ФАЙЛА ПО СЕТИ НЕ ЕДЕТ: это путь на диске хозяина, и у
     * гостя такого файла нет. Сказать это надо в лобби, а не дать партии
     * развалиться на подготовке у гостя.
     */
    @Test
    void столСоСвоимФайломРаскладкиНеПоднимается() {
        HotSeatWindow.Options своя = new HotSeatWindow.Options(
            "1.43.0", 2, 1L, List.of("human", "human"),
            null, java.nio.file.Path.of("data", "scenarios", "своя.kmap"),
            null, null, null, null, null);
        assertNotNull(НастройкиСтола.почемуНельзя(своя),
            "стол со своим файлом раскладки обязан отказаться подниматься");
        assertNull(НастройкиСтола.почемуНельзя(
                HotSeatWindow.Options.simple(2, 1L, List.of("human", "balanced"))),
            "обычный стол поднимается без оговорок");
    }

    private static Thread партия(HotSeatWindow.Options настройки, ЛентаРешений лента,
                                  AtomicReference<ReplayRecord> куда,
                                  AtomicReference<Throwable> беда, String имя) {
        Thread t = new Thread(() -> {
            try {
                GameConfig cfg = GameConfig.buildCached(настройки.rulesetId(),
                    настройки.players(), настройки.seed(), null, null);
                GameState state = Setup.buildGame(cfg);
                List<Agent> агенты = new ArrayList<>();
                List<String> подписи = new ArrayList<>();
                for (int место = 0; место < настройки.players(); место++) {
                    String состав = настройки.seatSpecs().get(место);
                    агенты.add(kelium.agents.BotCatalog.create(состав, место,
                        new Random(настройки.seed() * 131 + место + 1), настройки.players()));
                    подписи.add(состав);
                }
                куда.set(GameRecorder.playWithAgents(cfg, state, лента.обернуть(агенты),
                    подписи, настройки.seed(), сообщение -> { }));
            } catch (Throwable t2) {
                беда.compareAndSet(null, t2);
                лента.оборвать("партия " + имя + " сорвалась");
            }
        }, "сетевая-партия-" + имя);
        t.setDaemon(true);
        return t;
    }

    private static List<String> строки(ReplayRecord r) {
        List<String> out = new ArrayList<>();
        for (ReplayRecord.Frame f : r.frames) {
            out.add(f.type + "|" + f.round + "|" + f.circle + "|" + f.seat + "|" + f.log);
        }
        return out;
    }
}
