package kelium.gui;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.SwingUtilities;

import kelium.gui.replay2.Theme;

/** Проба: съедает ли HTML разрядку шрифта и как выглядят начертания кнопок. */
public final class FontProbe {

    private FontProbe() {
    }

    public static void main(String[] args) throws Exception {
        Theme.setUserScale(2.0);   // крупно — чтобы разрядка была видна глазом
        Theme.apply(false);
        String s = "Игроков (по стартам): колесо — масштаб";
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        col.add(row("1 обычный текст, Theme.body()", s, Theme.body(), false));
        col.add(row("2 ТО ЖЕ, но в HTML", s, Theme.body(), true));
        col.add(row("3 note(12) обычный", s, Theme.note(12), false));
        col.add(row("4 wideText(12)", s, Theme.wideText(12), false));
        col.add(row("5 font(12,BOLD)", s, Theme.font(12, Font.BOLD), false));

        JButton b1 = new JButton("Собрать из блоков — font(13,BOLD)");
        b1.setFont(Theme.font(13, Font.BOLD));
        JButton b2 = new JButton("Собрать из блоков — wideText(13)");
        b2.setFont(Theme.wideText(13));
        JButton b3 = new JButton("Собрать из блоков — font(13,PLAIN)");
        b3.setFont(Theme.font(13, Font.PLAIN));
        JButton b4 = new JButton("Собрать из блоков — note(13)");
        b4.setFont(Theme.note(13));
        col.add(b1);
        col.add(b2);
        col.add(b3);
        col.add(b4);

        col.setSize(700, 380);
        col.doLayout();
        for (java.awt.Component c : col.getComponents()) {
            c.setSize(c.getPreferredSize().width, c.getPreferredSize().height);
            c.doLayout();
        }
        col.setBackground(java.awt.Color.WHITE);
        BufferedImage img = new BufferedImage(700, 380, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 700, 380);
        SwingUtilities.invokeAndWait(() -> {
            col.setSize(700, 380);
            col.validate();
            col.paint(g);
        });
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", new java.io.File(args[0]));
        System.exit(0);
    }

    private static JLabel row(String tag, String text, Font f, boolean html) {
        JLabel l = new JLabel(html ? "<html>" + tag + " · " + text + "</html>"
            : tag + " · " + text);
        l.setFont(f);
        return l;
    }
}
