package kelium.gui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.dataio.GameConfig;
import kelium.report.ReplayRecord;

/**
 * ВКЛАДКИ ПЛАНШЕТОВ: наука+рынок и супер-задания (заказ дизайнера 12.08.2026).
 * Проверяем ровно то, за что отвечают панели: данные для них ПОПАЛИ в запись
 * партии, и обе рисуют содержимое, а не пустой лист.
 */
class BoardsTabsTest {

    private static ReplayRecord game() {
        return GameRecorder.play(GameConfig.DEFAULT_RULESET, 4, 4242,
            List.of("strat:hawk", "strat:dove", "strat:balanced", "strat:opportunist"), null);
    }

    @Test
    void recordCarriesSuperArsenalOfferAndPartProgress() {
        ReplayRecord rec = game();
        ReplayRecord.Snapshot first = rec.frames.get(0).snapshot;
        assertTrue(first.superArsenalOffer.size() == 3,
            "с подготовки на каждый трек выложена карта супер-арсенала, а есть "
                + first.superArsenalOffer);

        ReplayRecord.Snapshot last = rec.frames.get(rec.frames.size() - 1).snapshot;
        // К концу партии карт может стать меньше: вставший на вершину игрок
        // забирает карту трека себе. Значит пропавшая карта обязана быть у него.
        for (String track : first.superArsenalOffer.keySet()) {
            if (last.superArsenalOffer.containsKey(track)) {
                continue;
            }
            String card = first.superArsenalOffer.get(track);
            boolean held = last.players.stream().anyMatch(p -> p.superArsenal.contains(card));
            assertTrue(held, "карта " + card + " ушла с трека " + track + ", но ни у кого нет");
        }
        for (ReplayRecord.Player p : last.players) {
            assertNotNull(p.superObjective, "супер-задание выдаётся с подготовки");
            int sum = p.superParts.values().stream().mapToInt(Integer::intValue).sum();
            assertTrue(sum == p.superProgress,
                "сумма по частям (" + sum + ") должна совпадать с общим прогрессом ("
                    + p.superProgress + ")");
        }
    }

    @Test
    void bothPanelsPaintSomething() {
        ReplayRecord rec = game();
        ReplayRecord.Snapshot last = rec.frames.get(rec.frames.size() - 1).snapshot;

        GameConfig cfg = GameConfig.buildCached(
            GameConfig.DEFAULT_RULESET, 4, 0L, null, null);

        BoardsPanel boards = new BoardsPanel();
        boards.setRules(cfg.ruleset, cfg.content);
        boards.setSize(1000, 620);
        boards.show(rec, last);

        SuperObjectivesPanel supers = new SuperObjectivesPanel();
        supers.setContent(cfg.content);
        supers.setSize(1000, 620);
        supers.show(rec, last);

        assertTrue(inkOf(boards) > 4000, "планшеты науки и рынка нарисованы почти пусто");
        assertTrue(inkOf(supers) > 4000, "карты супер-заданий нарисованы почти пусто");
    }

    /** Сколько пикселей отличается от белого фона — «сколько чернил» на панели. */
    private static long inkOf(javax.swing.JComponent c) {
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(),
            BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, c.getWidth(), c.getHeight());
        c.paint(g);
        g.dispose();
        long ink = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) {
                    ink++;
                }
            }
        }
        return ink;
    }
}
