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

    /**
     * ДЕЙСТВУЮЩИЙ СВОД. Он выкладывает супер-задания открыто на стол и держит
     * супер-арсенал закрытой колодой — ровно то, что здесь и проверяется.
     */
    // ИГРОВОЙ СВОД, А НЕ ОПОРА СТЕНДОВ: DEFAULT_RULESET держит 1.46.0 ради
    // сравнимости замеров, а у игрока партия открывается GAME_RULESET. Панели
    // проверяются на том, что игрок видит.
    private static final String СВОД = kelium.dataio.GameConfig.GAME_RULESET;

    /**
     * ДОПОЛНЕНИЯ ЗАДАЮТСЯ ЯВНО, А НЕ БЕРУТСЯ ИЗ НАСТРОЕК ПРИЛОЖЕНИЯ.
     *
     * <p>БАГ-ФИКС 20.08.2026. Тест проверяет супер-задания, а {@code
     * GameRecorder.play} накладывает дополнения из ПОСТОЯННЫХ настроек
     * приложения ({@code %APPDATA%\Kelium\kelium.cfg}). Стоило дизайнеру
     * выключить тумблер «Супер задания» в окне подготовки — и тест начал падать
     * на машине, где код не менялся вовсе.
     *
     * <p>Тест не имеет права зависеть от того, что человек нажал в интерфейсе,
     * поэтому нужные дополнения включаются здесь принудительно. Прежнее
     * состояние возвращается после партии: настройки общие с приложением, и
     * портить их тестом нельзя.
     */
    private static ReplayRecord gameWithSupers() {
        var settings = kelium.dataio.AppSettings.of("replay2");
        boolean was = kelium.gui.Expansions.on(settings,
            kelium.gui.Expansions.SUPER_OBJECTIVES);
        kelium.gui.Expansions.set(settings, kelium.gui.Expansions.SUPER_OBJECTIVES, true);
        try {
            return game();
        } finally {
            kelium.gui.Expansions.set(settings,
                kelium.gui.Expansions.SUPER_OBJECTIVES, was);
        }
    }

    private static ReplayRecord game() {
        return GameRecorder.play(СВОД, 4, 4242,
            List.of("strat:hawk", "strat:dove", "strat:balanced", "strat:opportunist"), null);
    }

    /**
     * СУПЕР-КАРТЫ В ЗАПИСИ ПАРТИИ — ПО ДЕЙСТВУЮЩИМ ПРАВИЛАМ.
     *
     * <p>Прежде тест ждал по открытой карте супер-арсенала на каждой вершине
     * трека и супер-задание в руке у каждого с подготовки. Оба правила сменил
     * дизайнер, а тест остался под старые:
     * <ul>
     *   <li>14.10.2026 — СУПЕР-АРСЕНАЛ ЗАКРЫТАЯ КОЛОДА: открытых карт на треках
     *       нет; вставший на вершину берёт две верхние, одну оставляет;</li>
     *   <li>05.10.2026 — СУПЕР-ЗАДАНИЯ ЛЕЖАТ ОТКРЫТО НА СТОЛЕ, в руку с
     *       подготовки их не раздают.</li>
     * </ul>
     */
    @Test
    void recordCarriesSuperArsenalOfferAndPartProgress() {
        ReplayRecord rec = gameWithSupers();
        ReplayRecord.Snapshot first = rec.frames.get(0).snapshot;
        assertTrue(first.superArsenalOffer.isEmpty(),
            "супер-арсенал — закрытая колода, на треках открытых карт нет, а лежат "
                + first.superArsenalOffer);
        assertTrue(!first.superDisplay.isEmpty(),
            "супер-задания с подготовки лежат открыто на столе");

        // Карта супер-арсенала, взятая с вершины, обязана быть из набора: иначе
        // запись несёт то, чего в колоде не было.
        GameConfig cfg = GameConfig.buildCached(СВОД, 4, 0L, null, null);
        java.util.Set<String> набор = new java.util.HashSet<>(
            cfg.content.get("super_arsenal").ids());
        ReplayRecord.Snapshot last = rec.frames.get(rec.frames.size() - 1).snapshot;
        for (ReplayRecord.Player p : last.players) {
            for (String card : p.superArsenal) {
                assertTrue(набор.contains(card),
                    "у игрока " + (p.seat + 1) + " карта супер-арсенала не из набора: " + card);
            }
        }
    }

    @Test
    void bothPanelsPaintSomething() {
        ReplayRecord rec = game();
        ReplayRecord.Snapshot last = rec.frames.get(rec.frames.size() - 1).snapshot;

        GameConfig cfg = GameConfig.buildCached(
            СВОД, 4, 0L, null, null);

        BoardsPanel boards = new BoardsPanel();
        boards.setRules(cfg.ruleset, cfg.content);
        boards.setSize(1000, 620);
        boards.show(rec, last);

        assertTrue(inkOf(boards) > 4000, "планшеты науки и рынка нарисованы почти пусто");
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
