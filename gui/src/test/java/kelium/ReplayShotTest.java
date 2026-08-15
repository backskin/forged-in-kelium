package kelium;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import kelium.dataio.GameConfig;
import kelium.gui.FieldView;
import kelium.gui.GameRecorder;
import kelium.report.ReplayRecord;


/**
 * Swing-отрисовка поля: рендер обязан работать и вне окна (снимком в PNG), и
 * рисовать РАЗНЫЕ картинки на разных шагах — то есть показывать состояние
 * именно этого шага, а не конца партии (критерий приёмки №3).
 * Картинки заодно кладутся в {@code target/replay-shots} — посмотреть глазами.
 */
class ReplayShotTest {

    @Test
    void writesFieldSnapshots() throws Exception {
        ReplayRecord rec = GameRecorder.play(GameConfig.DEFAULT_RULESET, 4, 777,
            List.of("strat:hawk", "strat:dove", "explorer", "chaos"), null);
        Path dir = Path.of("target", "replay-shots");
        Files.createDirectories(dir);

        int[] picks = pickInteresting(rec);
        long[] painted = new long[picks.length];
        for (int k = 0; k < picks.length; k++) {
            FieldView view = new FieldView();
            view.setRecord(rec);
            view.setSize(1100, 820);
            view.setFrame(rec.frames.get(picks[k]));
            view.fitToWindow();
            BufferedImage img = new BufferedImage(1100, 820, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 1100, 820);
            view.paint(g);
            g.dispose();
            ImageIO.write(img, "png", dir.resolve("shot" + k + ".png").toFile());
            painted[k] = signature(img);
        }
        for (long p : painted) {
            org.junit.jupiter.api.Assertions.assertTrue(p > 0,
                "поле нарисовалось пустым — рендер не сработал");
        }
        org.junit.jupiter.api.Assertions.assertNotEquals(painted[0], painted[painted.length - 1],
            "разные шаги партии обязаны выглядеть по-разному");
        System.out.println("[shots] " + dir.toAbsolutePath());
    }

    /** Грубый отпечаток картинки: сколько и каких небелых пикселей. */
    private static long signature(BufferedImage img) {
        long acc = 0;
        for (int y = 0; y < img.getHeight(); y += 3) {
            for (int x = 0; x < img.getWidth(); x += 3) {
                int rgb = img.getRGB(x, y) & 0xFFFFFF;
                if (rgb != 0xFFFFFF) {
                    acc += rgb % 1021;
                }
            }
        }
        return acc;
    }

    /** Первый кадр с боем, первый со стройкой, и конец партии. */
    private static int[] pickInteresting(ReplayRecord rec) {
        int battle = 0;
        int build = 0;
        for (int i = 0; i < rec.frames.size(); i++) {
            ReplayRecord.Frame f = rec.frames.get(i);
            if (battle == 0 && f.combat) {
                battle = i;
            }
            if (build == 0 && !f.highlight.builds.isEmpty()) {
                build = i;
            }
        }
        return new int[]{build, battle, rec.frames.size() - 1};
    }
}
