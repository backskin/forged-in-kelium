package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.dataio.GameConfig;
import kelium.gui.GameRecorder;
import kelium.gui.replay2.Names;
import kelium.report.ReplayRecord;
import kelium.report.Json;



/**
 * Запись партии для проигрывателя: партия прогоняется один раз, на каждое
 * событие снимается кадр; запись сохраняется в файл и читается обратно ТЕМ ЖЕ
 * видом (критерий приёмки №6 заказа).
 */
class ReplayRecordTest {

    private static final List<String> SEATS =
        List.of("strat:hawk", "strat:dove", "strat:balanced", "strat:opportunist");

    private static ReplayRecord play(long seed) {
        return GameRecorder.play(GameConfig.DEFAULT_RULESET, 4, seed, SEATS, null);
    }

    @Test
    void recordsEveryStepWithItsOwnSnapshot() {
        ReplayRecord rec = play(777);
        assertTrue(rec.frames.size() > 50,
            "в партии должно быть много шагов, а получилось " + rec.frames.size());
        assertEquals("game_start", rec.frames.get(0).type);
        assertEquals("game_end", rec.frames.get(rec.frames.size() - 1).type);
        assertNotNull(rec.winner, "победитель должен быть определён");
        assertFalse(rec.hexes.isEmpty(), "поле не должно быть пустым");

        for (ReplayRecord.Frame f : rec.frames) {
            assertNotNull(f.snapshot, "у каждого шага должен быть свой снимок");
            assertEquals(rec.players, f.snapshot.players.size());
            assertFalse(f.log.isBlank(), "у каждого шага должна быть строка лога");
        }
        // Кадр показывает состояние ИМЕННО этого шага, а не конца партии
        // (критерий приёмки №3): раунд в снимках не убывает и растёт.
        int firstRound = rec.frames.get(0).snapshot.round;
        int lastRound = rec.frames.get(rec.frames.size() - 1).snapshot.round;
        assertTrue(lastRound > firstRound,
            "раунд в снимках обязан меняться по ходу партии");
    }

    @Test
    void botsExplainTheirDecisions() {
        ReplayRecord rec = play(777);
        int thoughts = 0;
        for (ReplayRecord.Frame f : rec.frames) {
            thoughts += f.thoughts.size();
        }
        assertTrue(thoughts > 20,
            "стратеги должны озвучивать решения, а мыслей набралось " + thoughts);
    }

    @Test
    void highlightsAreDerivedFromSnapshots() {
        ReplayRecord rec = play(777);
        int moves = 0;
        int builds = 0;
        for (ReplayRecord.Frame f : rec.frames) {
            moves += f.highlight.moves.size();
            builds += f.highlight.builds.size();
        }
        assertTrue(moves > 0, "за партию кто-то обязан подвинуть жетон");
        assertTrue(builds > 0, "за партию кто-то обязан что-то построить или выставить");
    }

    /**
     * НИ ОДНОГО ВНУТРЕННЕГО КОДА НА ЭКРАНЕ. Карты приказов названия в наборе не
     * имеют, и планшет игрока показывал вместо имени код карты — {@code blue_acq}
     * в блоке «ОТЛОЖЕННЫЙ ПРИКАЗ» и {@code blue_dev} в строке «Приказы»
     * (найдено 13.08.2026). Всё, что видит человек, идёт через {@code Names.card}.
     */
    @Test
    void orderCardsAreShownByWordsAndNeverByTheirCode() {
        ReplayRecord rec = play(777);
        int checked = 0;
        for (ReplayRecord.Frame f : rec.frames) {
            for (ReplayRecord.Player p : f.snapshot.players) {
                List<String> ids = new java.util.ArrayList<>(p.orderHand);
                ids.addAll(p.orderPlayed);
                if (p.orderSetAside != null) {
                    ids.add(p.orderSetAside);
                }
                for (String id : ids) {
                    String shown = Names.card(rec, id);
                    assertFalse(shown.contains(id),
                        "на экране виден внутренний код карты: " + shown);
                    assertFalse("не описано".equals(shown),
                        "карта приказа осталась без человеческого имени: " + id);
                    checked++;
                }
            }
        }
        assertTrue(checked > 0, "в партии не нашлось ни одной карты приказа");
    }

    /**
     * Старые записи имён приказов не хранят: там имя собирается словами по уже
     * вскрытым приказам той же карты. Проверяем именно этот запасной путь.
     */
    @Test
    void oldRecordsWithoutCardNamesStillNameOrdersWithWords() {
        ReplayRecord rec = new ReplayRecord();
        ReplayRecord.OrderPlay op = new ReplayRecord.OrderPlay();
        op.card = "blue_acq";
        op.top = "acquisitions";
        op.bottom = "infrastructure";
        rec.orderPlays.add(op);
        assertEquals("ПРИОБРЕТЕНИЯ / ИНФРАСТРУКТУРА", Names.card(rec, "blue_acq"));
        // Карты, которой в партии не видели, честно нет — но и кода её тоже нет.
        assertEquals("не описано", Names.card(rec, "blue_dev"));
        assertEquals("—", Names.card(rec, null));
    }

    @Test
    void savedRecordOpensBackTheSame() throws Exception {
        ReplayRecord rec = play(2024);
        Path file = Files.createTempFile("kelium-replay-", ".json");
        try {
            rec.save(file);
            ReplayRecord back = ReplayRecord.load(file);
            assertEquals(rec.frames.size(), back.frames.size());
            assertEquals(rec.players, back.players);
            assertEquals(rec.seed, back.seed);
            assertEquals(rec.winner, back.winner);
            assertEquals(rec.hexes.size(), back.hexes.size());
            for (int i = 0; i < rec.frames.size(); i++) {
                ReplayRecord.Frame a = rec.frames.get(i);
                ReplayRecord.Frame b = back.frames.get(i);
                assertEquals(a.type, b.type, "шаг " + i);
                assertEquals(a.log, b.log, "шаг " + i);
                assertEquals(a.thoughts.size(), b.thoughts.size(), "шаг " + i);
                assertEquals(a.snapshot.round, b.snapshot.round, "шаг " + i);
                assertEquals(a.snapshot.tokens.size(), b.snapshot.tokens.size(), "шаг " + i);
                for (int p = 0; p < rec.players; p++) {
                    assertEquals(a.snapshot.players.get(p).vp.get("total"),
                        b.snapshot.players.get(p).vp.get("total"), "шаг " + i + ", место " + p);
                    assertEquals(a.snapshot.players.get(p).kelium,
                        b.snapshot.players.get(p).kelium, "шаг " + i + ", место " + p);
                }
            }
            // Печать латиницей: кириллица в консоли Windows (cp1251) ломается.
            System.out.println("[replay] frames=" + rec.frames.size()
                + " file=" + (Files.size(file) / 1024) + "KB");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /**
     * Битый или чужой файл обязан давать ПОНЯТНУЮ ошибку, а не падение
     * приложения: проигрыватель — точка входа для файлов со стороны.
     */
    @Test
    void brokenFilesFailWithAReadableMessage() throws Exception {
        Path dir = Files.createTempDirectory("kelium-bad-");
        try {
            assertThrows(Exception.class, () -> ReplayRecord.load(
                write(dir.resolve("alien.json"), "{\"format\":\"что-то другое\"}")),
                "чужой формат должен быть отвергнут");
            assertThrows(Exception.class, () -> ReplayRecord.load(
                write(dir.resolve("nosteps.json"),
                    "{\"format\":\"kelium-replay\",\"version\":1,\"frames\":[]}")),
                "запись без шагов должна быть отвергнута");
            assertThrows(Exception.class, () -> ReplayRecord.load(
                write(dir.resolve("nosnap.json"),
                    "{\"format\":\"kelium-replay\",\"version\":1,"
                    + "\"frames\":[{\"type\":\"x\",\"snap\":null}]}")),
                "первый шаг без снимка должен быть отвергнут");
            assertThrows(Exception.class, () -> ReplayRecord.load(
                write(dir.resolve("cut.json"), "{\"format\":\"kelium-replay\",\"ver")),
                "оборванный файл должен быть отвергнут");
            // Глубокая вложенность не должна ронять разбор в StackOverflowError.
            String deep = "[".repeat(5000) + "]".repeat(5000);
            assertThrows(Json.JsonError.class, () -> Json.parse(deep));
        } finally {
            try (var walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                        // временный файл — не смогли удалить, и ладно
                    }
                });
            }
        }
    }

    private static Path write(Path p, String text) throws Exception {
        Files.writeString(p, text, java.nio.charset.StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void jsonSurvivesCyrillicAndSpecialCharacters() {
        String text = "Веду технику на h1_2 — «ударю» по цели\nи строка \"два\"\tтабом";
        Object back = Json.parse(Json.write(java.util.Map.of("t", text, "n", 42, "x", 1.5)));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) back;
        assertEquals(text, m.get("t"));
        assertEquals(42, m.get("n"));
        assertEquals(1.5, m.get("x"));
    }
}
