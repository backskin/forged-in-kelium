package kelium.gui.dev;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

import kelium.gui.replay2.Theme;
import kelium.gui.replay2.Toggle;

/**
 * СНИМОК ТУМБЛЕРОВ — чтобы смотреть на пиксели, а не на глаз.
 *
 * <p>ЗАЧЕМ. Дизайнер прислал скриншот: «тумблеры визуально порезались». Гадать
 * по чужому скриншоту о причине нельзя — обрезка бывает и от нехватки ширины, и
 * от того, что цвет дорожки почти сливается с фоном, и от рассинхрона масштаба
 * темы с уже посчитанной раскладкой. Все три случая выглядят похоже, а лечатся
 * по-разному, поэтому компонент рисуется ОТДЕЛЬНО и в трёх условиях:
 * в своём предпочтительном размере, зажатым по ширине и на светлой теме.
 *
 * <p>Правило проекта: окна Swing не проверяются мышкой по настоящему экрану —
 * компонент рисуется в картинку внутри процесса и картинка меряется.
 *
 * <p>Запуск: {@code kelium.gui.dev.ToggleShot [папка]}
 */
public final class ToggleShot {

    private ToggleShot() {
    }

    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0] : ".";
        снять(new File(dir, "тумблеры-как-есть.png"), 0);
        снять(new File(dir, "тумблеры-зажаты.png"), Theme.px(60));
        System.out.println("готово: " + new File(dir).getAbsolutePath());
    }

    /** Нарисовать пару тумблеров; {@code сузить} — на сколько урезать ширину. */
    private static void снять(File out, int сузить) throws Exception {
        JPanel row = new JPanel();
        row.setOpaque(true);
        row.setBackground(Theme.panel());
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        Toggle off = new Toggle("Супер задания", false, "выключено");
        Toggle on = new Toggle("Начальные задания", true, "включено");
        row.add(off);
        row.add(Box.createHorizontalStrut(Theme.px(10)));
        row.add(on);

        Dimension pref = row.getPreferredSize();
        int w = Math.max(Theme.px(40), pref.width - сузить);
        row.setSize(w, pref.height);
        row.doLayout();

        BufferedImage img = new BufferedImage(w, pref.height,
            BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, pref.height);
        row.paint(g);
        g.dispose();
        ImageIO.write(img, "png", out);
        System.out.println(out.getName() + ": " + w + "x" + pref.height
            + ", предпочтительно " + pref.width + "x" + pref.height
            + ", тумблеру дано " + off.getWidth() + "x" + off.getHeight()
            + ", он просил " + off.getPreferredSize().width + "x"
            + off.getPreferredSize().height);
    }
}
