package kelium.gui;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import kelium.core.Choice;
import kelium.core.InteractiveAgent;

/**
 * СНИМОК ЖИВОГО ОКНА ПАРТИИ — прогонщик для разработки интерфейса.
 *
 * <p>Открывает «Командный пункт» за краем экрана, играет за живых игроков
 * случайными ответами (как робот-тест) и останавливается на нужной точке
 * решения: после {@code skip} ответов — на первой точке вида {@code kind}
 * (или на любой, если вид не задан). Затем рисует окно в PNG.
 *
 * <p>Запуск: {@code HotSeatShot <out.png> [kind|-] [skip] [seed] [ширина] [высота]}.
 */
public final class HotSeatShot {

    private HotSeatShot() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("kelium.gui.offscreen", "true");
        // -Dshot.gamescale — масштаб, как у запуска игры (по экрану этой машины)
        if (System.getProperty("shot.gamescale") != null) {
            kelium.gui.replay2.Theme.useGameScale();
        }
        String out = args[0];
        String kind = args.length > 1 && !"-".equals(args[1]) ? args[1] : null;
        int skip = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        long seed = args.length > 3 ? Long.parseLong(args[3]) : 20260925L;
        int w = args.length > 4 ? Integer.parseInt(args[4]) : 1600;
        int h = args.length > 5 ? Integer.parseInt(args[5]) : 1000;

        // -Dshot.specs=human,builder:1,punisher:1 — состав мест (по умолчанию двое)
        List<String> specs = List.of(System.getProperty("shot.specs", "human,builder:1").split(","));
        HotSeatWindow win = new HotSeatWindow(
            HotSeatWindow.Options.simple(specs.size(), seed, specs));
        SwingUtilities.invokeAndWait(win::start);
        SwingUtilities.invokeAndWait(() -> win.frame.setSize(w, h));

        Random rnd = new Random(seed);
        int answered = 0;
        long deadline = System.currentTimeMillis() + 180_000L;
        while (!win.finishedForTest() && System.currentTimeMillis() < deadline) {
            var agent = win.humansBySeat.get(0);
            InteractiveAgent.PendingDecision d = agent == null ? null : agent.pending();
            if (d == null) {
                Thread.sleep(10);
                continue;
            }
            Thread.sleep(30);
            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() -> { });
            if (agent.pending() != d) {
                continue;
            }
            String k = String.valueOf(d.context().get("kind"));
            if (answered >= skip && (kind == null || kind.equals(k))) {
                break;
            }
            int idx = pick(rnd, d.options());
            SwingUtilities.invokeAndWait(() -> win.answerForTest(0, idx));
            answered++;
        }
        Thread.sleep(400);
        // -Dshot.seat=N — посмотреть стол другого места
        String seatProp = System.getProperty("shot.seat");
        if (seatProp != null) {
            SwingUtilities.invokeAndWait(() -> win.lookAtSeatForTest(Integer.parseInt(seatProp)));
            Thread.sleep(300);
        }
        // -Dshot.fake=<вид> — показать редкое решение, собранное из этой партии
        String fake = System.getProperty("shot.fake");
        if (fake != null) {
            var agent0 = win.humansBySeat.get(0);
            var now = agent0 == null ? null : agent0.pending();
            if (now != null) {
                var d = РедкиеРешения.собрать(fake, now.state(), 0);
                if (d == null) {
                    System.out.println("не из чего собрать решение " + fake);
                } else {
                    SwingUtilities.invokeAndWait(() -> win.previewDecisionForTest(0, d));
                    Thread.sleep(400);
                }
            }
        }
        // -Dshot.spread=objectives|arsenal|dump… — снять раскрытую группу карт
        String spreadGroup = System.getProperty("shot.spread");
        if (spreadGroup != null) {
            SwingUtilities.invokeAndWait(() -> win.openSpread(spreadGroup));
            Thread.sleep(400);
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            win.frame.getRootPane().validate();
            Graphics2D g = img.createGraphics();
            win.frame.getRootPane().paint(g);
            g.dispose();
        });
        ImageIO.write(img, "png", new File(out));
        System.out.println("ответов " + answered + ", точка: " + win.pendingKindForTest()
            + ", окно: " + win.statusForTest());
        System.exit(0);
    }

    private static int pick(Random rnd, List<Choice> options) {
        List<Integer> plays = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).payload() != null) {
                plays.add(i);
            }
        }
        if (plays.isEmpty() || rnd.nextInt(4) == 0) {
            return rnd.nextInt(options.size());
        }
        return plays.get(rnd.nextInt(plays.size()));
    }
}
