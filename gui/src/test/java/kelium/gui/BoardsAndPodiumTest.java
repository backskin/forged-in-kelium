package kelium.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.dataio.GameConfig;
import kelium.report.ReplayRecord;

/**
 * ПЛАНШЕТЫ И ИТОГОВАЯ ТАБЛИЦА (просьбы дизайнера 12.08.2026):
 * кубики игроков на ячейках треков и на ячейках рынка, ограниченное число
 * ячеек, золотые звёзды с ПО, а на последнем шаге — пьедестал поверх поля.
 */
class BoardsAndPodiumTest {

    private static ReplayRecord game() {
        return GameRecorder.play(GameConfig.DEFAULT_RULESET, 4, 909,
            List.of("strat:hawk", "strat:dove", "strat:balanced", "strat:opportunist"), null);
    }

    private static long ink(javax.swing.JComponent c, Color background) {
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(),
            BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(background);
        g.fillRect(0, 0, c.getWidth(), c.getHeight());
        c.paint(g);
        g.dispose();
        long n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) != (background.getRGB() & 0xFFFFFF)) {
                    n++;
                }
            }
        }
        return n;
    }

    // ==================== запись ====================

    @Test
    void recordCarriesTrackOccupancyAndMarketCells() {
        ReplayRecord rec = game();
        ReplayRecord.Snapshot last = rec.frames.get(rec.frames.size() - 1).snapshot;

        assertEquals(3, last.techOccupancy.size(), "три трека науки в записи");
        int onTracks = 0;
        for (List<List<Integer>> steps : last.techOccupancy.values()) {
            assertTrue(steps.size() >= 4, "по четыре шага на трек");
            for (List<Integer> seats : steps) {
                onTracks += seats.size();
                for (int seat : seats) {
                    assertTrue(seat >= 0 && seat < rec.players, "место игрока в пределах стола");
                }
            }
        }
        assertTrue(onTracks > 0, "за партию кто-то шагнул по трекам");

        // ЯЧЕЙКИ РЫНКА: владелец записан, и за несколько партий их хоть раз занимали.
        //
        // ПОЧЕМУ НЕСКОЛЬКО ПАРТИЙ, А НЕ ОДНА. Проверка сторожит запись — что в
        // снимке лежит место владельца ячейки, — но само занятие ячейки событие
        // вероятностное. На одной партии с одним сидом тест ловил не запись, а
        // склонность ботов к Рынку: когда коридор весов вернул action.market с
        // 24.89 к печатным двум, боты в этой конкретной партии до рынка не дошли,
        // и сторож упал на месте, где ничего не сломалось.
        boolean seenOwner = false;
        for (int i = 0; i < 6 && !seenOwner; i++) {
            ReplayRecord r = i == 0 ? rec : GameRecorder.play(GameConfig.DEFAULT_RULESET, 4,
                909 + i, List.of("strat:hawk", "strat:dove", "strat:balanced",
                    "strat:opportunist"), null);
            for (ReplayRecord.Frame f : r.frames) {
                for (int[] side : f.snapshot.marketCells) {
                    for (int seat : side) {
                        assertTrue(seat >= -1 && seat < r.players, "место или −1: " + seat);
                        if (seat >= 0) {
                            seenOwner = true;
                        }
                    }
                }
            }
        }
        assertTrue(seenOwner,
            "ни в одной из шести партий боты не заняли ячейку предложения рынка");
    }

    /**
     * КУБИКИ ЗАНИМАЮТ ЯЧЕЙКИ НАВСЕГДА (заказ дизайнера 02.09.2026): каждый
     * купленный шаг выкладывает НОВЫЙ кубик, прежние ячейки остаются за
     * игроком. Значит место игрока встречается на треке столько раз, сколько
     * шагов он на нём купил, и самая верхняя занятая им ячейка совпадает с его
     * шагом. Всего кубиков у игрока не больше запаса из свода — иначе он
     * выкладывал бы то, чего у него нет.
     */
    @Test
    void кубикиНакапливаютсяИНеПревышаютЗапас() {
        ReplayRecord rec = game();
        var rules = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 0L, null, null).ruleset;
        int запас = rules.getInt("tech.cube_supply", 8);
        for (ReplayRecord.Frame f : rec.frames) {
            int[] всего = new int[rec.players];
            for (var e : f.snapshot.techOccupancy.entrySet()) {
                List<List<Integer>> steps = e.getValue();
                int[] верхний = new int[rec.players];
                for (int i = 0; i < steps.size(); i++) {
                    for (int seat : steps.get(i)) {
                        всего[seat]++;
                        верхний[seat] = Math.max(верхний[seat], i + 1);
                    }
                }
                for (int seat = 0; seat < rec.players; seat++) {
                    int mine = f.snapshot.players.get(seat).tech.getOrDefault(e.getKey(), 0);
                    assertEquals(mine, верхний[seat],
                        "у игрока " + seat + " на треке " + e.getKey() + " шаг " + mine
                            + ", а самая верхняя занятая им ячейка — " + верхний[seat]);
                }
            }
            for (int seat = 0; seat < rec.players; seat++) {
                assertTrue(всего[seat] <= запас,
                    "игрок " + seat + " выложил " + всего[seat] + " кубиков, а в запасе "
                        + запас);
            }
        }
    }

    @Test
    void trackCapacityIsNeverExceeded() {
        ReplayRecord rec = game();
        var rules = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 0L, null, null).ruleset;
        List<Integer> caps = rules.stepCapacity(rec.players);
        for (ReplayRecord.Frame f : rec.frames) {
            for (var e : f.snapshot.techOccupancy.entrySet()) {
                List<List<Integer>> steps = e.getValue();
                for (int i = 0; i < steps.size() && i < caps.size(); i++) {
                    assertTrue(steps.get(i).size() <= caps.get(i),
                        "на шаге " + (i + 1) + " трека " + e.getKey() + " больше игроков ("
                            + steps.get(i).size() + "), чем ячеек (" + caps.get(i) + ")");
                }
            }
        }
    }

    /**
     * Ячейки шагов открываются по составу: на шагах 1, 2 и 3 последняя ячейка
     * доступна только вчетвером, вершина одна всегда.
     */
    @Test
    void cellCountsFollowThePlayerCount() {
        var rules = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 0L, null, null).ruleset;
        assertEquals(List.of(3, 3, 2, 1), rules.stepCapacity(4), "вчетвером открыто всё");
        assertEquals(List.of(2, 2, 1, 1), rules.stepCapacity(3),
            "втроём закрыты последние ячейки шагов 1, 2 и 3");
        assertEquals(List.of(2, 2, 1, 1), rules.stepCapacity(2),
            "вдвоём открыты те же ячейки, что и втроём");
    }

    // ==================== рисование ====================

    @Test
    void boardsDrawCubesAndPodiumAppearsOnTheLastFrame() {
        ReplayRecord rec = game();
        ReplayRecord.Frame last = rec.frames.get(rec.frames.size() - 1);
        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 0L, null, null);

        BoardsPanel boards = new BoardsPanel();
        boards.setRules(cfg.ruleset, cfg.content);
        boards.setSize(1000, 620);
        boards.show(rec, last.snapshot);
        assertTrue(ink(boards, Color.WHITE) > 20000, "планшеты нарисованы");

        // ПЬЕДЕСТАЛ: на последнем шаге картинка ДРУГАЯ, чем на середине партии
        FieldView view = new FieldView();
        view.setRecord(rec);
        view.setSize(900, 700);
        view.setFrame(rec.frames.get(rec.frames.size() / 2));
        view.fitToWindow();
        long middle = ink(view, Color.WHITE);

        view.setFrame(last);
        long end = ink(view, Color.WHITE);
        assertTrue(end != middle, "итоговая таблица меняет картинку последнего шага");
        assertTrue(end > 30000, "на последнем шаге нарисованы и поле, и таблица");
    }

    /**
     * ИТОГИ НЕ ПОКАЗЫВАЮТСЯ ДО ПАРТИИ. Баг дизайнера 12.08.2026: на стартовой
     * расстановке (в записи один кадр, он же последний) уже висела таблица с
     * победителем, хотя матч не запускали.
     */
    @Test
    void podiumDoesNotAppearOnTheStartingPreview() {
        ReplayRecord preview = GameRecorder.preview(GameConfig.DEFAULT_RULESET, 4, 909,
            List.of("strat:hawk", "strat:dove", "strat:balanced", "strat:opportunist"),
            null, null, null, null);
        assertTrue(preview.winner == null, "у расстановки победителя быть не может");

        FieldView view = new FieldView();
        view.setRecord(preview);
        view.setSize(900, 700);
        view.setFrame(preview.frames.get(preview.frames.size() - 1));
        view.fitToWindow();
        long shown = ink(view, Color.WHITE);

        // та же расстановка, но БЕЗ последнего кадра как «финала»: картинка
        // обязана совпасть — значит таблицы на превью нет
        FieldView plain = new FieldView();
        plain.setRecord(preview);
        plain.setSize(900, 700);
        plain.setFrame(preview.frames.get(0));
        plain.fitToWindow();
        assertEquals(ink(plain, Color.WHITE), shown,
            "на расстановке итоговой таблицы быть не должно");
    }
}
