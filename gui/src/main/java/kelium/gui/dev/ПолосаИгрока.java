package kelium.gui.dev;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import kelium.gui.replay2.PlayerStrip;
import kelium.gui.replay2.Session;
import kelium.report.ReplayRecord;

/**
 * СНИМОК ПОЛОСЫ ИГРОКА — проверка раскладки нижнего ряда БЕЗ живого окна.
 *
 * <p>ЗАЧЕМ. Дизайнер видел, как нижний ряд полосы «шатается»: кружки сыгранных
 * приказов на одном кадре стоят в строке, а на следующем улетают вниз. Причина
 * — ширина ряда считалась по текущим числам («1·0·4» уже, чем «1·0·10»).
 * Проверять это мышью нельзя (правило 30.08: прогонщики не забирают фокус),
 * поэтому полоса рисуется В ПАМЯТИ и складывается в PNG.
 *
 * <p>Берутся кадры, на которых ЧИСЛА разной длины, — если раскладка не
 * дёргается, ряд на всех снимках выглядит одинаково.
 *
 * <p>Запуск: {@code kelium.gui.dev.ПолосаИгрока <файл записи> [папка] [ширина]}
 */
public final class ПолосаИгрока {

    private ПолосаИгрока() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("нужен файл записи партии");
            return;
        }
        ReplayRecord rec = ReplayRecord.load(java.nio.file.Path.of(args[0]));
        File dir = new File(args.length > 1 ? args[1] : ".");
        int w = args.length > 2 ? Integer.parseInt(args[2]) : 455;

        Session session = new Session();
        session.setRecord(rec);

        // Кадры с РАЗНОЙ длиной строки зданий: на них старая раскладка и
        // разъезжалась.
        List<Integer> кадры = new ArrayList<>();
        java.util.Set<Integer> длины = new java.util.HashSet<>();
        for (int i = 0; i < rec.frames.size(); i++) {
            ReplayRecord.Frame f = rec.frames.get(i);
            if (f.snapshot == null || f.snapshot.players.isEmpty()) {
                continue;
            }
            ReplayRecord.Player p = f.snapshot.players.get(0);
            int len = (p.objectiveHand.size() + p.arsenalHand.size()) + i / 200;
            if (длины.add(len % 7) && кадры.size() < 4) {
                кадры.add(i);
            }
        }
        PlayerStrip strip = new PlayerStrip(session, 0);
        strip.setSize(w, kelium.gui.replay2.Theme.px(kelium.gui.replay2.Theme.H_STRIP));
        for (int idx : кадры) {
            session.seek(idx);
            strip.setSize(w, kelium.gui.replay2.Theme.px(kelium.gui.replay2.Theme.H_STRIP));
            strip.doLayout();
            BufferedImage im = new BufferedImage(strip.getWidth(), strip.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = im.createGraphics();
            strip.printAll(g);
            g.dispose();
            File out = new File(dir, "полоса-кадр-" + idx + ".png");
            ImageIO.write(im, "png", out);
            System.out.println("снят кадр " + idx + " -> " + out.getName());
        }
    }
}
