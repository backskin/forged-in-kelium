package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import kelium.core.Agent;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;
import kelium.gui.GameRecorder;
import kelium.gui.GameSave;
import kelium.gui.HotSeatWindow;
import kelium.gui.MoveLog;
import kelium.report.ReplayRecord;

/**
 * СТОРОЖ СОХРАНЕНИЯ ПАРТИИ.
 *
 * <p>Сохранение хранит не слепок состояния, а настройки стола и ленту принятых
 * решений: движок воспроизводим, и по той же ленте партия повторяется в
 * точности. Ровно это здесь и проверяется — иначе «продолжить партию» однажды
 * молча продолжило бы ДРУГУЮ партию, и заметить это было бы нечем.
 */
class GameSaveTest {

    /** Сыграть партию ботами, записывая ленту решений. */
    private ReplayRecord play(long seed, List<Integer> log, List<Integer> replay) {
        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 2, seed,
            null, null);
        var state = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int seat = 0; seat < 2; seat++) {
            agents.add(kelium.agents.BotCatalog.create("builder:1", seat,
                new Random(seed * 131 + seat + 1), 2));
            labels.add("builder:1");
        }
        // Пишущая обёртка ПОВЕРХ проигрывающей — тот же порядок, что в окне
        // партии: доигранное по сохранению обязано попадать в ленту снова,
        // иначе продолженную партию нечем будет сохранить второй раз.
        List<Agent> playing = agents;
        if (replay != null) {
            playing = MoveLog.playback(playing, replay, null);
        }
        playing = MoveLog.recording(playing, log);
        return GameRecorder.playWithAgents(cfg, state, playing, labels, seed, null);
    }

    @Test
    void партияПоТойЖеЛентеРешенийПовторяетсяВТочности() {
        List<Integer> first = new ArrayList<>();
        ReplayRecord a = play(20260826L, first, null);
        assertTrue(first.size() > 50, "лента решений подозрительно короткая: " + first.size());

        // Продолжаем «сохранение», в котором записана ВСЯ партия: она обязана
        // доиграться теми же ходами и кончиться тем же.
        List<Integer> second = new ArrayList<>();
        ReplayRecord b = play(20260826L, second, first);

        assertEquals(a.winner, b.winner, "победитель обязан совпасть");
        assertEquals(a.rounds, b.rounds, "число раундов обязано совпасть");
        assertEquals(a.frames.size(), b.frames.size(), "число шагов обязано совпасть");
        assertEquals(first, second, "лента решений обязана повториться шаг в шаг");
    }

    @Test
    void сохранениеПереживаетЗаписьНаДискИЧтение(@TempDir java.nio.file.Path dir)
            throws Exception {
        HotSeatWindow.Options opts = new HotSeatWindow.Options(
            GameConfig.DEFAULT_RULESET, 2, 777L, List.of("human", "builder:2"),
            null, null, java.util.Arrays.asList(3, null), List.of(2, 0), 9, null, null);
        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 2, 777L, null, null);
        GameSave save = new GameSave("проба", opts, List.of(1, 0, 4, 2),
            GameConfig.DEFAULT_RULESET, GameSave.contentVersionsOf(cfg),
            "2026-08-26 13:00", 3, 2);

        var file = dir.resolve("проба.kelium-save.json");
        save.save(file);
        GameSave back = GameSave.load(file);

        assertEquals(List.of(1, 0, 4, 2), back.moves);
        assertEquals(777L, back.options.seed());
        assertEquals(List.of("human", "builder:2"), back.options.seatSpecs());
        assertEquals(List.of(2, 0), back.options.seatColors());
        assertEquals(9, back.options.startCoins());
        assertTrue(back.options.training(), "тренировочная партия остаётся тренировочной");
        assertEquals(3, back.round);
        assertNull(back.checkCompatible(), "своё же сохранение обязано подходить");
    }

    @Test
    void сохранениеСЧужимиВерсиямиНаборовОтвергаетсяВслух() {
        HotSeatWindow.Options opts = HotSeatWindow.Options.simple(2, 5L,
            List.of("builder:1", "builder:1"));
        GameSave stale = new GameSave("старое", opts, List.of(0),
            GameConfig.DEFAULT_RULESET,
            java.util.Map.of("orders", "0.0.1-которой-не-было"),
            "2026-01-01 00:00", 1, 1);
        String why = stale.checkCompatible();
        assertNotNull(why, "сохранение на других наборах карт нельзя принимать молча");
        assertTrue(why.contains("orders"), "причина обязана называть набор: " + why);
    }
}
