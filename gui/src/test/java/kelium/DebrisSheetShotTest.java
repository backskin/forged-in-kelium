package kelium;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import kelium.dataio.GameConfig;
import kelium.gui.GameRecorder;
import kelium.gui.replay2.BoardSheet;
import kelium.gui.replay2.Session;
import kelium.report.ReplayRecord;

/**
 * Снимок ПЛАНШЕТА ИГРОКА: обломки обязаны попадать в ячейки хранилища и рисоваться
 * своим значком. Картинки кладутся в {@code target/sheet-shots} — посмотреть глазами.
 */
class DebrisSheetShotTest {

    @Test
    void debrisLandsInStorageCells() throws Exception {
        ReplayRecord rec = GameRecorder.play(GameConfig.DEFAULT_RULESET, 4, 4242,
            List.of("strat:hawk", "strat:dove", "explorer", "chaos"), null);
        Session s = new Session();
        s.setRecord(rec);

        Path dir = Path.of("target", "sheet-shots");
        Files.createDirectories(dir);

        // кадр, где у кого-то из игроков реально есть обломки
        int best = rec.frames.size() - 1;
        int bestSeat = 0;
        int bestDebris = -1;
        for (int i = 0; i < rec.frames.size(); i++) {
            for (ReplayRecord.Player p : rec.frames.get(i).snapshot.players) {
                if (p.debris > bestDebris) {
                    bestDebris = p.debris;
                    best = i;
                    bestSeat = p.seat;
                }
            }
        }
        System.out.println("[sheet] максимум обломков " + bestDebris
            + " у места " + bestSeat + " на кадре " + best + " из " + rec.frames.size());

        // партия обязана дойти до обломков — иначе снимок ничего не проверяет
        org.junit.jupiter.api.Assertions.assertTrue(bestDebris > 0,
            "в записи нет ни одного обломка — снимок хранилища бессмыслен");

        shoot(s, best, bestSeat, dir.resolve("sheet-debris.png"));

        // ВТОРОЙ СНИМОК — С РАЗЛОЖЕННЫМИ МОДУЛЯМИ. Кадр с максимумом обломков и
        // кадр с максимумом модулей — как правило разные, а места под жетоны надо
        // увидеть занятыми, а не только пустыми.
        int modBest = 0;
        int modSeat = 0;
        int modCount = -1;
        for (int i = 0; i < rec.frames.size(); i++) {
            for (ReplayRecord.Player p : rec.frames.get(i).snapshot.players) {
                int n = p.redPlaced.size() + p.bluePlaced.size();
                if (n > modCount) {
                    modCount = n;
                    modBest = i;
                    modSeat = p.seat;
                }
            }
        }
        System.out.println("[sheet] максимум модулей " + modCount + " у места "
            + modSeat + " на кадре " + modBest);
        org.junit.jupiter.api.Assertions.assertTrue(modCount > 0,
            "в записи никто не разложил ни одного модуля — места под жетоны не проверить");
        shoot(s, modBest, modSeat, dir.resolve("sheet-modules.png"));

        // ТРЕТИЙ СНИМОК — С ПОЗОЛОЧЁННЫМ ЖЕТОНОМ, если он в партии вообще был.
        // Золото рисуется каймой и уголком поверх своего цвета, и проверить это
        // можно только на кадре, где такой жетон лежит.
        int goldFrame = -1;
        int goldSeat = 0;
        outer:
        for (int i = 0; i < rec.frames.size(); i++) {
            for (ReplayRecord.Player p : rec.frames.get(i).snapshot.players) {
                boolean any = p.redPlaced.values().stream().anyMatch(x -> x.gold)
                    || p.bluePlaced.values().stream().anyMatch(x -> x.gold);
                if (any) {
                    goldFrame = i;
                    goldSeat = p.seat;
                    break outer;
                }
            }
        }
        if (goldFrame >= 0) {
            System.out.println("[sheet] позолочённый жетон: кадр " + goldFrame
                + ", место " + goldSeat);
            shoot(s, goldFrame, goldSeat, dir.resolve("sheet-gold.png"));
        } else {
            System.out.println("[sheet] позолочённых жетонов в этой партии нет");
        }
        System.out.println("[sheet] " + dir.toAbsolutePath());
    }

    private static void shoot(Session s, int frame, int seat, Path out) throws Exception {
        s.seek(frame);
        BoardSheet sheet = new BoardSheet(s, seat);
        sheet.setSize(760, 1180);
        BufferedImage img = new BufferedImage(760, 1180, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        sheet.paint(g);
        g.dispose();
        ImageIO.write(img, "png", out.toFile());
    }
}
