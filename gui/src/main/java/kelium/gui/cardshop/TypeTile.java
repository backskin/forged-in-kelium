package kelium.gui.cardshop;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.IntSupplier;

import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

/**
 * ПЛИТКА ТИПА КАРТ (дизайнер 06.10.2026: «красивее, нагляднее, крупнее»):
 * рубашка типа, название в две строки и сколько карт в каталоге. Выбранная —
 * подсвечена цветом и полосой снизу.
 */
final class TypeTile extends JToggleButton {
    private static final long serialVersionUID = 1L;
    private static final int H = 74;
    private static final int PIC = 56;

    final CardSpec.Type type;
    private final IntSupplier count;
    private final String line1;
    private final String line2;
    private BufferedImage pic;
    private boolean hover;

    TypeTile(CardAssets assets, CardSpec.Type type, String line1, String line2, IntSupplier count) {
        this.type = type;
        this.line1 = line1;
        this.line2 = line2;
        this.count = count;
        setFocusable(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(type.ru);
        setPreferredSize(new Dimension(168, H));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
        });
        // рубашку — в фоне, окно открывается сразу
        new Thread(() -> {
            BufferedImage b = assets.back(type);
            if (b == null && type == CardSpec.Type.SPAWN_HEX) {
                b = assets.template(type.hexFile(false, 2));
            }
            if (b != null) {
                BufferedImage f = CardAssets.fit(b, PIC, PIC);
                SwingUtilities.invokeLater(() -> {
                    pic = f;
                    repaint();
                });
            }
        }, "рубашка " + type).start();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        boolean on = isSelected();
        g.setColor(on ? new Color(0x4A, 0x2B, 0x2A) : hover ? Style.PANEL_HOVER : Style.PANEL);
        g.fillRoundRect(0, 0, w - 1, h - 1, 14, 14);
        g.setColor(on ? Style.ACCENT : Style.LINE);
        g.setStroke(new BasicStroke(on ? 2f : 1f));
        g.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);
        if (on) {
            g.fillRoundRect(10, h - 5, w - 20, 4, 4, 4);
        }
        int x = 9;
        if (pic != null) {
            g.drawImage(pic, x + (PIC - pic.getWidth()) / 2, (h - pic.getHeight()) / 2, null);
        }
        x += PIC + 9;
        g.setColor(on ? Color.WHITE : Style.INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.drawString(line1, x, line2.isEmpty() ? 34 : 27);
        if (!line2.isEmpty()) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g.setColor(on ? new Color(0xF2D6D0) : Style.INK2);
            g.drawString(line2, x, 45);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.setColor(on ? new Color(0xF2D6D0) : Style.INK3);
        int n = count.getAsInt();
        g.drawString(n + " " + Library.cardsWord(n), x, 62);
        g.dispose();
    }
}
